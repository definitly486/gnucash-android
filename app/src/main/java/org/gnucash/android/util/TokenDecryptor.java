/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gnucash.android.util;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Decrypts an access token stored in a password-protected file.
 *
 * <p>Primary format: files created by
 * {@code openssl enc -aes-256-cbc -salt -pbkdf2 -iter 200000} ("Salted__" + 8-byte salt +
 * AES-256-CBC ciphertext, key and IV from PBKDF2-HMAC-SHA256).</p>
 *
 * <p>Fallback format: PBKDF2-HMAC-SHA256 with {@value #PBKDF2_ITERATIONS} iterations and
 * AES-GCM (128-bit tag, no associated data).</p>
 *
 * <p>The file may be raw binary or base64 text. The GCM binary layout is
 * {@code salt (16 bytes) | IV (12 bytes) | ciphertext + GCM tag}. Because the exact layout
 * of the producing tool is not recorded in the file, the order {@code IV | salt | ciphertext}
 * and AES-128 as well as AES-256 keys are tried too; GCM authentication guarantees that a
 * wrong guess is rejected instead of yielding garbage.</p>
 *
 * <p>PBKDF2 is implemented on top of {@link Mac} because
 * {@code SecretKeyFactory "PBKDF2WithHmacSHA256"} is only available from Android API 26
 * and this app supports API 19.</p>
 */
public final class TokenDecryptor {

    public static final int PBKDF2_ITERATIONS = 200000;
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BYTES = 16;
    private static final int[] KEY_LENGTHS = {32, 16};

    private TokenDecryptor() {
    }

    /**
     * Decrypts the token.
     *
     * @param fileContent content of the encrypted file (binary or base64 text)
     * @param password    password the file was encrypted with
     * @return the decrypted token, trimmed
     * @throws GeneralSecurityException if the password is wrong or the format is not recognised
     */
    public static String decrypt(byte[] fileContent, String password) throws GeneralSecurityException {
        byte[] data = toBinary(fileContent);

        if (isOpenSslFormat(data))
            return decryptOpenSsl(data, password);

        if (data.length < SALT_LENGTH + IV_LENGTH + TAG_LENGTH_BYTES + 1)
            throw new GeneralSecurityException("Encrypted token file is too short");

        byte[] passwordBytes;
        try {
            passwordBytes = password.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new GeneralSecurityException(e);
        }

        byte[] body = Arrays.copyOfRange(data, SALT_LENGTH + IV_LENGTH, data.length);

        // layout 1: salt | iv | ciphertext      layout 2: iv | salt | ciphertext
        for (int layout = 0; layout < 2; layout++) {
            byte[] salt = layout == 0
                    ? Arrays.copyOfRange(data, 0, SALT_LENGTH)
                    : Arrays.copyOfRange(data, IV_LENGTH, IV_LENGTH + SALT_LENGTH);
            byte[] iv = layout == 0
                    ? Arrays.copyOfRange(data, SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
                    : Arrays.copyOfRange(data, 0, IV_LENGTH);

            // an AES-128 key is the first 16 bytes of the same PBKDF2 output
            byte[] derived = pbkdf2HmacSha256(passwordBytes, salt, PBKDF2_ITERATIONS, 32);
            for (int keyLength : KEY_LENGTHS) {
                try {
                    byte[] plain = aesGcmDecrypt(Arrays.copyOf(derived, keyLength), iv, body);
                    String token = new String(plain, "UTF-8").trim();
                    if (!token.isEmpty())
                        return token;
                } catch (GeneralSecurityException ignored) {
                    // wrong guess for this layout / key size, try the next one
                } catch (UnsupportedEncodingException ignored) {
                    // UTF-8 is always supported
                }
            }
        }
        throw new GeneralSecurityException("Wrong password or unsupported token file format");
    }

    private static final byte[] OPENSSL_MAGIC = {'S', 'a', 'l', 't', 'e', 'd', '_', '_'};
    private static final int OPENSSL_SALT_LENGTH = 8;
    private static final int OPENSSL_HEADER_LENGTH = 16;

    /**
     * {@code true} for files made by {@code openssl enc}: "Salted__", an 8-byte salt and a
     * whole number of AES blocks.
     */
    private static boolean isOpenSslFormat(byte[] data) {
        if (data.length < OPENSSL_HEADER_LENGTH + 16 || (data.length - OPENSSL_HEADER_LENGTH) % 16 != 0)
            return false;
        for (int i = 0; i < OPENSSL_MAGIC.length; i++) {
            if (data[i] != OPENSSL_MAGIC[i])
                return false;
        }
        return true;
    }

    /**
     * Decrypts a file created with
     * {@code openssl enc -aes-256-cbc -salt -pbkdf2 -iter 200000 -in token -out token.enc}.
     * openssl derives 48 bytes with PBKDF2-HMAC-SHA256 (its default digest): the first 32
     * are the AES key, the next 16 the CBC IV. The salt is stored after the "Salted__" magic.
     */
    private static String decryptOpenSsl(byte[] data, String password) throws GeneralSecurityException {
        byte[] passwordBytes;
        try {
            passwordBytes = password.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new GeneralSecurityException(e);
        }
        byte[] salt = Arrays.copyOfRange(data, OPENSSL_MAGIC.length, OPENSSL_MAGIC.length + OPENSSL_SALT_LENGTH);
        byte[] derived = pbkdf2HmacSha256(passwordBytes, salt, PBKDF2_ITERATIONS, 48);
        byte[] key = Arrays.copyOfRange(derived, 0, 32);
        byte[] iv = Arrays.copyOfRange(derived, 32, 48);

        byte[] plain;
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            plain = cipher.doFinal(data, OPENSSL_HEADER_LENGTH, data.length - OPENSSL_HEADER_LENGTH);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            throw new GeneralSecurityException("Wrong password or corrupted token file", e);
        }

        // CBC has no authentication: a wrong password passes the padding check about once in
        // 256 times and then yields garbage, so insist on a plausible printable token.
        for (byte b : plain) {
            if (b < 0x20 && b != '\n' && b != '\r' && b != '\t' || b >= 0x7F)
                throw new GeneralSecurityException("Wrong password or corrupted token file");
        }
        String token;
        try {
            token = new String(plain, "UTF-8").trim();
        } catch (UnsupportedEncodingException e) {
            throw new GeneralSecurityException(e);
        }
        if (token.isEmpty())
            throw new GeneralSecurityException("Wrong password or corrupted token file");
        return token;
    }

    private static byte[] aesGcmDecrypt(byte[] key, byte[] iv, byte[] ciphertextWithTag)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_LENGTH_BYTES * 8, iv));
        return cipher.doFinal(ciphertextWithTag);
    }

    /**
     * PBKDF2 (RFC 8018) with HMAC-SHA256 as the pseudorandom function.
     */
    static byte[] pbkdf2HmacSha256(byte[] password, byte[] salt, int iterations, int dkLen)
            throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(password.length == 0 ? new byte[1] : password, "HmacSHA256"));
        int hLen = mac.getMacLength();
        int blocks = (dkLen + hLen - 1) / hLen;
        byte[] derived = new byte[blocks * hLen];

        for (int block = 1; block <= blocks; block++) {
            mac.update(salt);
            mac.update(new byte[]{(byte) (block >>> 24), (byte) (block >>> 16),
                    (byte) (block >>> 8), (byte) block});
            byte[] u = mac.doFinal();
            byte[] t = u.clone();
            for (int i = 1; i < iterations; i++) {
                u = mac.doFinal(u);
                for (int j = 0; j < t.length; j++)
                    t[j] ^= u[j];
            }
            System.arraycopy(t, 0, derived, (block - 1) * hLen, hLen);
        }
        return Arrays.copyOf(derived, dkLen);
    }

    /**
     * Returns raw bytes: decodes the content if it is base64 text (standard or URL-safe
     * alphabet, whitespace and missing padding tolerated), otherwise returns it unchanged.
     */
    static byte[] toBinary(byte[] content) {
        int validChars = 0;
        for (byte b : content) {
            char c = (char) (b & 0xFF);
            if (c == '\r' || c == '\n' || c == ' ' || c == '\t')
                continue;
            if (!isBase64Char(c))
                return content; // not text in the base64 alphabet: treat as binary
            if (c != '=')
                validChars++;
        }
        if (validChars % 4 == 1)
            return content; // impossible base64 length

        byte[] out = new byte[validChars * 3 / 4];
        int outPos = 0, buffer = 0, bits = 0;
        for (byte b : content) {
            char c = (char) (b & 0xFF);
            if (c == '\r' || c == '\n' || c == ' ' || c == '\t' || c == '=')
                continue;
            buffer = (buffer << 6) | base64Value(c);
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out[outPos++] = (byte) (buffer >> bits);
                buffer &= (1 << bits) - 1;
            }
        }
        return Arrays.copyOf(out, outPos);
    }

    private static boolean isBase64Char(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '+' || c == '/' || c == '-' || c == '_' || c == '=';
    }

    private static int base64Value(char c) {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return c - 'a' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        if (c == '+' || c == '-') return 62;
        return 63; // '/' or '_'
    }
}

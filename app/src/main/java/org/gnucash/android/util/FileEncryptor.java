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
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts data in the format of
 * {@code openssl enc -aes-256-cbc -salt -pbkdf2 -iter 200000}, so that the result can be
 * decrypted on a computer with
 * <pre>openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in definitly.gnucash -out backup.gnca</pre>
 * (and by {@link TokenDecryptor}).
 *
 * <p>Layout: "Salted__" | 8-byte random salt | AES-256-CBC (PKCS#7) ciphertext. Key and IV are
 * the 48 bytes of PBKDF2-HMAC-SHA256 output ({@value TokenDecryptor#PBKDF2_ITERATIONS}
 * iterations): 32 bytes key, 16 bytes IV.</p>
 */
public final class FileEncryptor {

    private static final byte[] MAGIC = {'S', 'a', 'l', 't', 'e', 'd', '_', '_'};
    private static final int SALT_LENGTH = 8;

    private FileEncryptor() {
    }

    /**
     * @param plain    data to encrypt
     * @param password password to derive the key from (must not be empty)
     * @return the encrypted file content
     * @throws GeneralSecurityException if encryption is not possible
     */
    public static byte[] encrypt(byte[] plain, String password) throws GeneralSecurityException {
        if (password == null || password.isEmpty())
            throw new GeneralSecurityException("Password must not be empty");

        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return encrypt(plain, password, salt);
    }

    /** Visible for tests: encryption with a given salt. */
    static byte[] encrypt(byte[] plain, String password, byte[] salt) throws GeneralSecurityException {
        byte[] passwordBytes;
        try {
            passwordBytes = password.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new GeneralSecurityException(e);
        }
        byte[] derived = TokenDecryptor.pbkdf2HmacSha256(passwordBytes, salt,
                TokenDecryptor.PBKDF2_ITERATIONS, 48);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(Arrays.copyOfRange(derived, 0, 32), "AES"),
                new IvParameterSpec(Arrays.copyOfRange(derived, 32, 48)));
        byte[] ciphertext = cipher.doFinal(plain);

        byte[] out = new byte[MAGIC.length + SALT_LENGTH + ciphertext.length];
        System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
        System.arraycopy(salt, 0, out, MAGIC.length, SALT_LENGTH);
        System.arraycopy(ciphertext, 0, out, MAGIC.length + SALT_LENGTH, ciphertext.length);
        return out;
    }
}

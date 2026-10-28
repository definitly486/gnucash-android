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

import org.junit.Test;

import java.security.GeneralSecurityException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

/**
 * Test vectors were produced independently with Python's {@code cryptography} package
 * (PBKDF2-HMAC-SHA256, 200 000 iterations, AES-GCM). Password and token are dummy values.
 */
public class TokenDecryptorTest {

    private static final String PASSWORD = "correct horse";
    private static final String TOKEN = "ghp_A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8";

    /** salt(16) | iv(12) | ciphertext+tag, AES-256, base64 */
    private static final String SALT_IV_AES256 =
            "OS3uToihX9o39DQIspVXAdCeJoXCnJIQG9Bg8IGEs5AXWajDVeTAcXH5oUJEBlyP+l9TnBAiZbDEBpMj8IoNwzKePXBonRUsAjChgiHWLwOIsSbm";

    /** iv(12) | salt(16) | ciphertext+tag, AES-128, base64 */
    private static final String IV_SALT_AES128 =
            "2knl4HdpDQQHatb3zp+wRn/ZFEnk8xf6jMqmoDTZPP+irXDU3SF0YERyAahbnOByeufeLDgofkcT17dt6x/zV2w61w4TqtFZx6krWNa3oSBm04pw";

    /**
     * Output of {@code openssl enc -aes-256-cbc -salt -pbkdf2 -iter 200000 -pass pass:"correct horse"}
     * for a file holding {@link #OPENSSL_TOKEN} plus a trailing newline (112 bytes), as base64.
     */
    private static final String OPENSSL_FILE_BASE64 =
            "U2FsdGVkX19GYz/Fas/gv2KkEPQeNT/DxvMlvN1leNZNmd2jxm8TxvA6+Bi4c/lbtb++5OY2bsFQF0Vd9ePpdaoXGSxaTpZ9cGPh4uG57DHBOXn++4RB5GIfLbQXz+rSQ5rZUHgD/Wp2UZLG/b70Ng==";
    private static final String OPENSSL_TOKEN =
            "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";

    @Test
    public void shouldDecryptOpenSslCbcFileAsBinary() throws Exception {
        byte[] binary = java.util.Arrays.copyOf(decodeBase64(OPENSSL_FILE_BASE64), 112);
        assertThat(binary.length).isEqualTo(112);
        assertThat(TokenDecryptor.decrypt(binary, PASSWORD)).isEqualTo(OPENSSL_TOKEN);
    }

    @Test
    public void shouldDecryptOpenSslCbcFileAsBase64Text() throws Exception {
        assertThat(TokenDecryptor.decrypt(OPENSSL_FILE_BASE64.getBytes("US-ASCII"), PASSWORD))
                .isEqualTo(OPENSSL_TOKEN);
    }

    @Test
    public void shouldRejectWrongPasswordForOpenSslFile() throws Exception {
        try {
            TokenDecryptor.decrypt(OPENSSL_FILE_BASE64.getBytes("US-ASCII"), "correct horsE");
            fail("a wrong password must not decrypt the token");
        } catch (GeneralSecurityException expected) {
            // expected
        }
    }

    /** Test-only base64 decoder (java.util.Base64 is not available on API 19). */
    private static byte[] decodeBase64(String text) {
        return TokenDecryptor.toBinary(text.getBytes());
    }

    @Test
    public void shouldDecryptBase64SaltIvLayout() throws Exception {
        assertThat(TokenDecryptor.decrypt(SALT_IV_AES256.getBytes("US-ASCII"), PASSWORD)).isEqualTo(TOKEN);
    }

    @Test
    public void shouldDecryptBase64IvSaltLayoutWithAes128() throws Exception {
        assertThat(TokenDecryptor.decrypt(IV_SALT_AES128.getBytes("US-ASCII"), PASSWORD)).isEqualTo(TOKEN);
    }

    @Test
    public void shouldIgnoreTrailingNewline() throws Exception {
        assertThat(TokenDecryptor.decrypt((SALT_IV_AES256 + "\n").getBytes("US-ASCII"), PASSWORD))
                .isEqualTo(TOKEN);
    }

    @Test
    public void shouldRejectWrongPassword() throws Exception {
        try {
            TokenDecryptor.decrypt(SALT_IV_AES256.getBytes("US-ASCII"), "wrong password");
            fail("a wrong password must not decrypt the token");
        } catch (GeneralSecurityException expected) {
            // GCM authentication failed, as expected
        }
    }

    @Test
    public void shouldRejectTooShortInput() throws Exception {
        try {
            TokenDecryptor.decrypt("abcd".getBytes("US-ASCII"), PASSWORD);
            fail("too short input must be rejected");
        } catch (GeneralSecurityException expected) {
            // expected
        }
    }

    @Test
    public void pbkdf2ShouldMatchRfc7914TestVector() throws Exception {
        // PBKDF2-HMAC-SHA256, P="passwd", S="salt", c=1, dkLen=64 (RFC 7914 section 11)
        byte[] dk = TokenDecryptor.pbkdf2HmacSha256("passwd".getBytes("US-ASCII"),
                "salt".getBytes("US-ASCII"), 1, 64);
        StringBuilder hex = new StringBuilder();
        for (byte b : dk)
            hex.append(String.format("%02x", b));
        assertThat(hex.toString()).startsWith("55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc");
    }
}

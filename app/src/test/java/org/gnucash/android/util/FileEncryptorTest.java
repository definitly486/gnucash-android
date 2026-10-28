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
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public class FileEncryptorTest {

    /**
     * {@code openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -S 0102030405060708 -pass pass:secret-pass}
     * of "definitly gnucash backup test". With an explicit -S openssl omits the
     * "Salted__"+salt header, so this is the bare ciphertext (base64).
     */
    private static final String OPENSSL_CIPHERTEXT_BASE64 = "qQOlNcK2PUZoS0XJZBfZNX1iAwFgLCwhOT1l9zAVZ90=";
    private static final String PLAIN = "definitly gnucash backup test";

    @Test
    public void shouldProduceSameCiphertextAsOpenSsl() throws Exception {
        byte[] salt = {1, 2, 3, 4, 5, 6, 7, 8};
        byte[] encrypted = FileEncryptor.encrypt(PLAIN.getBytes("UTF-8"), "secret-pass", salt);

        assertThat(new String(encrypted, 0, 8, "US-ASCII")).isEqualTo("Salted__");
        assertThat(Arrays.copyOfRange(encrypted, 8, 16)).isEqualTo(salt);

        byte[] expectedCiphertext = TokenDecryptor.toBinary(OPENSSL_CIPHERTEXT_BASE64.getBytes("US-ASCII"));
        assertThat(Arrays.copyOfRange(encrypted, 16, encrypted.length)).isEqualTo(expectedCiphertext);
    }

    @Test
    public void shouldRoundTripThroughTokenDecryptor() throws Exception {
        byte[] encrypted = FileEncryptor.encrypt("ghp_RoundTrip123".getBytes("UTF-8"), "pässword");
        assertThat(TokenDecryptor.decrypt(encrypted, "pässword")).isEqualTo("ghp_RoundTrip123");
    }

    @Test
    public void shouldUseDifferentSaltEachTime() throws Exception {
        byte[] first = FileEncryptor.encrypt(PLAIN.getBytes("UTF-8"), "pw");
        byte[] second = FileEncryptor.encrypt(PLAIN.getBytes("UTF-8"), "pw");
        assertThat(Arrays.equals(first, second)).isFalse();
    }

    @Test
    public void shouldRejectEmptyPassword() throws Exception {
        try {
            FileEncryptor.encrypt(PLAIN.getBytes("UTF-8"), "");
            fail("an empty password must be rejected");
        } catch (GeneralSecurityException expected) {
            // expected
        }
    }
}

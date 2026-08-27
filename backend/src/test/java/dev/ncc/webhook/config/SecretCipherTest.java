package dev.ncc.webhook.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

  @Test
  void encryptsWithANewInitializationVectorAndDecryptsLosslessly() {
    SecurityProperties properties = new SecurityProperties();
    properties.setMasterKey(
        Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
    SecretCipher cipher = new SecretCipher(properties);

    String first = cipher.encrypt("endpoint-signing-secret");
    String second = cipher.encrypt("endpoint-signing-secret");

    assertThat(first).isNotEqualTo(second);
    assertThat(cipher.decrypt(first)).isEqualTo("endpoint-signing-secret");
    assertThat(cipher.decrypt(second)).isEqualTo("endpoint-signing-secret");
  }

  @Test
  void rejectsMasterKeysThatAreNotExactly256Bits() {
    SecurityProperties properties = new SecurityProperties();
    properties.setMasterKey(
        Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8)));

    assertThatThrownBy(() -> new SecretCipher(properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("32 bytes");
  }

  @Test
  void rejectsCiphertextEncryptedWithAnotherMasterKey() {
    SecurityProperties originalProperties = propertiesWithKey("0123456789abcdef0123456789abcdef");
    SecurityProperties replacementProperties =
        propertiesWithKey("abcdef0123456789abcdef0123456789");
    String ciphertext = new SecretCipher(originalProperties).encrypt("endpoint-signing-secret");

    assertThatThrownBy(() -> new SecretCipher(replacementProperties).decrypt(ciphertext))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unable to decrypt");
  }

  private SecurityProperties propertiesWithKey(String key) {
    SecurityProperties properties = new SecurityProperties();
    properties.setMasterKey(
        Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8)));
    return properties;
  }
}

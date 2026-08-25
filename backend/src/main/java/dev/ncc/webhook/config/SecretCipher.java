package dev.ncc.webhook.config;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class SecretCipher {

  private static final int IV_LENGTH = 12;
  private static final int TAG_LENGTH_BITS = 128;
  private final SecretKeySpec key;
  private final SecureRandom secureRandom = new SecureRandom();

  public SecretCipher(SecurityProperties properties) {
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(properties.getMasterKey());
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("APP_SECURITY_MASTER_KEY must be valid Base64", exception);
    }
    if (decoded.length != 32) {
      throw new IllegalStateException("APP_SECURITY_MASTER_KEY must decode to 32 bytes");
    }
    this.key = new SecretKeySpec(decoded, "AES");
  }

  public String encrypt(String plaintext) {
    byte[] iv = new byte[IV_LENGTH];
    secureRandom.nextBytes(iv);
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder()
          .encodeToString(
              ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Unable to encrypt endpoint secret", exception);
    }
  }

  public String decrypt(String encoded) {
    byte[] packed = Base64.getDecoder().decode(encoded);
    if (packed.length <= IV_LENGTH) {
      throw new IllegalArgumentException("Encrypted secret is malformed");
    }
    byte[] iv = new byte[IV_LENGTH];
    byte[] ciphertext = new byte[packed.length - IV_LENGTH];
    ByteBuffer.wrap(packed).get(iv).get(ciphertext);
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Unable to decrypt endpoint secret", exception);
    }
  }
}

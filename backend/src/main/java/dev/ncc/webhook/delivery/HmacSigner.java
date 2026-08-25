package dev.ncc.webhook.delivery;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class HmacSigner {

  public String sign(String secret, long timestamp, String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] digest = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
      return "v1=" + HexFormat.of().formatHex(digest);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Unable to sign webhook request", exception);
    }
  }
}

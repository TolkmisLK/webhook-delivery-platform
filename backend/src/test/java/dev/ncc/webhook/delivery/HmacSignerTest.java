package dev.ncc.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HmacSignerTest {

  private final HmacSigner signer = new HmacSigner();

  @Test
  void signsTheTimestampAndExactRequestBody() {
    String signature = signer.sign("test-secret", 1_700_000_000L, "{\"ok\":true}");

    assertThat(signature)
        .isEqualTo("v1=e00af05ced77fd14c24ad92b550cd513693939af637a094ea86bcfc6681451b0");
  }
}

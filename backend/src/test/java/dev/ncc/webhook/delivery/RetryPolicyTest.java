package dev.ncc.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ncc.webhook.config.DeliveryProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

  @Test
  void appliesExponentialBackoffAndCapsTheDelay() {
    DeliveryProperties properties = new DeliveryProperties();
    properties.setBaseRetryDelay(Duration.ofSeconds(2));
    properties.setMaxRetryDelay(Duration.ofSeconds(10));
    RetryPolicy policy = new RetryPolicy(properties);

    assertThat(policy.delayAfter(1)).isEqualTo(Duration.ofSeconds(2));
    assertThat(policy.delayAfter(2)).isEqualTo(Duration.ofSeconds(4));
    assertThat(policy.delayAfter(3)).isEqualTo(Duration.ofSeconds(8));
    assertThat(policy.delayAfter(4)).isEqualTo(Duration.ofSeconds(10));
  }
}

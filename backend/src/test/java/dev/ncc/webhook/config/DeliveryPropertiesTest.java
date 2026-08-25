package dev.ncc.webhook.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DeliveryPropertiesTest {

  @Test
  void requiresTheLeaseToOutliveAnHttpRequest() {
    DeliveryProperties properties = new DeliveryProperties();
    properties.setRequestTimeout(Duration.ofSeconds(10));
    properties.setLeaseTimeout(Duration.ofSeconds(5));

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Lease timeout");
  }

  @Test
  void requiresAPositiveMetricsRefreshInterval() {
    DeliveryProperties properties = new DeliveryProperties();
    properties.setMetricsRefreshInterval(Duration.ZERO);

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("metrics refresh interval");
  }
}

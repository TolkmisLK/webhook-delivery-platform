package dev.ncc.webhook.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ncc.webhook.config.OperatorAuthTelemetry.Outcome;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OperatorAuthTelemetryTest {

  @Test
  void preRegistersOnlyTheFixedAuthenticationOutcomes() {
    var registry = new SimpleMeterRegistry();
    var telemetry = new OperatorAuthTelemetry(registry);

    for (Outcome outcome : Outcome.values()) {
      telemetry.record(outcome);
    }

    Set<String> outcomes =
        registry.find("webhook.operator.authentication").counters().stream()
            .map(counter -> counter.getId().getTag("outcome"))
            .collect(Collectors.toSet());
    assertThat(outcomes).containsExactlyInAnyOrder("success", "failure", "rate_limited", "logout");
    assertThat(registry.find("webhook.operator.authentication").counters())
        .allSatisfy(counter -> assertThat(counter.count()).isEqualTo(1));
  }
}

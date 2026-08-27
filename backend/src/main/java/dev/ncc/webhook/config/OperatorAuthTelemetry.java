package dev.ncc.webhook.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OperatorAuthTelemetry {

  private static final String METER_NAME = "webhook.operator.authentication";
  private final Map<Outcome, Counter> counters = new EnumMap<>(Outcome.class);

  OperatorAuthTelemetry(MeterRegistry registry) {
    for (Outcome outcome : Outcome.values()) {
      counters.put(
          outcome,
          Counter.builder(METER_NAME)
              .description("Operator authentication outcomes")
              .tag("outcome", outcome.tag)
              .register(registry));
    }
  }

  void record(Outcome outcome) {
    counters.get(outcome).increment();
  }

  enum Outcome {
    SUCCESS("success"),
    FAILURE("failure"),
    RATE_LIMITED("rate_limited"),
    LOGOUT("logout");

    private final String tag;

    Outcome(String tag) {
      this.tag = tag;
    }
  }
}

package dev.ncc.webhook.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class OperatorLoginAttemptLimiterTest {

  @Test
  void blocksOnlyTheClientThatExceedsItsQuotaAndClearsItAfterSuccess() {
    var properties = properties(2, 100, 2);
    var clock = new MutableClock();
    var limiter = new OperatorLoginAttemptLimiter(properties, clock);

    assertThat(limiter.acquire("192.0.2.10").allowed()).isTrue();
    assertThat(limiter.acquire("192.0.2.10").allowed()).isTrue();
    assertThat(limiter.acquire("192.0.2.10").retryAfterSeconds()).isEqualTo(300);
    assertThat(limiter.acquire("192.0.2.10").retryAfterSeconds()).isEqualTo(300);
    assertThat(limiter.acquire("192.0.2.11").allowed()).isTrue();

    limiter.clearClient("192.0.2.10");

    assertThat(limiter.acquire("192.0.2.10").allowed()).isTrue();
  }

  @Test
  void appliesTheProcessWideQuotaAcrossClients() {
    var properties = properties(2, 2, 10);
    var clock = new MutableClock();
    var limiter = new OperatorLoginAttemptLimiter(properties, clock);

    assertThat(limiter.acquire("192.0.2.10").allowed()).isTrue();
    assertThat(limiter.acquire("192.0.2.11").allowed()).isTrue();
    assertThat(limiter.acquire("192.0.2.12").retryAfterSeconds()).isEqualTo(60);

    clock.advance(Duration.ofSeconds(61));

    assertThat(limiter.acquire("192.0.2.12").allowed()).isTrue();
  }

  @Test
  void boundsTrackedClientState() {
    var properties = properties(8, 100, 2);
    var limiter = new OperatorLoginAttemptLimiter(properties, new MutableClock());

    limiter.acquire("192.0.2.10");
    limiter.acquire("192.0.2.11");
    limiter.acquire("192.0.2.12");

    assertThat(limiter.trackedClientCount()).isEqualTo(2);
  }

  @Test
  void resetsAnUnblockedQuotaAfterTheWindow() {
    var properties = properties(1, 100, 10);
    var clock = new MutableClock();
    var limiter = new OperatorLoginAttemptLimiter(properties, clock);

    assertThat(limiter.acquire("192.0.2.10").allowed()).isTrue();
    clock.advance(Duration.ofSeconds(61));
    assertThat(limiter.acquire("192.0.2.10").allowed()).isTrue();
  }

  private OperatorLoginLimitProperties properties(
      int clientMaxAttempts, int globalMaxAttempts, int maxClientEntries) {
    var properties = new OperatorLoginLimitProperties();
    properties.setClientMaxAttempts(clientMaxAttempts);
    properties.setGlobalMaxAttempts(globalMaxAttempts);
    properties.setMaxClientEntries(maxClientEntries);
    return properties;
  }

  private static final class MutableClock extends Clock {

    private Instant instant = Instant.parse("2026-08-27T00:00:00Z");

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}

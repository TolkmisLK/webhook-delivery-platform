package dev.ncc.webhook.delivery;

import dev.ncc.webhook.config.DeliveryProperties;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class RetryPolicy {

  private final DeliveryProperties properties;

  public RetryPolicy(DeliveryProperties properties) {
    this.properties = properties;
  }

  public Duration delayAfter(int completedAttempt) {
    long multiplier = 1L << Math.min(Math.max(completedAttempt - 1, 0), 20);
    Duration candidate;
    try {
      candidate = properties.getBaseRetryDelay().multipliedBy(multiplier);
    } catch (ArithmeticException exception) {
      return properties.getMaxRetryDelay();
    }
    return candidate.compareTo(properties.getMaxRetryDelay()) > 0
        ? properties.getMaxRetryDelay()
        : candidate;
  }
}

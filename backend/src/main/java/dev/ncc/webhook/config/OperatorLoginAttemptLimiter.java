package dev.ncc.webhook.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OperatorLoginAttemptLimiter {

  private static final String UNKNOWN_CLIENT = "unknown";

  private final OperatorLoginLimitProperties properties;
  private final Clock clock;
  private final Map<String, AttemptWindow> clients = new LinkedHashMap<>(16, 0.75f, true);
  private final AttemptWindow global = new AttemptWindow();

  OperatorLoginAttemptLimiter(OperatorLoginLimitProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  synchronized Decision acquire(String remoteAddress) {
    Instant now = clock.instant();
    normalize(global, now);
    if (isBlocked(global, now)) {
      return Decision.deny(retryAfterSeconds(now, global.blockedUntil));
    }

    String clientKey = normalize(remoteAddress);
    AttemptWindow client = clients.get(clientKey);
    if (client == null) {
      makeClientCapacity();
      client = new AttemptWindow();
      clients.put(clientKey, client);
    }
    normalize(client, now);
    if (isBlocked(client, now)) {
      return Decision.deny(retryAfterSeconds(now, client.blockedUntil));
    }

    Decision globalDecision =
        increment(
            global,
            properties.getGlobalMaxAttempts(),
            properties.getGlobalBlockDuration(),
            now);
    if (!globalDecision.allowed()) {
      return globalDecision;
    }
    return increment(
        client,
        properties.getClientMaxAttempts(),
        properties.getClientBlockDuration(),
        now);
  }

  synchronized void clearClient(String remoteAddress) {
    clients.remove(normalize(remoteAddress));
  }

  synchronized int trackedClientCount() {
    return clients.size();
  }

  private Decision increment(
      AttemptWindow state, int maxAttempts, Duration blockDuration, Instant now) {
    state.attempts++;
    if (state.attempts > maxAttempts) {
      state.blockedUntil = now.plus(blockDuration);
      return Decision.deny(retryAfterSeconds(now, state.blockedUntil));
    }
    return Decision.allow();
  }

  private void normalize(AttemptWindow state, Instant now) {
    if (state.blockedUntil != null && !now.isBefore(state.blockedUntil)) {
      reset(state, now);
      return;
    }
    if (state.windowStartedAt == null
        || !now.isBefore(state.windowStartedAt.plus(properties.getWindow()))) {
      reset(state, now);
    }
  }

  private void reset(AttemptWindow state, Instant now) {
    state.windowStartedAt = now;
    state.attempts = 0;
    state.blockedUntil = null;
  }

  private boolean isBlocked(AttemptWindow state, Instant now) {
    return state.blockedUntil != null && now.isBefore(state.blockedUntil);
  }

  private void makeClientCapacity() {
    if (clients.size() < properties.getMaxClientEntries()) {
      return;
    }
    var entries = clients.entrySet().iterator();
    if (entries.hasNext()) {
      entries.next();
      entries.remove();
    }
  }

  private String normalize(String remoteAddress) {
    return remoteAddress == null || remoteAddress.isBlank() ? UNKNOWN_CLIENT : remoteAddress;
  }

  private long retryAfterSeconds(Instant now, Instant blockedUntil) {
    long millis = Duration.between(now, blockedUntil).toMillis();
    return Math.max(1, Math.ceilDiv(millis, 1_000));
  }

  public record Decision(boolean allowed, long retryAfterSeconds) {

    static Decision allow() {
      return new Decision(true, 0);
    }

    static Decision deny(long retryAfterSeconds) {
      return new Decision(false, retryAfterSeconds);
    }
  }

  private static final class AttemptWindow {
    private Instant windowStartedAt;
    private int attempts;
    private Instant blockedUntil;
  }
}

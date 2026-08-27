package dev.ncc.webhook.common;

public class LoginRateLimitException extends RuntimeException {

  private final long retryAfterSeconds;

  public LoginRateLimitException(long retryAfterSeconds) {
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}

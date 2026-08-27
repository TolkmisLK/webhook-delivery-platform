package dev.ncc.webhook.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.operator.login-limit")
public class OperatorLoginLimitProperties {

  @Min(1)
  @Max(10_000)
  private int clientMaxAttempts = 8;

  @Min(1)
  @Max(100_000)
  private int globalMaxAttempts = 64;

  @NotNull private Duration window = Duration.ofMinutes(1);

  @NotNull private Duration clientBlockDuration = Duration.ofMinutes(5);

  @NotNull private Duration globalBlockDuration = Duration.ofMinutes(1);

  @Min(1)
  @Max(100_000)
  private int maxClientEntries = 1_024;

  @AssertTrue(message = "Login-limit durations must be positive")
  public boolean isDurationConfigurationValid() {
    return window != null
        && !window.isZero()
        && !window.isNegative()
        && clientBlockDuration != null
        && !clientBlockDuration.isZero()
        && !clientBlockDuration.isNegative()
        && globalBlockDuration != null
        && !globalBlockDuration.isZero()
        && !globalBlockDuration.isNegative();
  }

  @AssertTrue(message = "Global login limit must not be lower than the client limit")
  public boolean isQuotaConfigurationValid() {
    return globalMaxAttempts >= clientMaxAttempts;
  }

  public int getClientMaxAttempts() {
    return clientMaxAttempts;
  }

  public void setClientMaxAttempts(int clientMaxAttempts) {
    this.clientMaxAttempts = clientMaxAttempts;
  }

  public int getGlobalMaxAttempts() {
    return globalMaxAttempts;
  }

  public void setGlobalMaxAttempts(int globalMaxAttempts) {
    this.globalMaxAttempts = globalMaxAttempts;
  }

  public Duration getWindow() {
    return window;
  }

  public void setWindow(Duration window) {
    this.window = window;
  }

  public Duration getClientBlockDuration() {
    return clientBlockDuration;
  }

  public void setClientBlockDuration(Duration clientBlockDuration) {
    this.clientBlockDuration = clientBlockDuration;
  }

  public Duration getGlobalBlockDuration() {
    return globalBlockDuration;
  }

  public void setGlobalBlockDuration(Duration globalBlockDuration) {
    this.globalBlockDuration = globalBlockDuration;
  }

  public int getMaxClientEntries() {
    return maxClientEntries;
  }

  public void setMaxClientEntries(int maxClientEntries) {
    this.maxClientEntries = maxClientEntries;
  }
}

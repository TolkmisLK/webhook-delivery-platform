package dev.ncc.webhook.config;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.delivery")
public class DeliveryProperties {

  private Duration pollInterval = Duration.ofSeconds(1);
  private Duration requestTimeout = Duration.ofSeconds(5);
  private Duration baseRetryDelay = Duration.ofSeconds(5);
  private Duration maxRetryDelay = Duration.ofMinutes(5);
  private Duration leaseTimeout = Duration.ofSeconds(30);
  private Duration metricsRefreshInterval = Duration.ofSeconds(5);
  private int batchSize = 10;
  private int workerConcurrency = 8;
  private int maxAttempts = 5;
  private int maxResponseBytes = 4096;
  private String workerId = "worker-1";

  @PostConstruct
  void validate() {
    requirePositive(pollInterval, "poll interval");
    requirePositive(requestTimeout, "request timeout");
    requirePositive(baseRetryDelay, "base retry delay");
    requirePositive(maxRetryDelay, "maximum retry delay");
    requirePositive(leaseTimeout, "lease timeout");
    requirePositive(metricsRefreshInterval, "metrics refresh interval");
    if (baseRetryDelay.compareTo(maxRetryDelay) > 0) {
      throw new IllegalStateException("Base retry delay cannot exceed the maximum retry delay");
    }
    if (leaseTimeout.compareTo(requestTimeout) <= 0) {
      throw new IllegalStateException("Lease timeout must be longer than the request timeout");
    }
    if (batchSize < 1 || workerConcurrency < 1 || maxAttempts < 1 || maxResponseBytes < 1) {
      throw new IllegalStateException("Delivery counts and size limits must be positive");
    }
    if (workerId == null || workerId.isBlank()) {
      throw new IllegalStateException("Worker ID must not be blank");
    }
  }

  private void requirePositive(Duration value, String name) {
    if (value == null || value.isZero() || value.isNegative()) {
      throw new IllegalStateException("Delivery " + name + " must be positive");
    }
  }

  public Duration getPollInterval() {
    return pollInterval;
  }

  public void setPollInterval(Duration pollInterval) {
    this.pollInterval = pollInterval;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
  }

  public Duration getBaseRetryDelay() {
    return baseRetryDelay;
  }

  public void setBaseRetryDelay(Duration baseRetryDelay) {
    this.baseRetryDelay = baseRetryDelay;
  }

  public Duration getMaxRetryDelay() {
    return maxRetryDelay;
  }

  public void setMaxRetryDelay(Duration maxRetryDelay) {
    this.maxRetryDelay = maxRetryDelay;
  }

  public Duration getLeaseTimeout() {
    return leaseTimeout;
  }

  public void setLeaseTimeout(Duration leaseTimeout) {
    this.leaseTimeout = leaseTimeout;
  }

  public Duration getMetricsRefreshInterval() {
    return metricsRefreshInterval;
  }

  public void setMetricsRefreshInterval(Duration metricsRefreshInterval) {
    this.metricsRefreshInterval = metricsRefreshInterval;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public int getWorkerConcurrency() {
    return workerConcurrency;
  }

  public void setWorkerConcurrency(int workerConcurrency) {
    this.workerConcurrency = workerConcurrency;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(int maxAttempts) {
    this.maxAttempts = maxAttempts;
  }

  public int getMaxResponseBytes() {
    return maxResponseBytes;
  }

  public void setMaxResponseBytes(int maxResponseBytes) {
    this.maxResponseBytes = maxResponseBytes;
  }

  public String getWorkerId() {
    return workerId;
  }

  public void setWorkerId(String workerId) {
    this.workerId = workerId;
  }
}

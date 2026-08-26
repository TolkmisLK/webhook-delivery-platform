package dev.ncc.webhook.delivery;

public enum DeliveryStatus {
  PENDING,
  PROCESSING,
  RETRY_SCHEDULED,
  SUCCEEDED,
  DEAD,
  CANCELED
}

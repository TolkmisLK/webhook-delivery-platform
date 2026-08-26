package dev.ncc.webhook.common;

public class DeliveryStateConflictException extends RuntimeException {

  public DeliveryStateConflictException(String message) {
    super(message);
  }
}

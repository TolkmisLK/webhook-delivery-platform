package dev.ncc.webhook.common;

public class VersionConflictException extends RuntimeException {

  public VersionConflictException(String message) {
    super(message);
  }
}

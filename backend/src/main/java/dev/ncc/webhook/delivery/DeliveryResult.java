package dev.ncc.webhook.delivery;

record DeliveryResult(
    boolean successful,
    boolean retryable,
    Integer statusCode,
    String error,
    String responseExcerpt,
    long durationMs) {}

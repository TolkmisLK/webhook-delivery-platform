package dev.ncc.webhook.delivery;

enum DeliveryStateChangeSource {
  ACCEPTED,
  WORKER_CLAIM,
  WORKER_RECLAIM,
  ATTEMPT_OUTCOME,
  MANUAL_REPLAY,
  MANUAL_CANCEL
}

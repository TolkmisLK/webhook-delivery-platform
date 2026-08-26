package dev.ncc.webhook.event;

import java.time.Instant;
import java.util.UUID;

/** Event-module port for creating or locating the durable delivery job. 事件模块用于创建或查询持久化投递任务的端口。 */
public interface DeliverySubmission {

  AcceptedDelivery ensureScheduled(
      UUID eventId, UUID endpointId, String targetUrl, String encryptedSecret, Instant acceptedAt);

  record AcceptedDelivery(UUID id, String status) {}
}

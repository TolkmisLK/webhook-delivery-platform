package dev.ncc.webhook.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.ncc.webhook.delivery.DeliveryJobRepository;
import dev.ncc.webhook.delivery.DeliveryStatus;
import dev.ncc.webhook.delivery.HmacSigner;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest
// Close scheduled workers before Testcontainers stops PostgreSQL.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
class WebhookFlowIT {

  private static final AtomicReference<String> RECEIVED_BODY = new AtomicReference<>();
  private static final AtomicReference<String> RECEIVED_SIGNATURE = new AtomicReference<>();
  private static final AtomicInteger RECEIVED_COUNT = new AtomicInteger();
  private static final AtomicInteger FLAKY_COUNT = new AtomicInteger();
  private static final AtomicInteger CANCELED_TARGET_COUNT = new AtomicInteger();
  private static final HttpServer TARGET = startTarget();

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add(
        "app.security.master-key",
        () ->
            Base64.getEncoder()
                .encodeToString(
                    "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
    registry.add("app.security.allow-private-targets", () -> "true");
    registry.add(
        "app.security.allowed-ports", () -> Integer.toString(TARGET.getAddress().getPort()));
    registry.add("app.delivery.poll-interval", () -> "100ms");
    registry.add("app.delivery.base-retry-delay", () -> "100ms");
    registry.add("app.delivery.max-retry-delay", () -> "100ms");
  }

  @Autowired MockMvc mockMvc;
  @Autowired JsonMapper jsonMapper;
  @Autowired DeliveryJobRepository deliveryRepository;
  @Autowired JdbcTemplate jdbcTemplate;

  @AfterAll
  static void stopTarget() {
    TARGET.stop(0);
  }

  @Test
  void acceptsSignsAndDeliversAnEvent() throws Exception {
    String endpointJson =
        mockMvc
            .perform(
                post("/api/endpoints")
                    .contentType("application/json")
                    .content(
                        """
                        {
                          "name": "integration target",
                          "url": "http://127.0.0.1:%d/hooks",
                          "secret": "integration-secret"
                        }
                        """
                            .formatted(TARGET.getAddress().getPort())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String endpointId = jsonMapper.readTree(endpointJson).get("id").asText();

    String eventJson =
        mockMvc
            .perform(
                post("/api/events")
                    .contentType("application/json")
                    .content(
                        """
                        {
                          "endpointId": "%s",
                          "eventType": "demo.completed",
                          "idempotencyKey": "integration-1",
                          "data": {"result": "ok"}
                        }
                        """
                            .formatted(endpointId)))
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID deliveryId = UUID.fromString(jsonMapper.readTree(eventJson).get("deliveryId").asText());

    String duplicateJson =
        mockMvc
            .perform(
                post("/api/events")
                    .contentType("application/json")
                    .content(
                        """
                        {
                          "endpointId": "%s",
                          "eventType": "demo.completed",
                          "idempotencyKey": "integration-1",
                          "data": {"result": "ignored duplicate"}
                        }
                        """
                            .formatted(endpointId)))
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();

    awaitSuccess(deliveryId);

    assertThat(jsonMapper.readTree(duplicateJson).get("duplicate").asBoolean()).isTrue();
    assertThat(jsonMapper.readTree(duplicateJson).get("deliveryId").asText())
        .isEqualTo(deliveryId.toString());
    assertThat(RECEIVED_BODY.get()).contains("demo.completed").contains("\"result\":\"ok\"");
    assertThat(RECEIVED_SIGNATURE.get()).startsWith("v1=");
    assertThat(RECEIVED_COUNT.get()).isEqualTo(1);

    String detailJson =
        mockMvc
            .perform(get("/api/deliveries/{id}", deliveryId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var detail = jsonMapper.readTree(detailJson);
    assertThat(detail.get("delivery").get("status").asText()).isEqualTo("SUCCEEDED");
    assertThat(detail.get("attempts").size()).isEqualTo(1);
    assertThat(detail.get("attempts").get(0).get("outcome").asText()).isEqualTo("SUCCEEDED");
    assertThat(detail.get("attempts").get(0).get("statusCode").asInt()).isEqualTo(204);
  }

  @Test
  void exposesTheAttemptTimelineAfterTransientFailures() throws Exception {
    FLAKY_COUNT.set(0);
    String endpointJson =
        mockMvc
            .perform(
                post("/api/endpoints")
                    .contentType("application/json")
                    .content(
                        """
                        {
                          "name": "flaky integration target",
                          "url": "http://127.0.0.1:%d/hooks/flaky",
                          "secret": "integration-secret"
                        }
                        """
                            .formatted(TARGET.getAddress().getPort())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String endpointId = jsonMapper.readTree(endpointJson).get("id").asText();

    String eventJson =
        mockMvc
            .perform(
                post("/api/events")
                    .contentType("application/json")
                    .content(
                        """
                        {
                          "endpointId": "%s",
                          "eventType": "demo.retry-recovered",
                          "idempotencyKey": "integration-flaky-1",
                          "data": {"result": "eventually-ok"}
                        }
                        """
                            .formatted(endpointId)))
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID deliveryId = UUID.fromString(jsonMapper.readTree(eventJson).get("deliveryId").asText());

    awaitSuccess(deliveryId);

    String detailJson =
        mockMvc
            .perform(get("/api/deliveries/{id}", deliveryId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var attempts = jsonMapper.readTree(detailJson).get("attempts");
    assertThat(attempts.size()).isEqualTo(3);
    assertThat(attempts.get(0).get("outcome").asText()).isEqualTo("RETRY_SCHEDULED");
    assertThat(attempts.get(1).get("outcome").asText()).isEqualTo("RETRY_SCHEDULED");
    assertThat(attempts.get(2).get("outcome").asText()).isEqualTo("SUCCEEDED");
    assertThat(FLAKY_COUNT.get()).isEqualTo(3);
  }

  @Test
  void versionsEndpointActivationAndRejectsStaleUpdates() throws Exception {
    String endpointJson =
        mockMvc
            .perform(
                post("/api/endpoints")
                    .contentType("application/json")
                    .content(
                        """
                        {
                          "name": "lifecycle target",
                          "url": "http://127.0.0.1:%d/hooks",
                          "secret": "integration-secret"
                        }
                        """
                            .formatted(TARGET.getAddress().getPort())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var created = jsonMapper.readTree(endpointJson);
    String endpointId = created.get("id").asText();
    assertThat(created.get("active").asBoolean()).isTrue();
    assertThat(created.get("version").asLong()).isZero();

    String inactiveJson =
        mockMvc
            .perform(
                patch("/api/endpoints/{id}/status", endpointId)
                    .contentType("application/json")
                    .content("{\"active\":false,\"expectedVersion\":0}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var inactive = jsonMapper.readTree(inactiveJson);
    assertThat(inactive.get("active").asBoolean()).isFalse();
    assertThat(inactive.get("version").asLong()).isEqualTo(1);

    mockMvc
        .perform(
            post("/api/events")
                .contentType("application/json")
                .content(
                    """
                    {
                      "endpointId": "%s",
                      "eventType": "demo.inactive",
                      "idempotencyKey": "inactive-endpoint",
                      "data": {"result": "blocked"}
                    }
                    """
                        .formatted(endpointId)))
        .andExpect(status().isBadRequest());

    String conflictJson =
        mockMvc
            .perform(
                patch("/api/endpoints/{id}/status", endpointId)
                    .contentType("application/json")
                    .content("{\"active\":true,\"expectedVersion\":0}"))
            .andExpect(status().isConflict())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(jsonMapper.readTree(conflictJson).get("code").asText())
        .isEqualTo("version_conflict");

    String activeJson =
        mockMvc
            .perform(
                patch("/api/endpoints/{id}/status", endpointId)
                    .contentType("application/json")
                    .content("{\"active\":true,\"expectedVersion\":1}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var active = jsonMapper.readTree(activeJson);
    assertThat(active.get("active").asBoolean()).isTrue();
    assertThat(active.get("version").asLong()).isEqualTo(2);

    mockMvc
        .perform(
            patch("/api/endpoints/{id}/status", UUID.randomUUID())
                .contentType("application/json")
                .content("{\"active\":false,\"expectedVersion\":0}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void exposesBoundedPrometheusQueueMetrics() throws Exception {
    String exposition =
        mockMvc
            .perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<String> jobSeries =
        exposition.lines().filter(line -> line.startsWith("webhook_delivery_jobs{")).toList();
    assertThat(jobSeries)
        .hasSize(6)
        .allMatch(
            line ->
                line.matches(
                    "webhook_delivery_jobs\\{status=\\\"(pending|processing|retry_scheduled|succeeded|dead|canceled)\\\"}"
                        + " [0-9.Ee+-]+"));
    assertThat(exposition).contains("webhook_delivery_oldest_runnable_age_seconds");
    assertThat(String.join("\n", jobSeries))
        .doesNotContain("endpoint", "delivery_id", "event_type", "url", "idempotency");
  }

  @Test
  void cancelsQueuedWorkIdempotentlyAndRejectsProcessingWork() throws Exception {
    CANCELED_TARGET_COUNT.set(0);
    String endpointJson =
        mockMvc
            .perform(
                post("/api/endpoints")
                    .contentType("application/json")
                    .content(
                        """
                        {
                          "name": "cancellation target",
                          "url": "http://127.0.0.1:%d/hooks/cancel",
                          "secret": "integration-secret"
                        }
                        """
                            .formatted(TARGET.getAddress().getPort())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID endpointId = UUID.fromString(jsonMapper.readTree(endpointJson).get("id").asText());

    UUID canceledDeliveryId = insertDelivery(endpointId, "PENDING", Instant.now().plusSeconds(60));
    String canceledJson =
        mockMvc
            .perform(post("/api/deliveries/{id}/cancel", canceledDeliveryId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(jsonMapper.readTree(canceledJson).get("status").asText()).isEqualTo("CANCELED");

    mockMvc
        .perform(post("/api/deliveries/{id}/cancel", canceledDeliveryId))
        .andExpect(status().isOk());
    jdbcTemplate.update(
        "update delivery_job set next_attempt_at = ? where id = ?",
        Timestamp.from(Instant.now().minusSeconds(1)),
        canceledDeliveryId);
    Thread.sleep(300);
    assertThat(deliveryRepository.findById(canceledDeliveryId).orElseThrow().getStatus())
        .isEqualTo(DeliveryStatus.CANCELED);
    assertThat(CANCELED_TARGET_COUNT.get()).isZero();

    UUID processingDeliveryId = insertDelivery(endpointId, "PROCESSING", Instant.now());
    jdbcTemplate.update(
        "update delivery_job set locked_at = ?, locked_by = 'integration-worker' where id = ?",
        Timestamp.from(Instant.now()),
        processingDeliveryId);
    String conflictJson =
        mockMvc
            .perform(post("/api/deliveries/{id}/cancel", processingDeliveryId))
            .andExpect(status().isConflict())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(jsonMapper.readTree(conflictJson).get("code").asText())
        .isEqualTo("delivery_state_conflict");

    mockMvc
        .perform(post("/api/deliveries/{id}/cancel", UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  private void awaitSuccess(UUID deliveryId) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (System.nanoTime() < deadline) {
      var job = deliveryRepository.findById(deliveryId).orElseThrow();
      if (job.getStatus() == DeliveryStatus.SUCCEEDED) {
        return;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("Delivery did not succeed before timeout");
  }

  private UUID insertDelivery(UUID endpointId, String status, Instant nextAttemptAt) {
    UUID eventId = UUID.randomUUID();
    UUID deliveryId = UUID.randomUUID();
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        insert into webhook_event
            (id, endpoint_id, event_type, body, idempotency_key, created_at)
        values (?, ?, ?, ?, ?, ?)
        """,
        eventId,
        endpointId,
        "demo.cancellation",
        "{\"type\":\"demo.cancellation\",\"data\":{}}",
        "integration-cancel-" + eventId,
        Timestamp.from(now));
    jdbcTemplate.update(
        """
        insert into delivery_job
            (id, event_id, endpoint_id, status, attempt_count, max_attempts,
             next_attempt_at, created_at, updated_at, version)
        values (?, ?, ?, ?, 0, 3, ?, ?, ?, 0)
        """,
        deliveryId,
        eventId,
        endpointId,
        status,
        Timestamp.from(nextAttemptAt),
        Timestamp.from(now),
        Timestamp.from(now));
    return deliveryId;
  }

  private static HttpServer startTarget() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/hooks", exchange -> handleTarget(exchange, false, false));
      server.createContext("/hooks/flaky", exchange -> handleTarget(exchange, true, false));
      server.createContext("/hooks/cancel", exchange -> handleTarget(exchange, false, true));
      server.start();
      return server;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private static void handleTarget(HttpExchange exchange, boolean flaky, boolean cancellation)
      throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    String timestamp = exchange.getRequestHeaders().getFirst("X-Webhook-Timestamp");
    String supplied = exchange.getRequestHeaders().getFirst("X-Webhook-Signature");
    String expected = new HmacSigner().sign("integration-secret", Long.parseLong(timestamp), body);
    RECEIVED_BODY.set(body);
    RECEIVED_SIGNATURE.set(supplied);

    int statusCode;
    if (!expected.equals(supplied)) {
      statusCode = 401;
    } else if (cancellation) {
      CANCELED_TARGET_COUNT.incrementAndGet();
      statusCode = 204;
    } else if (flaky) {
      statusCode = FLAKY_COUNT.incrementAndGet() <= 2 ? 503 : 204;
    } else {
      RECEIVED_COUNT.incrementAndGet();
      statusCode = 204;
    }

    byte[] response =
        statusCode == 503 ? "temporary failure".getBytes(StandardCharsets.UTF_8) : null;
    exchange.sendResponseHeaders(statusCode, response == null ? -1 : response.length);
    if (response != null) {
      exchange.getResponseBody().write(response);
    }
    exchange.close();
  }
}

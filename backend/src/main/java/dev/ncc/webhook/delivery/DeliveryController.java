package dev.ncc.webhook.delivery;

import dev.ncc.webhook.delivery.DeliveryDtos.DeliveryDetailResponse;
import dev.ncc.webhook.delivery.DeliveryDtos.DeliveryResponse;
import dev.ncc.webhook.delivery.DeliveryDtos.DeliveryStats;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {

  private final DeliveryQueryService service;
  private final DeliveryUpdates updates;

  public DeliveryController(DeliveryQueryService service, DeliveryUpdates updates) {
    this.service = service;
    this.updates = updates;
  }

  @GetMapping
  List<DeliveryResponse> recent(@RequestParam(defaultValue = "50") int limit) {
    return service.recent(limit);
  }

  @GetMapping("/stats")
  DeliveryStats stats() {
    return service.stats();
  }

  @GetMapping("/{id}")
  DeliveryDetailResponse detail(@PathVariable UUID id) {
    return service.detail(id);
  }

  @PostMapping("/{id}/replay")
  DeliveryResponse replay(@PathVariable UUID id) {
    return service.replay(id);
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter stream() {
    return updates.subscribe();
  }
}

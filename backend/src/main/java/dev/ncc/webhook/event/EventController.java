package dev.ncc.webhook.event;

import dev.ncc.webhook.event.EventDtos.PublishEventRequest;
import dev.ncc.webhook.event.EventDtos.PublishEventResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

  private final EventService service;

  public EventController(EventService service) {
    this.service = service;
  }

  @PostMapping
  ResponseEntity<PublishEventResponse> publish(@Valid @RequestBody PublishEventRequest request) {
    return ResponseEntity.accepted().body(service.publish(request));
  }
}

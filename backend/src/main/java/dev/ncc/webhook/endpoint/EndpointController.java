package dev.ncc.webhook.endpoint;

import dev.ncc.webhook.endpoint.EndpointDtos.CreateEndpointRequest;
import dev.ncc.webhook.endpoint.EndpointDtos.EndpointResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/endpoints")
public class EndpointController {

  private final EndpointService service;

  public EndpointController(EndpointService service) {
    this.service = service;
  }

  @PostMapping
  ResponseEntity<EndpointResponse> create(@Valid @RequestBody CreateEndpointRequest request) {
    EndpointResponse created = service.create(request);
    return ResponseEntity.created(URI.create("/api/endpoints/" + created.id())).body(created);
  }

  @GetMapping
  List<EndpointResponse> list() {
    return service.list();
  }
}

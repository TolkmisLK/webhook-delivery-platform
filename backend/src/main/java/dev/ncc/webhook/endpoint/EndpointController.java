package dev.ncc.webhook.endpoint;

import dev.ncc.webhook.endpoint.EndpointDtos.CreateEndpointRequest;
import dev.ncc.webhook.endpoint.EndpointDtos.EndpointResponse;
import dev.ncc.webhook.endpoint.EndpointDtos.RotateEndpointSecretRequest;
import dev.ncc.webhook.endpoint.EndpointDtos.SetEndpointStatusRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

  @PatchMapping("/{endpointId}/status")
  EndpointResponse setStatus(
      @PathVariable UUID endpointId, @Valid @RequestBody SetEndpointStatusRequest request) {
    return service.setStatus(endpointId, request);
  }

  @PatchMapping("/{endpointId}/secret")
  EndpointResponse rotateSecret(
      @PathVariable UUID endpointId, @Valid @RequestBody RotateEndpointSecretRequest request) {
    return service.rotateSecret(endpointId, request);
  }
}

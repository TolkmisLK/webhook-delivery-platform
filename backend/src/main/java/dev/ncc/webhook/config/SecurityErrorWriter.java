package dev.ncc.webhook.config;

import dev.ncc.webhook.common.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SecurityErrorWriter {

  private final Clock clock;
  private final JsonMapper jsonMapper;

  SecurityErrorWriter(Clock clock, JsonMapper jsonMapper) {
    this.clock = clock;
    this.jsonMapper = jsonMapper;
  }

  void write(HttpServletResponse response, HttpStatus status, String code, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    ApiError error =
        new ApiError(
            Instant.now(clock), status.value(), code, message, Map.of(), MDC.get("requestId"));
    jsonMapper.writeValue(response.getOutputStream(), error);
  }
}

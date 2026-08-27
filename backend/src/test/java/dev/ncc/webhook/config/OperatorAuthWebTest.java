package dev.ncc.webhook.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ncc.webhook.common.ApiExceptionHandler;
import dev.ncc.webhook.common.RequestIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(
    controllers = OperatorAuthController.class,
    properties = {
      "app.operator.username=test-operator",
      "app.operator.password=test-operator-password"
    })
@EnableConfigurationProperties(OperatorAccessProperties.class)
@Import({
  OperatorSecurityConfiguration.class,
  SecurityErrorWriter.class,
  ApiExceptionHandler.class,
  RequestIdFilter.class,
  OperatorAuthWebTest.ClockConfiguration.class
})
class OperatorAuthWebTest {

  @Autowired MockMvc mockMvc;
  @Autowired JsonMapper jsonMapper;

  @Test
  void returnsStableJsonWhenAuthenticationIsMissing() throws Exception {
    mockMvc
        .perform(get("/api/auth/session"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().exists(RequestIdFilter.HEADER))
        .andExpect(jsonPath("$.code").value("unauthenticated"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void protectsTheDeliveryEventStream() throws Exception {
    mockMvc
        .perform(get("/api/deliveries/stream"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("unauthenticated"));
  }

  @Test
  void requiresCsrfForLogin() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content(
                    """
                    {"username":"test-operator","password":"test-operator-password"}
                    """))
        .andExpect(status().isForbidden())
        .andExpect(header().exists(RequestIdFilter.HEADER))
        .andExpect(jsonPath("$.code").value("access_denied"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void rejectsCredentialsWithoutDisclosingWhichFieldFailed() throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        """
                        {"username":"test-operator","password":"wrong-password-value"}
                        """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("invalid_credentials"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(response).doesNotContain("wrong-password-value", "test-operator-password");
  }

  @Test
  void createsRotatedSessionAndInvalidatesItOnLogout() throws Exception {
    var csrfResult = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    MockHttpSession anonymousSession =
        (MockHttpSession) csrfResult.getRequest().getSession(false);
    var anonymousCsrf = jsonMapper.readTree(csrfResult.getResponse().getContentAsString());
    String previousSessionId = anonymousSession.getId();

    var loginResult =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .session(anonymousSession)
                    .header(
                        anonymousCsrf.get("headerName").asText(),
                        anonymousCsrf.get("token").asText())
                    .contentType("application/json")
                    .content(
                        """
                        {"username":"test-operator","password":"test-operator-password"}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("test-operator"))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andReturn();

    MockHttpSession authenticatedSession =
        (MockHttpSession) loginResult.getRequest().getSession(false);
    assertThat(authenticatedSession).isNotNull();
    assertThat(authenticatedSession.getId()).isNotEqualTo(previousSessionId);

    mockMvc
        .perform(get("/api/auth/session").session(authenticatedSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("test-operator"));

    var authenticatedCsrfResult =
        mockMvc
            .perform(get("/api/auth/csrf").session(authenticatedSession))
            .andExpect(status().isOk())
            .andReturn();
    var authenticatedCsrf =
        jsonMapper.readTree(authenticatedCsrfResult.getResponse().getContentAsString());

    mockMvc
        .perform(
            post("/api/auth/logout")
                .session(authenticatedSession)
                .header(
                    authenticatedCsrf.get("headerName").asText(),
                    authenticatedCsrf.get("token").asText()))
        .andExpect(status().isNoContent());

    assertThat(authenticatedSession.isInvalid()).isTrue();
  }

  @Test
  void exposesCsrfMetadataWithoutCreatingAnAuthenticatedSession() throws Exception {
    mockMvc
        .perform(get("/api/auth/csrf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
        .andExpect(jsonPath("$.token").isNotEmpty());
  }

  @TestConfiguration
  static class ClockConfiguration {

    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC);
    }
  }
}

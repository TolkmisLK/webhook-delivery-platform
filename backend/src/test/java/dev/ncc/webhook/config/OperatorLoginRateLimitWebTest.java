package dev.ncc.webhook.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ncc.webhook.common.ApiExceptionHandler;
import dev.ncc.webhook.common.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(
    controllers = OperatorAuthController.class,
    properties = {
      "app.operator.username=test-operator",
      "app.operator.password=test-operator-password",
      "app.operator.login-limit.client-max-attempts=1",
      "app.operator.login-limit.global-max-attempts=10"
    })
@EnableConfigurationProperties({OperatorAccessProperties.class, OperatorLoginLimitProperties.class})
@ExtendWith(OutputCaptureExtension.class)
@Import({
  OperatorSecurityConfiguration.class,
  OperatorLoginAttemptLimiter.class,
  OperatorAuthTelemetry.class,
  SecurityErrorWriter.class,
  ApiExceptionHandler.class,
  RequestIdFilter.class,
  OperatorAuthWebTest.ClockConfiguration.class
})
class OperatorLoginRateLimitWebTest {

  @Autowired MockMvc mockMvc;

  @Test
  void rejectsAttemptsOverTheClientQuotaWithRetryMetadata() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .with(csrf())
                .with(remoteAddress("192.0.2.10"))
                .contentType("application/json")
                .content(
                    """
                    {"username":"test-operator","password":"wrong-password-value"}
                    """))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/api/auth/login")
                .with(csrf())
                .with(remoteAddress("192.0.2.10"))
                .contentType("application/json")
                .content(
                    """
                    {"username":"test-operator","password":"test-operator-password"}
                    """))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "300"))
        .andExpect(jsonPath("$.code").value("login_rate_limited"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void missingCsrfDoesNotConsumeTheLoginQuota() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .with(remoteAddress("192.0.2.11"))
                .contentType("application/json")
                .content(
                    """
                    {"username":"test-operator","password":"test-operator-password"}
                    """))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/auth/login")
                .with(csrf())
                .with(remoteAddress("192.0.2.11"))
                .contentType("application/json")
                .content(
                    """
                    {"username":"test-operator","password":"test-operator-password"}
                    """))
        .andExpect(status().isOk());
  }

  @Test
  void failureLogsDoNotContainAttemptedCredentials(CapturedOutput output) throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .with(csrf())
                .with(remoteAddress("192.0.2.12"))
                .contentType("application/json")
                .content(
                    """
                    {"username":"attempted-identity-marker","password":"attempted-password-marker"}
                    """))
        .andExpect(status().isUnauthorized());

    assertThat(output).doesNotContain("attempted-identity-marker", "attempted-password-marker");
  }

  private RequestPostProcessor remoteAddress(String remoteAddress) {
    return request -> {
      request.setRemoteAddr(remoteAddress);
      return request;
    };
  }
}

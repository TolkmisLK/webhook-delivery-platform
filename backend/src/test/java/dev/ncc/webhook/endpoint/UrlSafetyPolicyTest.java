package dev.ncc.webhook.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ncc.webhook.config.SecurityProperties;
import dev.ncc.webhook.config.UrlSafetyPolicy;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UrlSafetyPolicyTest {

  @Test
  void blocksPrivateTargetsByDefault() {
    UrlSafetyPolicy policy = new UrlSafetyPolicy(new SecurityProperties());

    assertThatThrownBy(() -> policy.validate("http://127.0.0.1/hooks"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("non-public");
    assertThatThrownBy(() -> policy.validate("http://100.64.0.1/hooks"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("non-public");
    assertThatThrownBy(() -> policy.validate("http://[fd00::1]/hooks"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("non-public");
  }

  @Test
  void rejectsCredentialsFragmentsAndUnexpectedPorts() {
    SecurityProperties properties = new SecurityProperties();
    properties.setAllowPrivateTargets(true);
    UrlSafetyPolicy policy = new UrlSafetyPolicy(properties);

    assertThatThrownBy(() -> policy.validate("http://user@example.com/hooks"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> policy.validate("https://example.com/hooks#secret"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> policy.validate("https://example.com:8443/hooks"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("port");
  }

  @Test
  void allowsConfiguredDevelopmentTargets() {
    SecurityProperties properties = new SecurityProperties();
    properties.setAllowPrivateTargets(true);
    properties.setAllowedPorts(Set.of(8090));
    UrlSafetyPolicy policy = new UrlSafetyPolicy(properties);

    assertThat(policy.validate("http://127.0.0.1:8090/hooks").getPort()).isEqualTo(8090);
  }
}

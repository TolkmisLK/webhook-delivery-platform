package dev.ncc.webhook;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

  @Test
  void keepsApplicationModulesAcyclic() {
    ApplicationModules.of(WebhookDeliveryApplication.class).verify();
  }
}

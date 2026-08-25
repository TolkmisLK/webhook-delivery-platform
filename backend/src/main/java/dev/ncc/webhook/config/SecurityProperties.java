package dev.ncc.webhook.config;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security")
public class SecurityProperties {

  private boolean allowPrivateTargets = false;
  private Set<Integer> allowedPorts = new LinkedHashSet<>(Set.of(80, 443));
  private String masterKey = "";

  public boolean isAllowPrivateTargets() {
    return allowPrivateTargets;
  }

  public void setAllowPrivateTargets(boolean allowPrivateTargets) {
    this.allowPrivateTargets = allowPrivateTargets;
  }

  public Set<Integer> getAllowedPorts() {
    return allowedPorts;
  }

  public void setAllowedPorts(Set<Integer> allowedPorts) {
    this.allowedPorts = allowedPorts;
  }

  public String getMasterKey() {
    return masterKey;
  }

  public void setMasterKey(String masterKey) {
    this.masterKey = masterKey;
  }
}

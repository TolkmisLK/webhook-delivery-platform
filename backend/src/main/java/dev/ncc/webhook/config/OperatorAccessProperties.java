package dev.ncc.webhook.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.operator")
public class OperatorAccessProperties {

  @NotBlank
  @Size(max = 120)
  private String username = "";

  @NotBlank
  @Size(min = 16, max = 512)
  private String password = "";

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}

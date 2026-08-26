package dev.ncc.webhook.endpoint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_endpoint")
public class WebhookEndpoint {

  @Id private UUID id;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, length = 2048)
  private String url;

  @Column(name = "encrypted_secret", nullable = false, length = 1024)
  private String encryptedSecret;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Version private long version;

  protected WebhookEndpoint() {}

  WebhookEndpoint(UUID id, String name, String url, String encryptedSecret, Instant createdAt) {
    this.id = id;
    this.name = name;
    this.url = url;
    this.encryptedSecret = encryptedSecret;
    this.active = true;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getUrl() {
    return url;
  }

  public String getEncryptedSecret() {
    return encryptedSecret;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public long getVersion() {
    return version;
  }

  void setActive(boolean active) {
    this.active = active;
  }

  void rotateSecret(String encryptedSecret) {
    this.encryptedSecret = encryptedSecret;
  }
}

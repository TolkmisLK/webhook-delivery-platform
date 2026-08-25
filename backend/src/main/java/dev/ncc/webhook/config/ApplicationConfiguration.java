package dev.ncc.webhook.config;

import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  HttpClient deliveryHttpClient(DeliveryProperties properties) {
    return HttpClient.newBuilder()
        .connectTimeout(properties.getRequestTimeout())
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }
}

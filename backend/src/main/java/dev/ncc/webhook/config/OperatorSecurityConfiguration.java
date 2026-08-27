package dev.ncc.webhook.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class OperatorSecurityConfiguration {

  @Bean
  SecurityFilterChain operatorSecurityFilterChain(
      HttpSecurity http,
      SecurityContextRepository securityContextRepository,
      CsrfTokenRepository csrfTokenRepository,
      SecurityErrorWriter errors)
      throws Exception {
    http.securityContext(
            context -> context.securityContextRepository(securityContextRepository))
        .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/api/auth/csrf", "/api/auth/login")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .hasRole("OPERATOR")
                    .anyRequest()
                    .permitAll())
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            errors.write(
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "unauthenticated",
                                "Authentication is required"))
                    .accessDeniedHandler(
                        (request, response, exception) ->
                            errors.write(
                                response,
                                HttpStatus.FORBIDDEN,
                                "access_denied",
                                "Request could not be authorized")))
        .requestCache(cache -> cache.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable());
    return http.build();
  }

  @Bean
  PasswordEncoder operatorPasswordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService operatorUserDetails(
      OperatorAccessProperties properties, PasswordEncoder passwordEncoder) {
    var operator =
        User.withUsername(properties.getUsername())
            .password(passwordEncoder.encode(properties.getPassword()))
            .roles("OPERATOR")
            .build();
    return new InMemoryUserDetailsManager(operator);
  }

  @Bean
  AuthenticationManager operatorAuthenticationManager(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    var provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
  }

  @Bean
  SecurityContextRepository operatorSecurityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  CsrfTokenRepository operatorCsrfTokenRepository() {
    var repository = new HttpSessionCsrfTokenRepository();
    repository.setHeaderName("X-XSRF-TOKEN");
    return repository;
  }

  @Bean
  SessionAuthenticationStrategy operatorSessionAuthenticationStrategy(
      CsrfTokenRepository csrfTokenRepository) {
    return new CompositeSessionAuthenticationStrategy(
        List.of(
            new ChangeSessionIdAuthenticationStrategy(),
            new CsrfAuthenticationStrategy(csrfTokenRepository)));
  }

  @Bean
  LogoutHandler operatorLogoutHandler(CsrfTokenRepository csrfTokenRepository) {
    return new CompositeLogoutHandler(
        new CsrfLogoutHandler(csrfTokenRepository),
        new SecurityContextLogoutHandler(),
        new CookieClearingLogoutHandler("JSESSIONID"));
  }
}

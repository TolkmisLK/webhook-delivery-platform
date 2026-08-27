package dev.ncc.webhook.config;

import dev.ncc.webhook.common.InvalidCredentialsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class OperatorAuthController {

  private static final Logger logger = LoggerFactory.getLogger(OperatorAuthController.class);

  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository securityContextRepository;
  private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
  private final LogoutHandler logoutHandler;

  OperatorAuthController(
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository,
      SessionAuthenticationStrategy sessionAuthenticationStrategy,
      LogoutHandler logoutHandler) {
    this.authenticationManager = authenticationManager;
    this.securityContextRepository = securityContextRepository;
    this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    this.logoutHandler = logoutHandler;
  }

  @GetMapping("/csrf")
  CsrfResponse csrf(CsrfToken csrfToken) {
    return new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getToken());
  }

  @PostMapping("/login")
  SessionResponse login(
      @Valid @RequestBody LoginRequest login,
      HttpServletRequest request,
      HttpServletResponse response) {
    try {
      Authentication authentication =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(
                  login.username(), login.password()));
      sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(authentication);
      SecurityContextHolder.setContext(context);
      securityContextRepository.saveContext(context, request, response);
      logger.info("operator_login_succeeded username={}", authentication.getName());
      return new SessionResponse(authentication.getName());
    } catch (AuthenticationException exception) {
      logger.warn("operator_login_failed");
      throw new InvalidCredentialsException();
    }
  }

  @GetMapping("/session")
  SessionResponse session(Principal principal) {
    return new SessionResponse(principal.getName());
  }

  @PostMapping("/logout")
  ResponseEntity<Void> logout(
      Authentication authentication,
      HttpServletRequest request,
      HttpServletResponse response) {
    String username = authentication.getName();
    logoutHandler.logout(request, response, authentication);
    logger.info("operator_logout_succeeded username={}", username);
    return ResponseEntity.noContent().build();
  }

  public record LoginRequest(
      @NotBlank @Size(max = 120) String username,
      @NotBlank @Size(max = 512) String password) {}

  public record SessionResponse(String username) {}

  public record CsrfResponse(String headerName, String token) {}
}

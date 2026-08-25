package dev.ncc.webhook.config;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class UrlSafetyPolicy {

  private final SecurityProperties properties;

  public UrlSafetyPolicy(SecurityProperties properties) {
    this.properties = properties;
  }

  public URI validate(String value) {
    URI uri;
    try {
      uri = URI.create(value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Endpoint URL is invalid", exception);
    }

    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) {
      throw new IllegalArgumentException("Endpoint URL must use HTTP or HTTPS");
    }
    if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
      throw new IllegalArgumentException(
          "Endpoint URL must contain a host and no credentials or fragment");
    }

    int port = uri.getPort() == -1 ? (scheme.equals("https") ? 443 : 80) : uri.getPort();
    if (!properties.getAllowedPorts().contains(port)) {
      throw new IllegalArgumentException("Endpoint port is not allowed");
    }

    if (!properties.isAllowPrivateTargets()) {
      assertPublicAddresses(uri.getHost());
    }
    return uri;
  }

  private void assertPublicAddresses(String host) {
    try {
      InetAddress[] addresses = InetAddress.getAllByName(host);
      if (addresses.length == 0) {
        throw new IllegalArgumentException("Endpoint host did not resolve");
      }
      for (InetAddress address : addresses) {
        if (address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()
            || isCarrierGradeNat(address)
            || isIpv6UniqueLocal(address)) {
          throw new IllegalArgumentException("Endpoint host resolves to a non-public address");
        }
      }
    } catch (UnknownHostException exception) {
      throw new IllegalArgumentException("Endpoint host could not be resolved", exception);
    }
  }

  private boolean isCarrierGradeNat(InetAddress address) {
    byte[] bytes = address.getAddress();
    return bytes.length == 4
        && Byte.toUnsignedInt(bytes[0]) == 100
        && (Byte.toUnsignedInt(bytes[1]) & 0b1100_0000) == 0b0100_0000;
  }

  private boolean isIpv6UniqueLocal(InetAddress address) {
    byte[] bytes = address.getAddress();
    return bytes.length == 16 && (Byte.toUnsignedInt(bytes[0]) & 0b1111_1110) == 0b1111_1100;
  }
}

package dev.herald.core.net;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Server-side request forgery (SSRF) protection for tenant-supplied webhook URLs.
 *
 * <p>A shared delivery service runs inside the platform's network, so a malicious tenant could
 * register {@code http://169.254.169.254/} (cloud metadata) or an internal service. The guard
 * validates the URL shape at registration time and re-checks the <em>resolved</em> addresses at
 * delivery time, which also defeats DNS names that point at private ranges.
 */
public final class EndpointUrlGuard {

    private static final int MAX_URL_LENGTH = 2048;

    private final boolean allowPrivateNetworks;
    private final boolean requireHttps;

    public EndpointUrlGuard(boolean allowPrivateNetworks, boolean requireHttps) {
        this.allowPrivateNetworks = allowPrivateNetworks;
        this.requireHttps = requireHttps;
    }

    /** Validates syntax, scheme and literal hosts. Does not perform DNS resolution. */
    public URI validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank() || rawUrl.length() > MAX_URL_LENGTH) {
            throw new UnsafeUrlException("URL must be between 1 and " + MAX_URL_LENGTH + " characters");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new UnsafeUrlException("URL is malformed");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !(scheme.equals("http") && !requireHttps)) {
            throw new UnsafeUrlException(requireHttps ? "URL must use https" : "URL must use http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new UnsafeUrlException("URL must include a host");
        }
        if (uri.getUserInfo() != null) {
            throw new UnsafeUrlException("URL must not contain credentials");
        }
        if (!allowPrivateNetworks) {
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".internal")) {
                throw new UnsafeUrlException("URL host is not publicly routable");
            }
            if (isIpLiteral(host)) {
                checkAddress(literal(host));
            }
        }
        return uri;
    }

    /** Resolves the host and rejects it if any address is non-public. */
    public void checkResolved(URI uri) {
        if (allowPrivateNetworks) {
            return;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                checkAddress(address);
            }
        } catch (UnknownHostException e) {
            throw new UnsafeUrlException("Host could not be resolved: " + uri.getHost());
        }
    }

    static void checkAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isCarrierGradeNat(address)
                || isUniqueLocalIpv6(address)
                || isReservedIpv4(address)) {
            throw new UnsafeUrlException("URL resolves to a non-public address");
        }
    }

    private static boolean isCarrierGradeNat(InetAddress address) {
        byte[] b = address.getAddress();
        return address instanceof Inet4Address && (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64;
    }

    private static boolean isUniqueLocalIpv6(InetAddress address) {
        return address instanceof Inet6Address && (address.getAddress()[0] & 0xFE) == 0xFC;
    }

    private static boolean isReservedIpv4(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        int first = address.getAddress()[0] & 0xFF;
        return first == 0 || first >= 240;
    }

    private static boolean isIpLiteral(String host) {
        return host.startsWith("[") || host.chars().allMatch(c -> Character.isDigit(c) || c == '.');
    }

    private static InetAddress literal(String host) {
        try {
            // For IP literals getByName parses without a DNS lookup.
            return InetAddress.getByName(host.replace("[", "").replace("]", ""));
        } catch (UnknownHostException e) {
            throw new UnsafeUrlException("URL host is not a valid IP address");
        }
    }
}

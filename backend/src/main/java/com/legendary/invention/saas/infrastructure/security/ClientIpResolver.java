package com.legendary.invention.saas.infrastructure.security;

import com.legendary.invention.saas.infrastructure.config.WebProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class ClientIpResolver {

    private final WebProperties webProperties;

    public ClientIpResolver(WebProperties webProperties) {
        this.webProperties = webProperties;
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = normalizeIp(request.getRemoteAddr());
        if (!webProperties.isTrustForwardedHeaders() || !isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String forwarded = resolveForwardedHeader(request.getHeader("Forwarded"));
        if (StringUtils.hasText(forwarded)) {
            return forwarded;
        }

        String xForwardedFor = resolveXForwardedFor(request.getHeader("X-Forwarded-For"));
        return StringUtils.hasText(xForwardedFor) ? xForwardedFor : remoteAddr;
    }

    private String resolveForwardedHeader(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return "";
        }

        List<String> candidates = new ArrayList<>();
        for (String entry : headerValue.split(",")) {
            String[] parts = entry.split(";");
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.regionMatches(true, 0, "for=", 0, 4)) {
                    candidates.add(trimmed.substring(4));
                    break;
                }
            }
        }
        return resolveChain(candidates);
    }

    private String resolveXForwardedFor(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return "";
        }
        List<String> candidates = List.of(headerValue.split(","));
        return resolveChain(candidates);
    }

    private String resolveChain(List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }

        String fallback = "";
        for (int index = candidates.size() - 1; index >= 0; index--) {
            String candidate = normalizeIp(candidates.get(index));
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            if (StringUtils.hasText(fallback)) {
                // keep the leftmost non-empty candidate as a fallback when every
                // hop in the chain is trusted.
                fallback = candidate;
            } else {
                fallback = candidate;
            }
            if (!isTrustedProxy(candidate)) {
                return candidate;
            }
        }
        return fallback;
    }

    private String normalizeIp(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 1) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.startsWith("for=")) {
            trimmed = trimmed.substring(4);
        }
        if (trimmed.startsWith("[")) {
            int closingBracket = trimmed.indexOf(']');
            if (closingBracket > 0) {
                trimmed = trimmed.substring(1, closingBracket);
            }
        }
        if (trimmed.contains(":") && trimmed.indexOf(':') == trimmed.lastIndexOf(':') && trimmed.contains(".")) {
            trimmed = trimmed.substring(0, trimmed.lastIndexOf(':'));
        }
        return "unknown".equalsIgnoreCase(trimmed) ? "" : trimmed;
    }

    private boolean isTrustedProxy(String address) {
        if (!StringUtils.hasText(address)) {
            return false;
        }
        return webProperties.getTrustedProxyCidrs().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .anyMatch(cidr -> matchesCidr(address, cidr));
    }

    private boolean matchesCidr(String address, String cidr) {
        String normalizedAddress = normalizeIp(address);
        if (!StringUtils.hasText(normalizedAddress) || !StringUtils.hasText(cidr)) {
            return false;
        }

        String trimmedCidr = cidr.trim();
        if (!trimmedCidr.contains("/")) {
            return Objects.equals(normalizedAddress, normalizeIp(trimmedCidr));
        }

        String[] parts = trimmedCidr.split("/", 2);
        if (parts.length != 2) {
            return false;
        }

        try {
            InetAddress addressBytes = InetAddress.getByName(normalizedAddress);
            InetAddress networkBytes = InetAddress.getByName(parts[0].trim());
            int prefixLength = Integer.parseInt(parts[1].trim());
            return matchesAddressPrefix(addressBytes.getAddress(), networkBytes.getAddress(), prefixLength);
        } catch (UnknownHostException | NumberFormatException ex) {
            return false;
        }
    }

    private boolean matchesAddressPrefix(byte[] address, byte[] network, int prefixLength) {
        if (address.length != network.length || prefixLength < 0 || prefixLength > address.length * 8) {
            return false;
        }

        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;

        for (int index = 0; index < fullBytes; index++) {
            if (address[index] != network[index]) {
                return false;
            }
        }

        if (remainingBits == 0) {
            return true;
        }

        int mask = 0xFF << (8 - remainingBits);
        return (address[fullBytes] & mask) == (network[fullBytes] & mask);
    }
}

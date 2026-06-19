package com.lumira.common.web.repeatsubmit;

import com.lumira.common.web.WebProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Pattern;

@Component("repeatSubmitClientIpResolver")
public class ClientIpResolver {

    private static final Pattern IPV4_LITERAL = Pattern.compile(
        "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)"
            + "(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$"
    );
    private static final Pattern IPV6_LITERAL_CHARS = Pattern.compile("^[0-9A-Fa-f:.]+$");

    private final WebProperties webProperties;

    public ClientIpResolver(WebProperties webProperties) {
        this.webProperties = webProperties;
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = normalizeIp(request.getRemoteAddr());
        if (!webProperties.isTrustForwardedHeaders()) {
            return remoteAddr;
        }
        if (!isTrustedProxy(remoteAddr, webProperties.getTrustedProxyCidrs())) {
            return remoteAddr;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            String clientIp = normalizeIp(forwardedFor.split(",")[0]);
            return isValidIp(clientIp) ? clientIp : remoteAddr;
        }
        String forwarded = request.getHeader("Forwarded");
        if (StringUtils.hasText(forwarded)) {
            for (String part : forwarded.split(";")) {
                String trimmed = part.trim();
                if (trimmed.regionMatches(true, 0, "for=", 0, 4)) {
                    String clientIp = normalizeIp(trimmed.substring(4));
                    return isValidIp(clientIp) ? clientIp : remoteAddr;
                }
            }
        }
        return remoteAddr;
    }

    private String normalizeIp(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 1) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
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

    private boolean isValidIp(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        if (IPV4_LITERAL.matcher(value).matches()) {
            return true;
        }
        if (!value.contains(":") || !IPV6_LITERAL_CHARS.matcher(value).matches()) {
            return false;
        }
        try {
            return InetAddress.getByName(value).getAddress().length == 16;
        } catch (UnknownHostException ignored) {
            return false;
        }
    }

    private boolean isTrustedProxy(String remoteAddr, List<String> trustedProxyCidrs) {
        if (!StringUtils.hasText(remoteAddr) || trustedProxyCidrs == null || trustedProxyCidrs.isEmpty()) {
            return false;
        }
        for (String trustedProxyCidr : trustedProxyCidrs) {
            if (matchesCidr(remoteAddr, trustedProxyCidr)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesCidr(String ip, String cidr) {
        if (!StringUtils.hasText(cidr)) {
            return false;
        }
        String[] parts = cidr.trim().split("/", 2);
        try {
            InetAddress address = InetAddress.getByName(ip);
            InetAddress network = InetAddress.getByName(parts[0]);
            byte[] addressBytes = address.getAddress();
            byte[] networkBytes = network.getAddress();
            if (addressBytes.length != networkBytes.length) {
                return false;
            }
            int prefixLength = parts.length == 2 ? Integer.parseInt(parts[1]) : addressBytes.length * 8;
            if (prefixLength < 0 || prefixLength > addressBytes.length * 8) {
                return false;
            }
            return matchesPrefix(addressBytes, networkBytes, prefixLength);
        } catch (IllegalArgumentException | UnknownHostException ignored) {
            return false;
        }
    }

    private boolean matchesPrefix(byte[] address, byte[] network, int prefixLength) {
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

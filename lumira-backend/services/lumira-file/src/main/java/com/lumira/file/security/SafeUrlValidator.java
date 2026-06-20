package com.lumira.file.security;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

@Component
public class SafeUrlValidator {

    private static final Set<String> BLOCKED_HOSTS = Set.of("localhost", "metadata.google.internal");

    public URI validateHttpUrl(String value) {
        if (!StringUtils.hasText(value)) {
            throw rejected();
        }
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException exception) {
            throw rejected();
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw rejected();
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw rejected();
        }
        String normalizedHost = trimTrailingDot(host.toLowerCase(Locale.ROOT));
        if (BLOCKED_HOSTS.contains(normalizedHost)) {
            throw rejected();
        }
        validateResolvedAddresses(normalizedHost);
        return uri;
    }

    private void validateResolvedAddresses(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                throw rejected();
            }
            for (InetAddress address : addresses) {
                if (isUnsafeAddress(address)) {
                    throw rejected();
                }
            }
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw rejected();
        }
    }

    private boolean isUnsafeAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            return first == 0
                    || first == 10
                    || first == 127
                    || (first == 100 && second == 100)
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168)
                    || first >= 224;
        }
        return false;
    }

    private String trimTrailingDot(String value) {
        String result = value;
        while (result.endsWith(".")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private BizException rejected() {
        return new BizException(
                ErrorCode.BAD_REQUEST,
                "Remote storage endpoint is not allowed",
                "Remote storage endpoint is not accessible or not allowed"
        );
    }
}

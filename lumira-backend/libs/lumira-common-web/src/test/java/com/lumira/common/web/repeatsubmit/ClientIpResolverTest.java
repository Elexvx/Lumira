package com.lumira.common.web.repeatsubmit;

import com.lumira.common.web.WebProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {

    @Test
    void resolveShouldIgnoreForwardedHeaderFromUntrustedPeer() {
        WebProperties properties = new WebProperties();
        properties.setTrustForwardedHeaders(true);
        properties.setTrustedProxyCidrs(List.of("10.0.0.0/8"));
        ClientIpResolver resolver = new ClientIpResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.7");

        assertEquals("203.0.113.10", resolver.resolve(request));
    }

    @Test
    void resolveShouldTrustForwardedHeaderFromTrustedPeer() {
        WebProperties properties = new WebProperties();
        properties.setTrustForwardedHeaders(true);
        properties.setTrustedProxyCidrs(List.of("10.0.0.0/8"));
        ClientIpResolver resolver = new ClientIpResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("X-Forwarded-For", "198.51.100.7, 10.1.2.3");

        assertEquals("198.51.100.7", resolver.resolve(request));
    }

    @Test
    void resolveShouldRejectInvalidForwardedClientIpFromTrustedPeer() {
        WebProperties properties = new WebProperties();
        properties.setTrustForwardedHeaders(true);
        properties.setTrustedProxyCidrs(List.of("10.0.0.0/8"));
        ClientIpResolver resolver = new ClientIpResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("X-Forwarded-For", "evil-client, 198.51.100.7");

        assertEquals("10.1.2.3", resolver.resolve(request));
    }

    @Test
    void resolveShouldRejectResolvableHostnameFromTrustedPeer() {
        WebProperties properties = new WebProperties();
        properties.setTrustForwardedHeaders(true);
        properties.setTrustedProxyCidrs(List.of("10.0.0.0/8"));
        ClientIpResolver resolver = new ClientIpResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("X-Forwarded-For", "localhost");

        assertEquals("10.1.2.3", resolver.resolve(request));
    }

    @Test
    void resolveShouldAcceptQuotedIpv6ForwardedHeaderFromTrustedPeer() {
        WebProperties properties = new WebProperties();
        properties.setTrustForwardedHeaders(true);
        properties.setTrustedProxyCidrs(List.of("2001:db8::/32"));
        ClientIpResolver resolver = new ClientIpResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("2001:db8::1");
        request.addHeader("Forwarded", "for=\"[2001:db8::7]\";proto=https");

        assertEquals("2001:db8::7", resolver.resolve(request));
    }
}

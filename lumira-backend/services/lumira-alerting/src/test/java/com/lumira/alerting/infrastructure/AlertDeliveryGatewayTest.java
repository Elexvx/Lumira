package com.lumira.alerting.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.sun.net.httpserver.HttpServer;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;

class AlertDeliveryGatewayTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void blocksUntrustedWebhookAndPrivateSmtpTargets() {
        AlertDeliveryGateway gateway = gateway(null, false);

        assertThatThrownBy(() -> gateway.validateConfiguration("WECOM_WEBHOOK", Map.of(
                "webhookUrl", "https://example.com/hook"
        ))).isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        assertThatThrownBy(() -> gateway.validateConfiguration("EMAIL_CUSTOM_SMTP", Map.of(
                "host", "127.0.0.1", "port", 25, "username", "u", "password", "p", "from", "a@example.com"
        ))).isInstanceOf(BizException.class).hasMessageContaining("Private or local");
    }

    @Test
    void sendsToLocalWebhookMockAndEnforcesPerChannelRateLimit() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            byte[] response = "{\"errcode\":0,\"msgid\":\"mock-1\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        AlertDeliveryGateway gateway = gateway(null, true);
        Map<String, Object> config = Map.of(
                "webhookUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/hook",
                "rateLimitPerMinute", 1
        );
        AlertingRepository.ChannelRecord channel = channel(7, "WECOM_WEBHOOK");
        String payload = "{\"title\":\"mock\",\"severity\":\"INFO\",\"summary\":\"test\",\"detailsUrl\":\"\"}";

        AlertDeliveryGateway.ProviderResult first = gateway.send(channel, config, "webhook", "TEST", payload);
        AlertDeliveryGateway.ProviderResult second = gateway.send(channel, config, "webhook", "TEST", payload);

        assertThat(first.success()).isTrue();
        assertThat(first.providerMessageId()).isEqualTo("mock-1");
        assertThat(second.success()).isFalse();
        assertThat(second.retryable()).isTrue();
        assertThat(second.error()).contains("rate limit");
    }

    @Test
    void sendsEmailThroughConfiguredSystemMailSenderMock() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mimeMessage);
        AlertDeliveryGateway gateway = gateway(sender, false);
        AlertingRepository.ChannelRecord channel = channel(9, "EMAIL_SYSTEM_SMTP");

        AlertDeliveryGateway.ProviderResult result = gateway.send(
                channel,
                Map.of("from", "alerts@example.com"),
                "recipient@example.com",
                "FIRING",
                "{\"title\":\"mock\",\"severity\":\"CRITICAL\",\"summary\":\"test\",\"detailsUrl\":\"https://example.com\"}"
        );

        assertThat(result.success()).isTrue();
        verify(sender).send(mimeMessage);
    }

    @SuppressWarnings("unchecked")
    private static AlertDeliveryGateway gateway(JavaMailSender sender, boolean allowPrivateTargets) {
        ObjectProvider<JavaMailSender> mailProvider = mock(ObjectProvider.class);
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        when(mailProvider.getIfAvailable()).thenReturn(sender);
        when(redisProvider.getIfAvailable()).thenReturn(null);
        return new AlertDeliveryGateway(new ObjectMapper(), mailProvider, redisProvider, allowPrivateTargets);
    }

    private static AlertingRepository.ChannelRecord channel(long id, String type) {
        return new AlertingRepository.ChannelRecord(id, "test", type, true, "encrypted", "fp",
                null, null, null, 1, LocalDateTime.now());
    }
}

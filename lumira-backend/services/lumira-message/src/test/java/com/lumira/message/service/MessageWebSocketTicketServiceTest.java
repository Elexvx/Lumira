package com.lumira.message.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.security.CurrentUser;
import com.lumira.message.config.MessageProperties;
import com.lumira.message.infrastructure.redis.CacheTemplate;
import com.lumira.message.vo.MessageVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageWebSocketTicketServiceTest {

    @Mock
    private CacheTemplate cacheTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void issue_shouldPersistOneTimeTicketWithExpectedTtl() {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setWsTicketExpiresInSeconds(45L);
        MessageWebSocketTicketService ticketService = new MessageWebSocketTicketService(cacheTemplate, objectMapper, messageProperties);

        MessageVO.WebSocketTicketVO ticket = ticketService.issue(currentUser());

        assertThat(ticket.getTicket()).isNotBlank();
        assertThat(ticket.getExpiresInSeconds()).isEqualTo(45L);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(cacheTemplate).put(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());
        assertThat(keyCaptor.getValue()).startsWith("message:ws-ticket:");
        assertThat(valueCaptor.getValue()).contains("\"sessionId\":\"session-1\"");
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofSeconds(45L));
    }

    @Test
    void consume_shouldLoadPayloadAndRemoveTheTicketKey() throws Exception {
        MessageProperties messageProperties = new MessageProperties();
        MessageWebSocketTicketService ticketService = new MessageWebSocketTicketService(cacheTemplate, objectMapper, messageProperties);
        when(cacheTemplate.getAndRemove("message:ws-ticket:ticket-1")).thenReturn(
                objectMapper.writeValueAsString(new MessageWebSocketTicketService.TicketPayload("session-1", 1001L, 3))
        );

        MessageWebSocketTicketService.TicketPayload payload = ticketService.consume(" ticket-1 ");

        assertThat(payload).isEqualTo(new MessageWebSocketTicketService.TicketPayload("session-1", 1001L, 3));
    }

    private CurrentUser currentUser() {
        return new CurrentUser(1001L, "alice", 1001L, "session-1", 3, true, Set.of("message:message:view"));
    }
}

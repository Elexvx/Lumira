package com.lumira.api.message;

public record MessageWebSocketTicketDTO(String ticket, Long expiresInSeconds) {
}

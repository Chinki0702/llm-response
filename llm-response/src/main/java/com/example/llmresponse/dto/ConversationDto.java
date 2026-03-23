package com.example.llmresponse.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationDto(
        UUID id,
        String userId,
        Instant createdAt,
        List<MessageDto> messages
) {
}

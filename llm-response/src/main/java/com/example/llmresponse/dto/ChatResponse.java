package com.example.llmresponse.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatResponse(
        UUID conversationId,
        UUID messageId,
        String reply,
        Instant timestamp
) {
}

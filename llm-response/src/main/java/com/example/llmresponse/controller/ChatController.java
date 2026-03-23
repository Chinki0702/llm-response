package com.example.llmresponse.controller;

import com.example.llmresponse.dto.ChatRequest;
import com.example.llmresponse.dto.ChatResponse;
import com.example.llmresponse.dto.ConversationDto;
import com.example.llmresponse.service.ConversationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ConversationService conversationService;

    public ChatController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * Send a message and receive an AI-generated response.
     *
     * @param idempotencyKey optional header to prevent duplicate processing
     * @param request        the chat request body
     * @return the AI response with conversation metadata
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ChatRequest request) {

        log.info("POST /api/chat - userId={}, idempotencyKey={}", request.userId(), idempotencyKey);
        ChatResponse response = conversationService.chat(request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve a conversation by ID, including full message history.
     */
    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<ConversationDto> getConversation(@PathVariable UUID conversationId) {
        log.info("GET /api/conversations/{}", conversationId);
        ConversationDto dto = conversationService.getConversation(conversationId);
        return ResponseEntity.ok(dto);
    }

    /**
     * List all conversations for a user.
     */
    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationDto>> getUserConversations(@RequestParam String userId) {
        log.info("GET /api/conversations?userId={}", userId);
        List<ConversationDto> conversations = conversationService.getUserConversations(userId);
        return ResponseEntity.ok(conversations);
    }
}

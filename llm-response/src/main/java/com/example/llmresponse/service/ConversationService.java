package com.example.llmresponse.service;

import com.example.llmresponse.dto.ChatRequest;
import com.example.llmresponse.dto.ChatResponse;
import com.example.llmresponse.dto.ConversationDto;
import com.example.llmresponse.dto.MessageDto;
import com.example.llmresponse.entity.Conversation;
import com.example.llmresponse.entity.IdempotencyRecord;
import com.example.llmresponse.entity.Message;
import com.example.llmresponse.repository.ConversationRepository;
import com.example.llmresponse.repository.IdempotencyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final ResilientLlmService llmService;
    private final ObjectMapper objectMapper;

    public ConversationService(ConversationRepository conversationRepository,
                               IdempotencyRepository idempotencyRepository,
                               ResilientLlmService llmService,
                               ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes a chat request: stores user message, calls LLM, stores response, and returns the reply.
     * Supports idempotency via an optional idempotency key.
     */
    @Transactional
    public ChatResponse chat(ChatRequest request, String idempotencyKey) {
        log.info("Processing chat request for userId={}, conversationId={}, idempotencyKey={}",
                request.userId(), request.conversationId(), idempotencyKey);

        // Check idempotency — key must match AND request content must be identical
        String requestHash = hashRequest(request);
        if (idempotencyKey != null) {
            Optional<IdempotencyRecord> existing = idempotencyRepository.findById(idempotencyKey);
            if (existing.isPresent()) {
                if (existing.get().getRequestHash().equals(requestHash)) {
                    log.info("Duplicate request detected for idempotencyKey={}, returning cached response", idempotencyKey);
                    return deserializeResponse(existing.get().getResponseBody());
                }
                log.warn("Idempotency key={} reused with different request body — processing as new request", idempotencyKey);
            }
        }

        // Resolve or create conversation
        Conversation conversation = resolveConversation(request);

        // Store user message
        Message userMessage = new Message(Message.Role.USER, request.message());
        conversation.addMessage(userMessage);
        conversationRepository.save(conversation);
        log.debug("Stored user message in conversation={}", conversation.getId());

        // Call LLM
        String aiReply;
        try {
            aiReply = llmService.generate(request.message());
        } catch (LlmException ex) {
            log.error("LLM call failed for conversation={}: {}", conversation.getId(), ex.getMessage());
            throw ex;
        }

        // Store assistant message
        Message assistantMessage = new Message(Message.Role.ASSISTANT, aiReply);
        conversation.addMessage(assistantMessage);
        conversationRepository.save(conversation);
        log.debug("Stored assistant message in conversation={}", conversation.getId());

        ChatResponse response = new ChatResponse(
                conversation.getId(),
                assistantMessage.getId(),
                aiReply,
                assistantMessage.getTimestamp()
        );

        // Store idempotency record
        if (idempotencyKey != null) {
            idempotencyRepository.save(new IdempotencyRecord(idempotencyKey, requestHash, serializeResponse(response)));
            log.debug("Stored idempotency record for key={}", idempotencyKey);
        }

        return response;
    }

    @Transactional(readOnly = true)
    public ConversationDto getConversation(UUID conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
        return toDto(conversation);
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> getUserConversations(String userId) {
        return conversationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private Conversation resolveConversation(ChatRequest request) {
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            UUID convId = UUID.fromString(request.conversationId());
            return conversationRepository.findById(convId)
                    .orElseThrow(() -> new ConversationNotFoundException(convId));
        }
        log.info("Creating new conversation for userId={}", request.userId());
        return new Conversation(request.userId());
    }

    private ConversationDto toDto(Conversation conversation) {
        List<MessageDto> messages = conversation.getMessages().stream()
                .map(m -> new MessageDto(m.getId(), m.getRole().name(), m.getContent(), m.getTimestamp()))
                .toList();
        return new ConversationDto(conversation.getId(), conversation.getUserId(), conversation.getCreatedAt(), messages);
    }

    private String serializeResponse(ChatResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize response", e);
        }
    }

    private ChatResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, ChatResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize cached response", e);
        }
    }

    private String hashRequest(ChatRequest request) {
        String raw = request.userId() + "|" + request.conversationId() + "|" + request.message();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

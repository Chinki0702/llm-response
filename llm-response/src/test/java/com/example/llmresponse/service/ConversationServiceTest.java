package com.example.llmresponse.service;

import com.example.llmresponse.dto.ChatRequest;
import com.example.llmresponse.dto.ChatResponse;
import com.example.llmresponse.dto.ConversationDto;
import com.example.llmresponse.repository.ConversationRepository;
import com.example.llmresponse.repository.IdempotencyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ConversationServiceTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public LlmClient testLlmClient() {
            return prompt -> "Mocked AI response for testing";
        }
    }

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @BeforeEach
    void setUp() {
        idempotencyRepository.deleteAll();
        conversationRepository.deleteAll();
    }

    @Test
    void chat_createsNewConversationAndReturnsResponse() {
        ChatRequest request = new ChatRequest("user1", null, "Hello AI!");

        ChatResponse response = conversationService.chat(request, null);

        assertThat(response).isNotNull();
        assertThat(response.conversationId()).isNotNull();
        assertThat(response.reply()).isEqualTo("Mocked AI response for testing");
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void chat_continuesExistingConversation() {
        ChatRequest first = new ChatRequest("user1", null, "First message");
        ChatResponse firstResponse = conversationService.chat(first, null);

        ChatRequest second = new ChatRequest("user1", firstResponse.conversationId().toString(), "Second message");
        ChatResponse secondResponse = conversationService.chat(second, null);

        assertThat(secondResponse.conversationId()).isEqualTo(firstResponse.conversationId());

        ConversationDto conversation = conversationService.getConversation(firstResponse.conversationId());
        assertThat(conversation.messages()).hasSize(4); // 2 user + 2 assistant
    }

    @Test
    void chat_idempotency_returnsCachedResponseForDuplicateKey() {
        ChatRequest request = new ChatRequest("user1", null, "Hello!");
        String key = "unique-key-123";

        ChatResponse first = conversationService.chat(request, key);
        ChatResponse second = conversationService.chat(request, key);

        assertThat(second.conversationId()).isEqualTo(first.conversationId());
        assertThat(second.reply()).isEqualTo(first.reply());
        assertThat(second.messageId()).isEqualTo(first.messageId());
    }

    @Test
    void getConversation_throwsForUnknownId() {
        java.util.UUID randomId = java.util.UUID.randomUUID();
        assertThatThrownBy(() -> conversationService.getConversation(randomId))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void getUserConversations_returnsAllForUser() {
        conversationService.chat(new ChatRequest("userA", null, "msg1"), null);
        conversationService.chat(new ChatRequest("userA", null, "msg2"), null);
        conversationService.chat(new ChatRequest("userB", null, "msg3"), null);

        assertThat(conversationService.getUserConversations("userA")).hasSize(2);
        assertThat(conversationService.getUserConversations("userB")).hasSize(1);
        assertThat(conversationService.getUserConversations("userC")).isEmpty();
    }
}

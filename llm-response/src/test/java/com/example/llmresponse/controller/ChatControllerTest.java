package com.example.llmresponse.controller;

import com.example.llmresponse.dto.ChatRequest;
import com.example.llmresponse.service.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public LlmClient testLlmClient() {
            return prompt -> "Test AI response";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void postChat_returnsOkWithResponse() throws Exception {
        ChatRequest request = new ChatRequest("user1", null, "Hello!");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").isNotEmpty())
                .andExpect(jsonPath("$.reply").value("Test AI response"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void postChat_withIdempotencyKey_returnsSameResponse() throws Exception {
        ChatRequest request = new ChatRequest("user1", null, "Hello!");
        String body = objectMapper.writeValueAsString(request);

        String firstResponse = mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "test-key-001")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String secondResponse = mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "test-key-001")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Both responses should have the same conversation ID and message
        org.assertj.core.api.Assertions.assertThat(firstResponse).isEqualTo(secondResponse);
    }

    @Test
    void postChat_withBlankUserId_returnsBadRequest() throws Exception {
        ChatRequest request = new ChatRequest("", null, "Hello!");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postChat_withBlankMessage_returnsBadRequest() throws Exception {
        ChatRequest request = new ChatRequest("user1", null, "");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getConversation_returnsConversationWithHistory() throws Exception {
        // Create a conversation first
        ChatRequest request = new ChatRequest("user1", null, "Tell me a joke");
        String response = mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String conversationId = objectMapper.readTree(response).get("conversationId").asText();

        mockMvc.perform(get("/api/conversations/" + conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conversationId))
                .andExpect(jsonPath("$.userId").value("user1"))
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"));
    }

    @Test
    void getConversation_unknownId_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/conversations/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUserConversations_returnsList() throws Exception {
        // Create conversations
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("userX", null, "msg1"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/conversations")
                        .param("userId", "userX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }
}

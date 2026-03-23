package com.example.llmresponse.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockLlmClientTest {

    @Test
    void generate_returnsNonEmptyResponse() {
        MockLlmClient client = new MockLlmClient(5000L, 0.0);

        String response = client.generate("Hello, how are you?");

        assertThat(response).isNotNull().isNotBlank();
    }

    @Test
    void generate_returnsResponseContainingPromptReference() {
        MockLlmClient client = new MockLlmClient(5000L, 0.0);

        String response = client.generate("Exact same prompt");

        assertThat(response).contains("Exact same prompt");
    }
}

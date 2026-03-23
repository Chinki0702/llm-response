package com.example.llmresponse.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Wraps the LLM client with retry logic.
 */
@Service
public class ResilientLlmService {

    private static final Logger log = LoggerFactory.getLogger(ResilientLlmService.class);

    private final LlmClient llmClient;

    public ResilientLlmService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Retryable(
            retryFor = LlmException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public String generate(String prompt) {
        log.debug("Attempting LLM generation");
        return llmClient.generate(prompt);
    }

    @Recover
    public String recover(LlmException ex, String prompt) {
        log.error("All LLM retry attempts exhausted. Last error: {}", ex.getMessage());
        throw new LlmException("LLM service unavailable after retries: " + ex.getMessage(), ex);
    }
}

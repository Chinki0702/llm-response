package com.example.llmresponse.service;

/**
 * Abstraction for LLM API calls.
 */
public interface LlmClient {

    /**
     * Sends a prompt to the LLM and returns the generated text.
     *
     * @param prompt the user prompt
     * @return generated response text
     * @throws LlmException if the call fails
     */
    String generate(String prompt);
}

package com.example.llmresponse.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.*;

/**
 * Mocked LLM client that simulates AI responses.
 * Occasionally simulates failures and slow responses for testing reliability.
 */
@Component
public class MockLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(MockLlmClient.class);

    private static final List<String> PREFIXES = List.of(
            "That's an interesting question!",
            "Great point!",
            "Thank you for asking!",
            "Interesting thought!",
            "I appreciate your curiosity!"
    );

    private static final List<String> BODIES = List.of(
            "Based on my analysis, I'd suggest considering multiple perspectives before drawing a conclusion.",
            "The key factor to consider is the context in which this applies.",
            "The most relevant information I can provide is that this topic has several nuances worth exploring.",
            "The best approach would be to break this down into smaller, manageable steps.",
            "There are both advantages and disadvantages to consider here.",
            "I recommend looking at this from a practical standpoint first.",
            "The underlying principles suggest a methodical approach would be most effective.",
            "Let me highlight the most important aspects you should focus on."
    );

    private final long timeoutMs;
    private final double failureRate;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public MockLlmClient(
            @Value("${llm.timeout-ms:5000}") long timeoutMs,
            @Value("${llm.mock.failure-rate:0.0}") double failureRate) {
        this.timeoutMs = timeoutMs;
        this.failureRate = failureRate;
    }

    @Override
    public String generate(String prompt) {
        log.info("Calling mocked LLM API with prompt length={}", prompt.length());

        Future<String> future = executor.submit(() -> simulateLlmCall(prompt));

        try {
            String response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            log.info("LLM response received successfully");
            return response;
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("LLM call timed out after {}ms", timeoutMs);
            throw new LlmException("LLM call timed out after " + timeoutMs + "ms", e);
        } catch (ExecutionException e) {
            log.error("LLM call failed: {}", e.getCause().getMessage());
            throw new LlmException("LLM call failed: " + e.getCause().getMessage(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM call interrupted", e);
        }
    }

    private String simulateLlmCall(String prompt) {
        // Simulate some processing latency (200-800ms)
        try {
            Thread.sleep(200 + ThreadLocalRandom.current().nextLong(600));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted during processing", e);
        }

        // Simulate random failures based on configured rate
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new LlmException("Simulated LLM API failure");
        }

        // Build a varied response using the prompt content and randomness
        String prefix = PREFIXES.get(ThreadLocalRandom.current().nextInt(PREFIXES.size()));
        String body = BODIES.get(ThreadLocalRandom.current().nextInt(BODIES.size()));

        // Extract a short topic reference from the prompt
        String topic = prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt;
        return prefix + " Regarding \"" + topic + "\": " + body;
    }
}

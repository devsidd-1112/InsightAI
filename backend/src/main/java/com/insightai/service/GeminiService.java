package com.insightai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insightai.dto.*;
import com.insightai.exception.GeminiApiException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GeminiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PROMPT_TEMPLATE = """
            You are an AI assistant analyzing meeting transcripts.

            Analyze the following meeting transcript and extract the summary, key decisions, and action items.
            Return the result strictly as a JSON object.

            Transcript:
            %s
            """;

    public GeminiParsedResponse generateSummary(String transcript) {
        logger.info("Generating summary for transcript of length: {}", transcript.length());

        try {
            // Build the prompt
            String prompt = String.format(PROMPT_TEMPLATE, transcript);

            // Define the JSON schema for structured output
            GeminiRequest.Schema actionItemSchema = GeminiRequest.Schema.builder()
                    .type("OBJECT")
                    .properties(java.util.Map.of(
                            "action",
                            GeminiRequest.Schema.builder().type("STRING").description("Action description").build(),
                            "owner",
                            GeminiRequest.Schema.builder().type("STRING")
                                    .description("Name if mentioned, else \"Unassigned\"").build(),
                            "deadline",
                            GeminiRequest.Schema.builder().type("STRING")
                                    .description("Date if mentioned, else \"Not specified\"").build()))
                    .required(java.util.Arrays.asList("action", "owner", "deadline"))
                    .build();

            GeminiRequest.Schema rootSchema = GeminiRequest.Schema.builder()
                    .type("OBJECT")
                    .properties(java.util.Map.of(
                            "summary",
                            GeminiRequest.Schema.builder().type("STRING")
                                    .description("A concise 2-3 sentence summary of the meeting").build(),
                            "keyDecisions",
                            GeminiRequest.Schema.builder().type("ARRAY")
                                    .items(GeminiRequest.Schema.builder().type("STRING").build())
                                    .description("Bullet points of important decisions made").build(),
                            "actionItems",
                            GeminiRequest.Schema.builder().type("ARRAY").items(actionItemSchema)
                                    .description("List of action items").build()))
                    .required(java.util.Arrays.asList("summary", "keyDecisions", "actionItems"))
                    .build();

            // Build the request
            GeminiRequest request = GeminiRequest.builder()
                    .contents(Collections.singletonList(
                            GeminiRequest.Content.builder()
                                    .parts(Collections.singletonList(
                                            GeminiRequest.Part.builder()
                                                    .text(prompt)
                                                    .build()))
                                    .build()))
                    .generationConfig(GeminiRequest.GenerationConfig.builder()
                            .temperature(0.4)
                            .maxOutputTokens(8192)
                            .responseMimeType("application/json")
                            .responseSchema(rootSchema)
                            .build())
                    .build();

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<GeminiRequest> entity = new HttpEntity<>(request, headers);

            // Add API key as query parameter
            String urlWithKey = apiUrl + "?key=" + apiKey;

            // Call Gemini API
            logger.debug("Calling Gemini API at: {}", urlWithKey);
            ResponseEntity<GeminiResponse> response = restTemplate.exchange(
                    urlWithKey,
                    HttpMethod.POST,
                    entity,
                    GeminiResponse.class);

            if (response.getBody() == null ||
                    response.getBody().getCandidates() == null ||
                    response.getBody().getCandidates().isEmpty()) {
                throw new GeminiApiException("Empty response from Gemini API");
            }

            // Extract text from response
            String responseText = response.getBody()
                    .getCandidates()
                    .get(0)
                    .getContent()
                    .getParts()
                    .get(0)
                    .getText();

            logger.debug("Received response from Gemini API");

            // Parse the JSON response
            return parseGeminiResponse(responseText);

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                logger.error("Invalid Gemini API key");
                throw new GeminiApiException("Invalid API key");
            } else if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                logger.error("Gemini API quota exceeded");
                throw new GeminiApiException("API quota exceeded. Please try again later.");
            } else {
                logger.error("Gemini API client error: {}", e.getMessage());
                throw new GeminiApiException("AI service error: " + e.getMessage());
            }
        } catch (HttpServerErrorException e) {
            logger.error("Gemini API server error: {}", e.getMessage());
            throw new GeminiApiException("AI service is temporarily unavailable");
        } catch (Exception e) {
            logger.error("Unexpected error calling Gemini API", e);
            throw new GeminiApiException("Failed to generate summary: " + e.getMessage(), e);
        }
    }

    private GeminiParsedResponse parseGeminiResponse(String responseText) {
        try {
            // Try to extract JSON from the response (it might be wrapped in markdown code
            // blocks)
            // Try to extract JSON from the response (it might be wrapped in markdown code
            // blocks)
            String jsonText = responseText.trim();

            // Strip markdown formatting if present
            if (jsonText.startsWith("```json")) {
                jsonText = jsonText.substring(7);
            } else if (jsonText.startsWith("```")) {
                jsonText = jsonText.substring(3);
            }
            if (jsonText.endsWith("```")) {
                jsonText = jsonText.substring(0, jsonText.length() - 3);
            }
            jsonText = jsonText.trim();

            // Find the opening brace and closing brace to extract just the JSON object
            int startIndex = jsonText.indexOf('{');
            int endIndex = jsonText.lastIndexOf('}');

            if (startIndex >= 0 && endIndex > startIndex) {
                jsonText = jsonText.substring(startIndex, endIndex + 1);
            } else if (startIndex >= 0 && endIndex <= startIndex) {
                logger.warn("Truncated JSON response from Gemini API (missing closing brace)");
                throw new JsonProcessingException(
                        "Truncated JSON response. The model may have reached its output token limit.") {
                };
            } else {
                throw new JsonProcessingException("No JSON object found in response") {
                };
            }

            // Parse JSON
            GeminiParsedResponse parsed = objectMapper.readValue(jsonText, GeminiParsedResponse.class);

            // Ensure non-null lists
            if (parsed.getKeyDecisions() == null) {
                parsed.setKeyDecisions(new ArrayList<>());
            }
            if (parsed.getActionItems() == null) {
                parsed.setActionItems(new ArrayList<>());
            }

            logger.info("Successfully parsed Gemini response: {} action items",
                    parsed.getActionItems().size());

            return parsed;

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse Gemini response as JSON: {}", responseText, e);

            // Fallback: create a basic response with the raw text as summary and the error
            // string for debugging
            GeminiParsedResponse fallback = new GeminiParsedResponse();
            fallback.setSummary("DEBUG - JSON Parse Error: " + e.getMessage() + "\n\nRaw Response:\n" + responseText);
            fallback.setKeyDecisions(new ArrayList<>());
            fallback.setActionItems(new ArrayList<>());

            return fallback;
        }
    }
}

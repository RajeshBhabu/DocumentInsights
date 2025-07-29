package com.documentinsights.service;

import com.documentinsights.model.Document;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AIService {

    @Value("${ai.provider:demo}")
    private String aiProvider;

    // OpenAI Configuration
    @Value("${openai.api-key:}")
    private String openaiApiKey;
    
    @Value("${openai.model:gpt-4o-mini}")
    private String openaiModel;
    
    @Value("${openai.max-tokens:2000}")
    private int maxTokens;
    
    @Value("${openai.temperature:0.7}")
    private double temperature;

    // Azure OpenAI Configuration
    @Value("${azure.openai.endpoint:}")
    private String azureEndpoint;
    
    @Value("${azure.openai.api-key:}")
    private String azureApiKey;
    
    @Value("${azure.openai.deployment-name:gpt-35-turbo}")
    private String azureDeploymentName;
    
    @Value("${azure.openai.api-version:2024-02-15-preview}")
    private String azureApiVersion;

    // Google Gemini Configuration
    @Value("${google.gemini.api-key:}")
    private String geminiApiKey;
    
    @Value("${google.gemini.model:gemini-pro}")
    private String geminiModel;

    // Anthropic Claude Configuration
    @Value("${anthropic.api-key:}")
    private String anthropicApiKey;
    
    @Value("${anthropic.model:claude-3-sonnet-20240229}")
    private String anthropicModel;

    // Ollama Configuration
    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;
    
    @Value("${ollama.model:llama2}")
    private String ollamaModel;
    
    private static final int MAX_DOCUMENT_LENGTH = 10000;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Safely convert HttpEntity to String, handling ParseException
     */
    private String entityToString(HttpEntity entity) throws IOException {
        try {
            return EntityUtils.toString(entity);
        } catch (ParseException e) {
            throw new IOException("Failed to parse response entity", e);
        }
    }

    /**
     * Generate insights from documents based on user query
     */
    @Cacheable(value = "insights", key = "#query + '_' + T(java.util.Objects).hash(#documents)")
    public String generateInsights(String query, List<Document> documents) throws IOException {
        log.info("Generating AI insights using provider: {} for query: {}", 
                aiProvider, query.substring(0, Math.min(query.length(), 100)));
        
        try {
            String context = prepareDocumentContext(documents);
            String prompt = createPrompt(query, context, documents);
            
            return switch (aiProvider.toLowerCase()) {
                case "openai" -> callOpenAI(prompt);
                case "azure" -> callAzureOpenAI(prompt);
                case "google" -> callGoogleGemini(prompt);
                case "anthropic" -> callAnthropic(prompt);
                case "ollama" -> callOllama(prompt);
                case "demo" -> generateDemoResponse(query, documents);
                default -> throw new IOException("Unsupported AI provider: " + aiProvider);
            };
        } catch (Exception e) {
            log.error("Error generating AI insights with provider: {}", aiProvider, e);
            throw new IOException("Failed to generate insights: " + e.getMessage(), e);
        }
    }

    /**
     * Generate demo response without requiring API keys
     */
    private String generateDemoResponse(String query, List<Document> documents) {
        String documentList = documents.stream()
                .map(doc -> "• " + doc.getOriginalName() + " (" + doc.getType() + ")")
                .collect(Collectors.joining("\n"));

        return String.format("""
                **Demo Mode Response from Backend Rajesh**
                
                Thank you for your question: "%s"
                
                I would analyze the following documents to provide insights:
                %s
                
                **Sample Analysis:**
                Based on the %d document%s provided, here are some key points I would typically identify:
                
                • **Document Summary**: I would extract the main themes and topics from each document
                • **Key Insights**: Important findings, recommendations, or action items would be highlighted
                • **Cross-Document Analysis**: Connections and patterns across multiple documents would be identified
                • **Actionable Items**: Specific next steps or recommendations would be provided
                
                **Note**: This is a demo response. To get actual AI-powered insights, please configure one of the supported AI providers:
                - Azure OpenAI (recommended for corporate environments)
                - Google Gemini API
                - Anthropic Claude API
                - Local AI with Ollama
                
                Check the README.md for setup instructions.
                """, 
                query, 
                documentList, 
                documents.size(), 
                documents.size() == 1 ? "" : "s");
    }

    /**
     * Call OpenAI API
     */
    private String callOpenAI(String prompt) throws IOException {
        if (openaiApiKey == null || openaiApiKey.trim().isEmpty()) {
            throw new IOException("OpenAI API key is not configured");
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost request = new HttpPost("https://api.openai.com/v1/chat/completions");
            
            request.setHeader("Authorization", "Bearer " + openaiApiKey);
            request.setHeader("Content-Type", "application/json");
            
            Map<String, Object> requestBody = createOpenAIRequestBody(prompt);
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            request.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));
            
            return executeHttpRequest(httpClient, request);
        }
    }

    /**
     * Call Azure OpenAI API
     */
    private String callAzureOpenAI(String prompt) throws IOException {
        if (azureEndpoint == null || azureEndpoint.trim().isEmpty() || 
            azureApiKey == null || azureApiKey.trim().isEmpty()) {
            throw new IOException("Azure OpenAI configuration is not complete");
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String url = String.format("%s/openai/deployments/%s/chat/completions?api-version=%s",
                    azureEndpoint, azureDeploymentName, azureApiVersion);
            
            HttpPost request = new HttpPost(url);
            request.setHeader("api-key", azureApiKey);
            request.setHeader("Content-Type", "application/json");
            
            Map<String, Object> requestBody = createOpenAIRequestBody(prompt);
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            request.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));
            
            return executeHttpRequest(httpClient, request);
        }
    }

    /**
     * Call Google Gemini API
     */
    private String callGoogleGemini(String prompt) throws IOException {
        if (geminiApiKey == null || geminiApiKey.trim().isEmpty()) {
            throw new IOException("Google Gemini API key is not configured");
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                    geminiModel, geminiApiKey);
            
            HttpPost request = new HttpPost(url);
            request.setHeader("Content-Type", "application/json");
            
            Map<String, Object> requestBody = createGeminiRequestBody(prompt);
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            request.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));
            
            ClassicHttpResponse response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();
            
            if (response.getCode() >= 200 && response.getCode() < 300) {
                String responseBody = entityToString(entity);
                return parseGeminiResponse(responseBody);
            } else {
                String errorBody = entity != null ? entityToString(entity) : "No response body";
                throw new IOException("Gemini API request failed with status " + 
                    response.getCode() + ": " + errorBody);
            }
        }
    }

    /**
     * Call Anthropic Claude API
     */
    private String callAnthropic(String prompt) throws IOException {
        if (anthropicApiKey == null || anthropicApiKey.trim().isEmpty()) {
            throw new IOException("Anthropic API key is not configured");
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost request = new HttpPost("https://api.anthropic.com/v1/messages");
            
            request.setHeader("x-api-key", anthropicApiKey);
            request.setHeader("Content-Type", "application/json");
            request.setHeader("anthropic-version", "2023-06-01");
            
            Map<String, Object> requestBody = createAnthropicRequestBody(prompt);
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            request.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));
            
            ClassicHttpResponse response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();
            
            if (response.getCode() >= 200 && response.getCode() < 300) {
                String responseBody = entityToString(entity);
                return parseAnthropicResponse(responseBody);
            } else {
                String errorBody = entity != null ? entityToString(entity) : "No response body";
                throw new IOException("Anthropic API request failed with status " + 
                    response.getCode() + ": " + errorBody);
            }
        }
    }

    /**
     * Call local Ollama API
     */
    private String callOllama(String prompt) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(ollamaBaseUrl + "/api/generate");
            request.setHeader("Content-Type", "application/json");
            
            Map<String, Object> requestBody = createOllamaRequestBody(prompt);
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            request.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));
            
            ClassicHttpResponse response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();
            
            if (response.getCode() >= 200 && response.getCode() < 300) {
                String responseBody = entityToString(entity);
                return parseOllamaResponse(responseBody);
            } else {
                String errorBody = entity != null ? entityToString(entity) : "No response body";
                throw new IOException("Ollama API request failed. Is Ollama running? Status " + 
                    response.getCode() + ": " + errorBody);
            }
        }
    }

    // Request body creators
    private Map<String, Object> createOpenAIRequestBody(String prompt) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", openaiModel);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", temperature);
        
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", getSystemPrompt());
        
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        
        requestBody.put("messages", List.of(systemMessage, userMessage));
        return requestBody;
    }

    private Map<String, Object> createGeminiRequestBody(String prompt) {
        Map<String, Object> content = new HashMap<>();
        Map<String, Object> part = new HashMap<>();
        part.put("text", getSystemPrompt() + "\n\n" + prompt);
        content.put("parts", List.of(part));
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(content));
        
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", temperature);
        generationConfig.put("maxOutputTokens", maxTokens);
        requestBody.put("generationConfig", generationConfig);
        
        return requestBody;
    }

    private Map<String, Object> createAnthropicRequestBody(String prompt) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", anthropicModel);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", temperature);
        requestBody.put("system", getSystemPrompt());
        
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        requestBody.put("messages", List.of(message));
        
        return requestBody;
    }

    private Map<String, Object> createOllamaRequestBody(String prompt) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", ollamaModel);
        requestBody.put("prompt", getSystemPrompt() + "\n\n" + prompt);
        requestBody.put("stream", false);
        
        Map<String, Object> options = new HashMap<>();
        options.put("temperature", temperature);
        options.put("num_predict", maxTokens);
        requestBody.put("options", options);
        
        return requestBody;
    }

    // Response parsers
    private String executeHttpRequest(CloseableHttpClient httpClient, HttpPost request) throws IOException {
        ClassicHttpResponse response = httpClient.execute(request);
        HttpEntity entity = response.getEntity();
        
        if (response.getCode() >= 200 && response.getCode() < 300) {
            String responseBody = entityToString(entity);
            return parseOpenAIResponse(responseBody);
        } else {
            String errorBody = entity != null ? entityToString(entity) : "No response body";
            throw new IOException("API request failed with status " + 
                response.getCode() + ": " + errorBody);
        }
    }

    private String parseOpenAIResponse(String responseBody) throws IOException {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            
            if (choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode message = firstChoice.path("message");
                String content = message.path("content").asText();
                
                if (content.isEmpty()) {
                    throw new IOException("No response generated from AI service");
                }
                
                return content;
            } else {
                throw new IOException("Invalid response format from API");
            }
        } catch (Exception e) {
            log.error("Error parsing API response", e);
            throw new IOException("Failed to parse API response: " + e.getMessage(), e);
        }
    }

    private String parseGeminiResponse(String responseBody) throws IOException {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode firstCandidate = candidates.get(0);
                JsonNode content = firstCandidate.path("content");
                JsonNode parts = content.path("parts");
                
                if (parts.isArray() && parts.size() > 0) {
                    String text = parts.get(0).path("text").asText();
                    if (text.isEmpty()) {
                        throw new IOException("No response generated from Gemini");
                    }
                    return text;
                }
            }
            throw new IOException("Invalid response format from Gemini API");
        } catch (Exception e) {
            log.error("Error parsing Gemini response", e);
            throw new IOException("Failed to parse Gemini response: " + e.getMessage(), e);
        }
    }

    private String parseAnthropicResponse(String responseBody) throws IOException {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("content");
            
            if (content.isArray() && content.size() > 0) {
                String text = content.get(0).path("text").asText();
                if (text.isEmpty()) {
                    throw new IOException("No response generated from Claude");
                }
                return text;
            }
            throw new IOException("Invalid response format from Anthropic API");
        } catch (Exception e) {
            log.error("Error parsing Anthropic response", e);
            throw new IOException("Failed to parse Anthropic response: " + e.getMessage(), e);
        }
    }

    private String parseOllamaResponse(String responseBody) throws IOException {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String response = root.path("response").asText();
            
            if (response.isEmpty()) {
                throw new IOException("No response generated from Ollama");
            }
            return response;
        } catch (Exception e) {
            log.error("Error parsing Ollama response", e);
            throw new IOException("Failed to parse Ollama response: " + e.getMessage(), e);
        }
    }

    // Existing helper methods (unchanged)
    private String prepareDocumentContext(List<Document> documents) {
        StringBuilder context = new StringBuilder();
        
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            context.append("=== DOCUMENT ").append(i + 1).append(": ")
                   .append(doc.getOriginalName()).append(" ===\n");
            
            String content = doc.getContent();
            if (content.length() > MAX_DOCUMENT_LENGTH) {
                content = content.substring(0, MAX_DOCUMENT_LENGTH) + "... [truncated]";
            }
            
            context.append(content).append("\n\n");
        }
        
        return context.toString();
    }

    private String createPrompt(String query, String context, List<Document> documents) {
        String documentList = documents.stream()
                .map(doc -> String.format("- \"%s\" (%s)", doc.getOriginalName(), doc.getType()))
                .collect(Collectors.joining("\n"));

        return String.format("""
                USER QUERY: %s

                AVAILABLE DOCUMENTS:
                %s

                DOCUMENT CONTENT:
                %s

                Please analyze the above documents and provide comprehensive insights to answer the user's query. 
                Structure your response clearly and cite specific documents when relevant.
                """, query, documentList, context);
    }

    private String getSystemPrompt() {
        return """
                You are an expert document analyst and insights generator. Your role is to analyze documents 
                and provide comprehensive, accurate, and helpful insights based on user queries.

                Key responsibilities:
                1. Analyze the provided documents thoroughly
                2. Answer questions based ONLY on the content available in the documents
                3. Provide detailed, well-structured responses
                4. If information is not available in the documents, clearly state this
                5. Cite specific documents when relevant
                6. Summarize key findings and provide actionable insights
                7. Maintain a professional and helpful tone

                Guidelines:
                - Be thorough but concise
                - Use bullet points and structure for clarity
                - Quote relevant sections when helpful
                - Highlight important findings
                - Suggest follow-up questions or areas for further investigation
                - If multiple documents contain relevant information, synthesize insights across them
                """;
    }

    // Simplified document summarization using the configured provider
    @Cacheable(value = "documentSummaries", key = "#document.id")
    public String summarizeDocument(Document document) throws IOException {
        if ("demo".equals(aiProvider)) {
            return String.format("**Demo Summary for: %s**\n\nThis document contains %d characters of content. " +
                    "In a real implementation, this would be an AI-generated summary highlighting the key points, " +
                    "main themes, and important findings from the document.",
                    document.getOriginalName(), document.getContent().length());
        }
        
        return generateInsights("Please provide a concise summary of this document", List.of(document));
    }

    @Cacheable(value = "keyTopics", key = "T(java.util.Objects).hash(#documents)")
    public List<String> extractKeyTopics(List<Document> documents) throws IOException {
        if ("demo".equals(aiProvider)) {
            return List.of("Demo Topic 1", "Demo Topic 2", "Demo Topic 3", "Demo Topic 4", "Demo Topic 5");
        }
        
        // For other providers, this would need specific implementation
        return List.of("Key topics extraction not implemented for " + aiProvider);
    }
} 
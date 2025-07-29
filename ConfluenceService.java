package com.documentinsights.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ConfluenceService {

    @Value("${confluence.timeout:30000}")
    private int timeout;
    
    @Value("${confluence.default-email:}")
    private String defaultEmail;
    
    @Value("${confluence.default-token:}")
    private String defaultToken;
    
    @Value("${confluence.base-url:}")
    private String defaultBaseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Pattern to extract page ID from Confluence URL
    private static final Pattern PAGE_ID_PATTERN = Pattern.compile("pages/(\\d+)");
    private static final Pattern VIEWPAGE_PATTERN = Pattern.compile("viewpage\\.action\\?pageId=(\\d+)");

    /**
     * Fetch content from Confluence page
     * @param url Confluence page URL
     * @param token Confluence API token (optional)
     * @param email Confluence user email (optional)
     * @return ConfluenceContent with title and content
     * @throws IOException if fetching fails
     */
    public ConfluenceContent fetchConfluenceContent(String url, String token, String email) throws IOException {
        log.info("Fetching Confluence content from URL: {}", url);
        
        try {
            String pageId = extractPageId(url);
            String baseUrl = extractBaseUrl(url);
            String apiUrl = buildApiUrl(baseUrl, pageId);
            
            // Use provided credentials or fall back to defaults
            String authEmail = (email != null && !email.trim().isEmpty()) ? email : defaultEmail;
            String authToken = (token != null && !token.trim().isEmpty()) ? token : defaultToken;
            
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpGet request = new HttpGet(apiUrl);
                
                // Add authentication if available
                if (authEmail != null && !authEmail.trim().isEmpty() && 
                    authToken != null && !authToken.trim().isEmpty()) {
                    String auth = authEmail + ":" + authToken;
                    String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                    request.setHeader("Authorization", "Basic " + encodedAuth);
                    log.debug("Using authentication for Confluence API request");
                } else {
                    log.warn("No Confluence credentials provided - attempting unauthenticated request");
                }
                
                request.setHeader("Accept", "application/json");
                request.setHeader("Content-Type", "application/json");
                
                ClassicHttpResponse response = httpClient.execute(request);
                HttpEntity entity = response.getEntity();
                
                if (response.getCode() >= 200 && response.getCode() < 300) {
                    String responseBody = EntityUtils.toString(entity);
                    return parseConfluenceResponse(responseBody);
                } else {
                    String errorBody = entity != null ? EntityUtils.toString(entity) : "No response body";
                    throw new IOException("Confluence API request failed with status " + 
                        response.getCode() + ": " + errorBody);
                }
            }
        } catch (Exception e) {
            log.error("Error fetching Confluence content from URL: {}", url, e);
            throw new IOException("Failed to fetch Confluence content: " + e.getMessage(), e);
        }
    }

    /**
     * Extract page ID from Confluence URL
     */
    private String extractPageId(String url) throws IOException {
        // Try different URL patterns
        Matcher matcher = PAGE_ID_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        matcher = VIEWPAGE_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // If no pattern matches, try to extract from query parameters
        if (url.contains("pageId=")) {
            String[] parts = url.split("pageId=");
            if (parts.length > 1) {
                String idPart = parts[1].split("&")[0];
                if (idPart.matches("\\d+")) {
                    return idPart;
                }
            }
        }
        
        throw new IOException("Unable to extract page ID from Confluence URL: " + url);
    }

    /**
     * Extract base URL from Confluence URL
     */
    private String extractBaseUrl(String url) throws IOException {
        try {
            // Remove protocol
            String withoutProtocol = url.replaceFirst("^https?://", "");
            
            // Find the domain part
            int slashIndex = withoutProtocol.indexOf('/');
            if (slashIndex == -1) {
                throw new IOException("Invalid Confluence URL format");
            }
            
            String domain = withoutProtocol.substring(0, slashIndex);
            
            // Reconstruct base URL
            String protocol = url.startsWith("https://") ? "https://" : "http://";
            return protocol + domain;
        } catch (Exception e) {
            throw new IOException("Unable to extract base URL from: " + url, e);
        }
    }

    /**
     * Build Confluence REST API URL
     */
    private String buildApiUrl(String baseUrl, String pageId) {
        return String.format("%s/wiki/rest/api/content/%s?expand=body.storage,title", baseUrl, pageId);
    }

    /**
     * Parse Confluence API response
     */
    private ConfluenceContent parseConfluenceResponse(String responseBody) throws IOException {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            
            String title = root.path("title").asText("Untitled");
            String content = "";
            
            // Extract content from storage format
            JsonNode body = root.path("body").path("storage");
            if (!body.isMissingNode()) {
                String storageContent = body.path("value").asText("");
                content = cleanHtmlContent(storageContent);
            }
            
            if (content.trim().isEmpty()) {
                throw new IOException("No content found in Confluence page");
            }
            
            return ConfluenceContent.builder()
                    .title(title)
                    .content(content)
                    .build();
        } catch (Exception e) {
            log.error("Error parsing Confluence response", e);
            throw new IOException("Failed to parse Confluence response: " + e.getMessage(), e);
        }
    }

    /**
     * Clean HTML content from Confluence storage format
     */
    private String cleanHtmlContent(String htmlContent) {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            return "";
        }
        
        // Remove HTML tags and decode entities
        String cleaned = htmlContent
                // Remove HTML tags but keep content
                .replaceAll("<[^>]+>", " ")
                // Decode common HTML entities
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                // Clean up whitespace
                .replaceAll("\\s+", " ")
                .trim();
        
        return cleaned;
    }

    /**
     * Confluence content holder
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ConfluenceContent {
        private String title;
        private String content;
    }
} 
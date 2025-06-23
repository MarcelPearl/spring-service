package com.marcella.backend.nodeHandlers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marcella.backend.services.WorkflowEventProducer;
import com.marcella.backend.utils.TemplateUtils;
import com.marcella.backend.workflow.NodeCompletionMessage;
import com.marcella.backend.workflow.NodeExecutionMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class HttpRequestNodeHandler implements NodeHandler {

    private final WorkflowEventProducer eventProducer;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean canHandle(String nodeType) {
        return "httpRequest".equalsIgnoreCase(nodeType);
    }

    @Override
    public Map<String, Object> execute(NodeExecutionMessage message) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> output = new HashMap<>();

        try {
            Map<String, Object> data = message.getNodeData();
            Map<String, Object> context = message.getContext();
            log.info("🔍 Context keys before auth handling: {}", context.keySet());
            log.info("🔍 googleAccessToken in context: {}", context.get("googleAccessToken"));

            // Process URL with template substitution
            String rawUrl = (String) data.get("url");
            if (rawUrl == null || rawUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("URL is required for HTTP request");
            }
            String processedUrl = TemplateUtils.substitute(rawUrl, context);

            // Process method
            String method = (String) data.getOrDefault("method", "GET");
            method = TemplateUtils.substitute(method, context).toUpperCase();

            // Process headers - handle both Map and stringified JSON
            Map<String, String> processedHeaders = processHeaders(data.get("headers"), context);

            // Process body - handle both Map and stringified JSON
            Object processedBody = processBody(data.get("body"), context);

            // Build final URL with query parameters if any
            URI finalUri = buildFinalUri(processedUrl, context);

            log.info("Preparing HTTP request to: {}", finalUri);
            log.info("Method: {}, Headers: {}", method, processedHeaders.keySet());
            if (processedBody != null) {
                log.info("Body type: {}", processedBody.getClass().getSimpleName());
            }

            // Build HTTP headers
            HttpHeaders httpHeaders = new HttpHeaders();

            // Add custom headers first
            if (processedHeaders != null && !processedHeaders.isEmpty()) {
                processedHeaders.forEach(httpHeaders::add);
            }

            // Handle authentication tokens based on URL
            handleAuthentication(finalUri.toString(), context, httpHeaders, data);

            // Set content type if not already set and we have a body
            if (processedBody != null && !httpHeaders.containsKey("Content-Type")) {
                if (processedBody instanceof String) {
                    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                } else {
                    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                }
            }

            // Create HTTP entity
            HttpEntity<?> entity = new HttpEntity<>(processedBody, httpHeaders);

            // Execute HTTP request
            ResponseEntity<String> response = restTemplate.exchange(
                    finalUri,
                    HttpMethod.valueOf(method),
                    entity,
                    String.class
            );

            // Process response
            processResponse(response, output, context);

            long processingTime = System.currentTimeMillis() - startTime;
            publishCompletionEvent(message, output, "COMPLETED", processingTime);
            return output;

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("HTTP Request Node Error for node: {}", message.getNodeId(), e);

            Map<String, Object> errorOutput = new HashMap<>();
            if (message.getContext() != null) {
                errorOutput.putAll(message.getContext());
            }
            errorOutput.put("error", e.getMessage());
            errorOutput.put("http_request_failed", true);
            errorOutput.put("failed_at", Instant.now().toString());
            errorOutput.put("node_type", "httpRequest");

            publishCompletionEvent(message, errorOutput, "FAILED", processingTime);
            throw new RuntimeException("HTTP Request Node failed: " + e.getMessage(), e);
        }
    }

    private Map<String, String> processHeaders(Object headersObj, Map<String, Object> context) {
        Map<String, String> processedHeaders = new HashMap<>();

        if (headersObj == null) {
            return processedHeaders;
        }

        try {
            Map<String, Object> headersMap;

            if (headersObj instanceof String) {
                // Parse stringified JSON
                String headersJson = TemplateUtils.substitute((String) headersObj, context);
                if (headersJson.trim().isEmpty() || headersJson.equals("{}")) {
                    return processedHeaders;
                }
                headersMap = objectMapper.readValue(headersJson, new TypeReference<Map<String, Object>>() {});
            } else if (headersObj instanceof Map) {
                headersMap = (Map<String, Object>) headersObj;
            } else {
                log.warn("Headers object is neither String nor Map, ignoring");
                return processedHeaders;
            }

            // Process each header with template substitution
            for (Map.Entry<String, Object> entry : headersMap.entrySet()) {
                String key = TemplateUtils.substitute(entry.getKey(), context);
                String value = TemplateUtils.substitute(String.valueOf(entry.getValue()), context);
                processedHeaders.put(key, value);
            }

        } catch (Exception e) {
            log.error("Failed to process headers: {}", e.getMessage());
            throw new RuntimeException("Invalid headers format: " + e.getMessage());
        }

        return processedHeaders;
    }

    private Object processBody(Object bodyObj, Map<String, Object> context) {
        if (bodyObj == null) {
            return null;
        }

        try {
            if (bodyObj instanceof String) {
                String bodyStr = (String) bodyObj;
                bodyStr = TemplateUtils.substitute(bodyStr, context);

                if (bodyStr.trim().isEmpty()) {
                    return null;
                }

                // Try to parse as JSON, if it fails, return as string
                try {
                    return objectMapper.readValue(bodyStr, Object.class);
                } catch (Exception e) {
                    // If JSON parsing fails, return as string (might be plain text, XML, etc.)
                    return bodyStr;
                }
            } else if (bodyObj instanceof Map) {
                // Process Map body with template substitution
                Map<String, Object> bodyMap = (Map<String, Object>) bodyObj;
                Map<String, Object> processedBody = new HashMap<>();

                for (Map.Entry<String, Object> entry : bodyMap.entrySet()) {
                    String key = TemplateUtils.substitute(entry.getKey(), context);
                    Object value = entry.getValue();

                    if (value instanceof String) {
                        value = TemplateUtils.substitute((String) value, context);
                    }

                    processedBody.put(key, value);
                }

                return processedBody;
            } else {
                return bodyObj;
            }
        } catch (Exception e) {
            log.error("Failed to process body: {}", e.getMessage());
            throw new RuntimeException("Invalid body format: " + e.getMessage());
        }
    }

    private URI buildFinalUri(String baseUrl, Map<String, Object> context) {
        try {
            // Parse the URL to handle existing query parameters
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl);

            // Apply template substitution to any existing query parameters
            // This handles cases where query params might contain template variables
            URI tempUri = builder.build().toUri();
            String finalUrl = tempUri.toString();

            // Apply final template substitution to the complete URL
            finalUrl = TemplateUtils.substitute(finalUrl, context);

            return URI.create(finalUrl);
        } catch (Exception e) {
            log.error("Failed to build URI from: {}", baseUrl, e);
            throw new RuntimeException("Invalid URL format: " + baseUrl);
        }
    }

    private void handleAuthentication(
            String url,
            Map<String, Object> context,
            HttpHeaders headers,
            Map<String, Object> nodeData
    ) {
        if (context == null) {
            log.warn("⚠️ Context is null during authentication handling");
            return;
        }

        // === GOOGLE AUTH ===
        if (url.contains("googleapis.com") || url.contains("google.com/api") || Boolean.TRUE.equals(nodeData.get("useGoogleAuth"))) {
            String googleToken = null;

            // (1) From context
            if (context.containsKey("googleAccessToken")) {
                Object tokenObj = context.get("googleAccessToken");
                if (tokenObj instanceof String tokenStr) {
                    googleToken = tokenStr;
                    log.info("✅ Google Access Token found in context");
                }
            }

            // (3) From manually supplied headers (X-Google-Access-Token or Authorization)
            if ((googleToken == null || googleToken.isBlank()) && headers.containsKey("X-Google-Access-Token")) {
                googleToken = headers.getFirst("X-Google-Access-Token");
                log.info("🧪 Found Google token in X-Google-Access-Token header");
            }

            // Apply token
            if (googleToken == null || googleToken.isBlank()) {
                log.warn("❌ No Google access token available for request to: {}", url);
            } else {
                headers.setBearerAuth(googleToken);
                log.info("✅ Applied Google token to Authorization header for request to: {}", url);
            }
            return;
        }

        // === GITHUB AUTH ===
        if (url.contains("api.github.com") || url.contains("github.com/api") || Boolean.TRUE.equals(nodeData.get("useGithubAuth"))) {
            String githubToken = (String) context.get("githubAccessToken");
            if (githubToken != null && !githubToken.isBlank()) {
                headers.setBearerAuth(githubToken);
                log.info("✅ Added GitHub Access Token for request to: {}", url);
                return;
            }

            // Fallback
            String genericGithubToken = (String) context.get("github_token");
            if (genericGithubToken != null && !genericGithubToken.isBlank()) {
                headers.setBearerAuth(genericGithubToken);
                log.info("✅ Added fallback GitHub Token for request to: {}", url);
                return;
            }
        }

        // === GENERIC API KEY ===
        String apiKey = (String) context.get("api_key");
        if (apiKey != null && !apiKey.isBlank()) {
            if (Boolean.TRUE.equals(nodeData.get("useApiKeyAsBearer"))) {
                headers.setBearerAuth(apiKey);
                log.info("✅ Added API key as Bearer token");
            } else {
                headers.add("X-API-Key", apiKey);
                log.info("✅ Added API key as X-API-Key header");
            }
        }

        // === CUSTOM AUTH ===
        String customAuth = (String) nodeData.get("authorization");
        if (customAuth != null && !customAuth.isBlank()) {
            String processedAuth = TemplateUtils.substitute(customAuth, context);
            headers.add("Authorization", processedAuth);
            log.info("✅ Added custom Authorization header");
        }
    }




    private void processResponse(ResponseEntity<String> response, Map<String, Object> output, Map<String, Object> context) {
        // Add context to output
        if (context != null) {
            output.putAll(context);
        }

        // Status code
        HttpStatusCode statusCode = response.getStatusCode();
        output.put("http_status_code", statusCode.value());

        // Status text (safe cast)
        if (statusCode instanceof HttpStatus httpStatus) {
            output.put("http_status_text", httpStatus.name()); // e.g., OK, NOT_FOUND
        } else {
            output.put("http_status_text", "UNKNOWN");
        }

        output.put("http_response_body", response.getBody());
        output.put("http_response_headers", response.getHeaders().toSingleValueMap());
        output.put("http_request_successful", statusCode.is2xxSuccessful());
        output.put("node_type", "httpRequest");
        output.put("executed_at", Instant.now().toString());

        // Try to parse response body as JSON if possible
        if (response.getBody() != null && !response.getBody().trim().isEmpty()) {
            try {
                Object parsedBody = objectMapper.readValue(response.getBody(), Object.class);
                output.put("http_response_json", parsedBody);

                if (parsedBody instanceof Map) {
                    Map<String, Object> responseMap = (Map<String, Object>) parsedBody;
                    responseMap.forEach((key, value) -> output.put("response_" + key, value));
                }
            } catch (Exception e) {
                log.debug("Response body is not valid JSON, keeping as string");
            }
        }
        log.info("HTTP request completed with status: {}",
                statusCode.value());
        try {
            String prettyOutput = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
            log.info("✅ Final Output of HTTP Node:\n{}", prettyOutput);
        } catch (Exception e) {
            log.warn("Failed to pretty-print HTTP node output", e);
        }

    }


    private void publishCompletionEvent(NodeExecutionMessage message, Map<String, Object> output, String status, long duration) {
        NodeCompletionMessage completion = NodeCompletionMessage.builder()
                .executionId(message.getExecutionId())
                .workflowId(message.getWorkflowId())
                .nodeId(message.getNodeId())
                .nodeType(message.getNodeType())
                .status(status)
                .output(output)
                .timestamp(Instant.now())
                .processingTime(duration)
                .build();

        eventProducer.publishNodeCompletion(completion);
        log.info("Published completion event for HTTP request node: {} with status: {} in {}ms",
                message.getNodeId(), status, duration);
    }
}
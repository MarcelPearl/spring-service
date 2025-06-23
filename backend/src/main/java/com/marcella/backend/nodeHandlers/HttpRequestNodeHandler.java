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
            log.info("🔍 Node data: {}", data.keySet());
            log.info("🔍 Node URL: {}", data.get("url"));
            log.info("🔍 useGoogleAuth: {}", data.get("useGoogleAuth"));

            String rawUrl = (String) data.get("url");
            if (rawUrl == null || rawUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("URL is required for HTTP request");
            }
            String processedUrl = TemplateUtils.substitute(rawUrl, context);

            String method = (String) data.getOrDefault("method", "GET");
            method = TemplateUtils.substitute(method, context).toUpperCase();

            Map<String, String> processedHeaders = processHeaders(data.get("headers"), context);

            Object processedBody = processBody(data.get("body"), context);

            URI finalUri = buildFinalUri(processedUrl, context);

            log.info("Preparing HTTP request to: {}", finalUri);
            log.info("Method: {}, Headers: {}", method, processedHeaders.keySet());
            if (processedBody != null) {
                log.info("Body type: {}", processedBody.getClass().getSimpleName());
            }

            HttpHeaders httpHeaders = new HttpHeaders();

            if (processedHeaders != null && !processedHeaders.isEmpty()) {
                processedHeaders.forEach(httpHeaders::add);
            }

            handleAuthentication(finalUri.toString(), context, httpHeaders, data);

            if (processedBody != null && !httpHeaders.containsKey("Content-Type")) {
                if (processedBody instanceof String) {
                    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                } else {
                    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                }
            }

            HttpEntity<?> entity = new HttpEntity<>(processedBody, httpHeaders);

            ResponseEntity<String> response = restTemplate.exchange(
                    finalUri,
                    HttpMethod.valueOf(method),
                    entity,
                    String.class
            );

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
                try {
                    return objectMapper.readValue(bodyStr, Object.class);
                } catch (Exception e) {
                    return bodyStr;
                }
            } else if (bodyObj instanceof Map) {
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
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl);

            URI tempUri = builder.build().toUri();
            String finalUrl = tempUri.toString();

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

        if (url.contains("googleapis.com") || url.contains("google.com/api") || Boolean.TRUE.equals(nodeData.get("useGoogleAuth"))) {
            String googleToken = null;

            if (context.containsKey("googleAccessToken")) {
                Object tokenObj = context.get("googleAccessToken");
                if (tokenObj instanceof String tokenStr) {
                    googleToken = tokenStr;
                    log.info("✅ Google Access Token found in context");
                }
            }

            if ((googleToken == null || googleToken.isBlank()) && headers.containsKey("X-Google-Access-Token")) {
                googleToken = headers.getFirst("X-Google-Access-Token");
                log.info("🧪 Found Google token in X-Google-Access-Token header");
            }

            if (googleToken == null || googleToken.isBlank()) {
                log.warn("❌ No Google access token available for request to: {}", url);
            } else {
                headers.setBearerAuth(googleToken);
                log.info("✅ Applied Google token to Authorization header for request to: {}", url);
            }
        }
    }

    private void processResponse(ResponseEntity<String> response, Map<String, Object> output, Map<String, Object> context) {
        if (context != null) {
            output.putAll(context);
        }

        HttpStatusCode statusCode = response.getStatusCode();
        output.put("http_status_code", statusCode.value());

        if (statusCode instanceof HttpStatus httpStatus) {
            output.put("http_status_text", httpStatus.name());
        } else {
            output.put("http_status_text", "UNKNOWN");
        }

        output.put("http_response_body", response.getBody());
        output.put("http_response_headers", response.getHeaders().toSingleValueMap());
        output.put("http_request_successful", statusCode.is2xxSuccessful());
        output.put("node_type", "httpRequest");
        output.put("executed_at", Instant.now().toString());


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
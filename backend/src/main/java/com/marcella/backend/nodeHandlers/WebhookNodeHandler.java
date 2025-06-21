package com.marcella.backend.nodeHandlers;

import com.marcella.backend.services.WorkflowEventProducer;
import com.marcella.backend.workflow.NodeCompletionMessage;
import com.marcella.backend.workflow.NodeExecutionMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookNodeHandler implements NodeHandler {

    private final WorkflowEventProducer eventProducer;

    @Override
    public boolean canHandle(String nodeType) {
        return "webhook".equals(nodeType);
    }

    @Override
    public Map<String, Object> execute(NodeExecutionMessage message) {
        log.info("Executing webhook node: {}", message.getNodeId());

        try {
            Map<String, Object> nodeData = message.getNodeData();
            Map<String, Object> context = message.getContext();

            String url = (String) nodeData.get("url");
            String method = (String) nodeData.getOrDefault("method", "POST");
            Map<String, Object> payload = (Map<String, Object>) nodeData.get("payload");

            // Substitute templates in payload
            Map<String, Object> processedPayload = new HashMap<>();
            if (payload != null) {
                for (Map.Entry<String, Object> entry : payload.entrySet()) {
                    String value = substituteTemplate(String.valueOf(entry.getValue()), context);
                    processedPayload.put(entry.getKey(), value);
                }
            }

            // Simulate webhook call
            log.info("Webhook {} called to {} with payload: {}", method, url, processedPayload);

            Map<String, Object> output = new HashMap<>();
            output.put("webhook_called", true);
            output.put("url", url);
            output.put("method", method);
            output.put("response_status", 200); // Simulated
            output.put("called_at", Instant.now().toString());

            publishCompletionEvent(message, output, "COMPLETED");
            return output;

        } catch (Exception e) {
            log.error("Webhook node failed: {}", message.getNodeId(), e);
            publishCompletionEvent(message, Map.of("error", e.getMessage()), "FAILED");
            throw e;
        }
    }

    private String substituteTemplate(String template, Map<String, Object> context) {
        if (template == null || context == null) return template;

        String result = template;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    private void publishCompletionEvent(NodeExecutionMessage message, Map<String, Object> output, String status) {
        NodeCompletionMessage completionMessage = NodeCompletionMessage.builder()
                .executionId(message.getExecutionId())
                .workflowId(message.getWorkflowId())
                .nodeId(message.getNodeId())
                .nodeType(message.getNodeType())
                .status(status)
                .output(output)
                .timestamp(Instant.now())
                .processingTime(System.currentTimeMillis())
                .build();

        eventProducer.publishNodeCompletion(completionMessage);
        log.info("Published completion event for node: {} with status: {}", message.getNodeId(), status);
    }
}
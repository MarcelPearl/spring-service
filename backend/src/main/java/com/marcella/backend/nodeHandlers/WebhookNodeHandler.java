package com.marcella.backend.nodeHandlers;

import com.marcella.backend.services.WorkflowEventProducer;
import com.marcella.backend.utils.TemplateUtils;
import com.marcella.backend.workflow.NodeCompletionMessage;
import com.marcella.backend.workflow.NodeExecutionMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        return "trigger".equals(nodeType);
    }

    @Override
    public Map<String, Object> execute(NodeExecutionMessage message) {
        long startTime = System.currentTimeMillis();
        log.info("Executing webhook node: {}", message.getNodeId());

        try {
            Map<String, Object> nodeData = message.getNodeData();
            Map<String, Object> context = message.getContext();

            String url = TemplateUtils.substitute((String) nodeData.get("url"), context);
            String method = TemplateUtils.substitute((String) nodeData.getOrDefault("method", "POST"), context);
            Map<String, Object> payload = (Map<String, Object>) nodeData.get("payload");

            Map<String, Object> processedPayload = new HashMap<>();
            if (payload != null) {
                for (Map.Entry<String, Object> entry : payload.entrySet()) {
                    String value = TemplateUtils.substitute(String.valueOf(entry.getValue()), context);
                    processedPayload.put(entry.getKey(), value);
                }
            }

            log.info("Webhook {} called to {} with payload: {}", method, url, processedPayload);

            Map<String, Object> output = new HashMap<>();
            if (context != null) {
                output.putAll(context);
            }

            output.put("webhook_called", true);
            output.put("url", url);
            output.put("method", method);
            output.put("response_status", 200);
            output.put("called_at", Instant.now().toString());
            output.put("node_type", "webhook");
            output.put("node_executed_at", Instant.now().toString());

            long processingTime = System.currentTimeMillis() - startTime;
            publishCompletionEvent(message, output, "COMPLETED", processingTime);
            return output;

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("Webhook node failed: {}", message.getNodeId(), e);
            publishCompletionEvent(message, Map.of("error", e.getMessage()), "FAILED", processingTime);
            throw e;
        }
    }

    private void publishCompletionEvent(NodeExecutionMessage message, Map<String, Object> output, String status, long processingTime) {
        NodeCompletionMessage completionMessage = NodeCompletionMessage.builder()
                .executionId(message.getExecutionId())
                .workflowId(message.getWorkflowId())
                .nodeId(message.getNodeId())
                .nodeType(message.getNodeType())
                .status(status)
                .output(output)
                .timestamp(Instant.now())
                .processingTime(processingTime)
                .build();

        eventProducer.publishNodeCompletion(completionMessage);
        log.info("Published completion event for webhook node: {} with status: {} in {}ms",
                message.getNodeId(), status, processingTime);
    }
}

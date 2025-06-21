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
public class TransformNodeHandler implements NodeHandler {

    private final WorkflowEventProducer eventProducer;

    @Override
    public boolean canHandle(String nodeType) {
        return "transform".equals(nodeType);
    }

    @Override
    public Map<String, Object> execute(NodeExecutionMessage message) {
        log.info("Executing transform node: {}", message.getNodeId());

        try {
            Map<String, Object> nodeData = message.getNodeData();
            Map<String, Object> context = message.getContext();
            Map<String, Object> output = new HashMap<>();

            // Get mapping configuration
            Map<String, Object> mapping = (Map<String, Object>) nodeData.get("mapping");

            if (mapping != null) {
                for (Map.Entry<String, Object> entry : mapping.entrySet()) {
                    String outputKey = entry.getKey();
                    String sourceKey = (String) entry.getValue();

                    if (context.containsKey(sourceKey)) {
                        output.put(outputKey, context.get(sourceKey));
                        log.info("Mapped {} -> {}: {}", sourceKey, outputKey, context.get(sourceKey));
                    }
                }
            }

            output.put("transformed_at", Instant.now().toString());
            output.put("node_type", "transform");

            publishCompletionEvent(message, output, "COMPLETED");
            return output;

        } catch (Exception e) {
            log.error("Transform node failed: {}", message.getNodeId(), e);
            publishCompletionEvent(message, Map.of("error", e.getMessage()), "FAILED");
            throw e;
        }
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
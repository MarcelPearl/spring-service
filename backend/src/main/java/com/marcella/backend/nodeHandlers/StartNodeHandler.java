package com.marcella.backend.nodeHandlers;

import com.marcella.backend.services.WorkflowEventProducer;
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
public class StartNodeHandler implements NodeHandler {

    private final WorkflowEventProducer eventProducer;

    @Override
    public boolean canHandle(String nodeType) {
        return "start".equals(nodeType);
    }

    @Override
    public Map<String, Object> execute(NodeExecutionMessage message) {
        long startTime = System.currentTimeMillis();
        log.info("Start node executed for workflow: {} node: {}", message.getWorkflowId(), message.getNodeId());

        try {
            // Start node doesn't do much processing, just initializes context
            Map<String, Object> output = new HashMap<>();

            // Pass through any context from the node data
            if (message.getNodeData() != null && message.getNodeData().containsKey("context")) {
                Map<String, Object> nodeContext = (Map<String, Object>) message.getNodeData().get("context");
                output.putAll(nodeContext);
                log.info("Start node initialized workflow with {} variables", nodeContext.size());
            }

            // Add any context that was passed in the execution message
            if (message.getContext() != null) {
                output.putAll(message.getContext());
            }

            // Add execution metadata
            output.put("node_executed_at", Instant.now().toString());
            output.put("node_type", "start");
            output.put("execution_id", message.getExecutionId().toString());
            output.put("started_by", "workflow_coordinator");

            // Calculate processing time
            long processingTime = System.currentTimeMillis() - startTime;

            // Publish completion event
            publishCompletionEvent(message, output, "COMPLETED", processingTime);

            return output;

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("Start node failed: {}", message.getNodeId(), e);
            publishCompletionEvent(message, Map.of("error", e.getMessage()), "FAILED", processingTime);
            throw e;
        }
    }

    private void publishCompletionEvent(NodeExecutionMessage message, Map<String, Object> output,
                                        String status, long processingTime) {
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
        log.info("Published completion event for node: {} with status: {} in {}ms",
                message.getNodeId(), status, processingTime);
    }
}
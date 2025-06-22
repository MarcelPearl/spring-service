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
public class DelayNodeHandler implements NodeHandler {

    private final WorkflowEventProducer eventProducer;

    @Override
    public boolean canHandle(String nodeType) {
        return "delay".equals(nodeType);
    }

    @Override
    public Map<String, Object> execute(NodeExecutionMessage message) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        log.info("Executing delay node: {}", message.getNodeId());

        try {
            Map<String, Object> nodeData = message.getNodeData();
            Map<String, Object> context = message.getContext();

            Integer duration = (Integer) nodeData.getOrDefault("duration", 1000);
            log.info("Delaying for {} milliseconds", duration);

            Thread.sleep(duration);

            Map<String, Object> output = new HashMap<>();
            if (context != null) {
                output.putAll(context);
            }

            output.put("delay_completed", true);
            output.put("duration_ms", duration);
            output.put("completed_at", Instant.now().toString());
            output.put("node_type", "delay");
            output.put("node_executed_at", Instant.now().toString());

            long processingTime = System.currentTimeMillis() - startTime;
            publishCompletionEvent(message, output, "COMPLETED", processingTime);

            return output;

        } catch (InterruptedException e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("Delay node failed: {}", message.getNodeId(), e);
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
        log.info("Published completion event for delay node: {} with status: {} in {}ms",
                message.getNodeId(), status, processingTime);
    }
}

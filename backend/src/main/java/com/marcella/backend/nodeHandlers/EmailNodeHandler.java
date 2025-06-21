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
public class EmailNodeHandler implements NodeHandler {

    private final WorkflowEventProducer eventProducer;

    @Override
    public boolean canHandle(String nodeType) {
        return "email".equals(nodeType);
    }

    @Override
    public Map<String, Object> execute(NodeExecutionMessage message) {
        log.info("Executing email node: {}", message.getNodeId());

        try {
            Map<String, Object> nodeData = message.getNodeData();
            Map<String, Object> context = message.getContext();

            // Extract email details with template substitution
            String to = substituteTemplate((String) nodeData.get("to"), context);
            String subject = substituteTemplate((String) nodeData.get("subject"), context);
            String body = substituteTemplate((String) nodeData.get("body"), context);

            // Simulate sending email
            log.info("Email sent to {} with subject '{}'", to, subject);
            log.info("Email body: {}", body);

            Map<String, Object> output = new HashMap<>();
            output.put("email_sent", true);
            output.put("recipient", to);
            output.put("subject", subject);
            output.put("sent_at", Instant.now().toString());

            // IMPORTANT: Publish completion event
            publishCompletionEvent(message, output, "COMPLETED");

            return output;

        } catch (Exception e) {
            log.error("Email node failed: {}", message.getNodeId(), e);
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
package com.marcella.backend.kafka;

import com.marcella.backend.events.WorkflowEvent;
import com.marcella.backend.services.WorkflowExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;


@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEventConsumer {
    private final WorkflowExecutorService executorService;

    @KafkaListener(topics = "workflow-events", groupId = "workflow-group")
    public void consumeWorkflowEvent(WorkflowEvent event) {
        log.info("Received workflow event: {}", event);

        switch (event.getEventType()) {
            case "RUN" -> executorService.executeWorkflow(event.getWorkflowId());

            case "WEBHOOK_TRIGGERED" -> {
                Map<String, Object> payload = event.getPayload();
                String nodeId = (String) payload.get("nodeId");
                Map<String, Object> input = (Map<String, Object>) payload.get("input");

                executorService.executeFromNode(event.getWorkflowId(), nodeId, input);
            }

            default -> log.warn("Unhandled event type: {}", event.getEventType());
        }
    }
}

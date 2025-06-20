package com.marcella.backend.kafka;

import com.marcella.backend.events.WorkflowEvent;
import com.marcella.backend.services.WorkflowExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEventConsumer {
    private final WorkflowExecutorService executorService;

    @KafkaListener(topics = "workflow-events", groupId = "workflow-group")
    public void consumeWorkflowEvent(WorkflowEvent event) {
        log.info("Received workflow event: {}", event);

        if ("CREATED".equals(event.getEventType()) || "RUN".equals(event.getEventType())) {
            executorService.executeWorkflow(event.getWorkflowId());
        }
    }
}

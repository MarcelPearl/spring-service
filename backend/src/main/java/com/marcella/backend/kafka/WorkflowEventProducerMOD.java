package com.marcella.backend.kafka;

import com.marcella.backend.events.WorkflowEventREM;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkflowEventProducerMOD {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishWorkflowEvent(WorkflowEventREM event) {
        kafkaTemplate.send("workflow-events", event.getWorkflowId().toString(), event);
    }
}

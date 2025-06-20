package com.marcella.backend.kafka;

import com.marcella.backend.events.WorkflowEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkflowEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishWorkflowEvent(WorkflowEvent event) {
        kafkaTemplate.send("workflow-events", event.getWorkflowId().toString(), event);
    }
}

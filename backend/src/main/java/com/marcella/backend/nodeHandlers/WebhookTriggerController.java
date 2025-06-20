package com.marcella.backend.nodeHandlers;

import com.marcella.backend.events.WorkflowEvent;
import com.marcella.backend.kafka.WorkflowEventProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/triggers")
public class WebhookTriggerController {

    private final WorkflowEventProducer eventProducer;

    @PostMapping("/{workflowId}")
    public ResponseEntity<String> triggerWorkflow(@PathVariable UUID workflowId, @RequestBody Map<String, Object> input) {
        WorkflowEvent event = new WorkflowEvent(workflowId, null, "RUN", Instant.now(),input);

        eventProducer.publishWorkflowEvent(event);
        return ResponseEntity.ok("Triggered");
    }
}


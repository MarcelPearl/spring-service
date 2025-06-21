package com.marcella.backend.nodeHandlers;

import com.marcella.backend.events.WorkflowEventREM;
import com.marcella.backend.kafka.WorkflowEventProducerMOD;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/triggers",produces = "application/json")
public class WebhookTriggerController {

    private final WorkflowEventProducerMOD eventProducer;

    @PostMapping("/{workflowId}/{nodeId}")
    public ResponseEntity<String> webhookTrigger(
            @PathVariable UUID workflowId,
            @PathVariable String nodeId,
            @RequestBody(required = false) Map<String, Object> input) {

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("nodeId", nodeId);
        metadata.put("input", input);

        WorkflowEventREM event = new WorkflowEventREM(
                workflowId,
                null,
                "WEBHOOK_TRIGGERED",
                Instant.now(),
                metadata
        );

        eventProducer.publishWorkflowEvent(event);
        return ResponseEntity.ok("Webhook received and workflow resumed.");
    }
}


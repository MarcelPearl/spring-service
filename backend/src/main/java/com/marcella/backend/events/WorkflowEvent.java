package com.marcella.backend.events;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class WorkflowEvent {
    private UUID workflowId;
    private UUID userId;
    private String eventType;
    private Instant timestamp;

    public WorkflowEvent(UUID workflowId, UUID userId, String eventType) {
        this.workflowId = workflowId;
        this.userId = userId;
        this.eventType = eventType;
        this.timestamp = Instant.now();
    }

    public WorkflowEvent() {}
}

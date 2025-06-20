package com.marcella.backend.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowEvent {
    private UUID workflowId;
    private UUID userId;
    private String eventType;
    private Instant timestamp;
    private Map<String, Object> payload;
}

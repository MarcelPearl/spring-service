package com.marcella.backend.workflowDtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDto {
    private UUID id;
    private String name;
    private String description;
    private String status;
    private String workflowData;
    private boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
    private UUID ownerId;
}

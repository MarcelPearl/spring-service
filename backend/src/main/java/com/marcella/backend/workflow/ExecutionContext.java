package com.marcella.backend.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionContext {
    private UUID executionId;
    private UUID workflowId;
    private ExecutionStatus status;
    private Instant startTime;
    private Map<String, Object> globalVariables;
    private Map<String, Map<String, Object>> nodeOutputs;
}

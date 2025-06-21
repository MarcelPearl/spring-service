package com.marcella.backend.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionContext {
    private UUID executionId;
    private UUID workflowId;
    private UUID userId;
    private Map<String, Object> globalVariables;
    private Map<String, Map<String, Object>> nodeOutputs; // nodeId -> output data
    private ExecutionStatus status;
    private Instant startTime;
    private Instant endTime;

    public enum ExecutionStatus {
        RUNNING, COMPLETED, FAILED, PAUSED
    }

    public Map<String, Object> getGlobalVariables() {
        if (globalVariables == null) {
            globalVariables = new HashMap<>();
        }
        return globalVariables;
    }

    public Map<String, Map<String, Object>> getNodeOutputs() {
        if (nodeOutputs == null) {
            nodeOutputs = new HashMap<>();
        }
        return nodeOutputs;
    }
}

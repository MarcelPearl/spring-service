package com.marcella.backend.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowEdge {
    private String id;
    private String source;
    private String target;
    private String sourceHandle;
    private String targetHandle;
    private String type;
    private Map<String, Object> data;


    public WorkflowEdge(String source, String target) {
        this.source = source;
        this.target = target;
        this.sourceHandle = "output";
        this.targetHandle = "input";
        this.type = "default";
        this.data = new HashMap<>();
    }

    public WorkflowEdge(String source, String target, String sourceHandle, String targetHandle) {
        this.source = source;
        this.target = target;
        this.sourceHandle = sourceHandle;
        this.targetHandle = targetHandle;
        this.type = "conditional";
        this.data = new HashMap<>();
    }
}

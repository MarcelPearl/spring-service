package com.marcella.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marcella.backend.workflow.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExecutionContextService {

    @Qualifier("customStringRedisTemplate")
    @Autowired
    private RedisTemplate<String, String> customStringRedisTemplate;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CONTEXT_KEY = "execution:context:";
    private static final String DEPENDENCY_KEY = "execution:dependencies:";
    private static final String READY_NODES_KEY = "execution:ready:";

    private DependencyGraph buildDependencyGraph(WorkflowDefinition workflow) {
        Map<String, List<String>> incomingEdges = new HashMap<>();
        Map<String, List<String>> outgoingEdges = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (WorkflowNode node : workflow.getNodes()) {
            String nodeId = node.getId();
            incomingEdges.put(nodeId, new java.util.ArrayList<>());
            outgoingEdges.put(nodeId, new java.util.ArrayList<>());
            inDegree.put(nodeId, 0);
        }

        for (WorkflowEdge edge : workflow.getEdges()) {
            String from = edge.getSource();
            String to = edge.getTarget();

            outgoingEdges.get(from).add(to);
            incomingEdges.get(to).add(from);

            inDegree.put(to, inDegree.get(to) + 1);
        }

        return DependencyGraph.builder()
                .incomingEdges(incomingEdges)
                .outgoingEdges(outgoingEdges)
                .inDegree(inDegree)
                .completedNodes(new java.util.HashSet<>())
                .failedNodes(new java.util.HashSet<>())
                .build();
    }

    public void initializeExecution(UUID executionId, WorkflowDefinition workflow) {
        String contextKey = CONTEXT_KEY + executionId;
        String dependencyKey = DEPENDENCY_KEY + executionId;

        ExecutionContext context = ExecutionContext.builder()
                .executionId(executionId)
                .workflowId(workflow.getId())
                .status(ExecutionStatus.RUNNING)
                .startTime(Instant.now())
                .globalVariables(new HashMap<>())
                .nodeOutputs(new HashMap<>())
                .build();

        redisTemplate.opsForValue().set(contextKey, context);

        DependencyGraph dependencyGraph = buildDependencyGraph(workflow);
        redisTemplate.opsForValue().set(dependencyKey, dependencyGraph);

        redisTemplate.expire(contextKey, Duration.ofHours(24));
        redisTemplate.expire(dependencyKey, Duration.ofHours(24));
    }

    public ExecutionContext getContext(UUID executionId) {
        return (ExecutionContext) redisTemplate.opsForValue().get(CONTEXT_KEY + executionId);
    }

    public void updateNodeOutput(UUID executionId, String nodeId, Map<String, Object> output) {
        String contextKey = CONTEXT_KEY + executionId;
        ExecutionContext context = getContext(executionId);

        if (context != null) {
            context.getNodeOutputs().put(nodeId, output);
            redisTemplate.opsForValue().set(contextKey, context);
        }
    }

    public List<String> getReadyNodes(UUID executionId) {
        return customStringRedisTemplate.opsForList().range(READY_NODES_KEY + executionId, 0, -1);
    }

    public void addReadyNodes(UUID executionId, List<String> nodeIds) {
        String readyKey = READY_NODES_KEY + executionId;
        nodeIds.forEach(nodeId -> redisTemplate.opsForList().rightPush(readyKey, nodeId));
        redisTemplate.expire(readyKey, Duration.ofHours(24));
    }
}

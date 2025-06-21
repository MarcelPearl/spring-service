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
    private final KahnAlgoService kahnAlgoService;

    private static final String CONTEXT_KEY = "execution:context:";
    private static final String DEPENDENCY_KEY = "execution:dependencies:";
    private static final String READY_NODES_KEY = "execution:ready:";
    private static final Duration DEFAULT_EXPIRATION = Duration.ofHours(24);

    public void initializeExecution(UUID executionId, WorkflowDefinition workflow) {
        String contextKey = CONTEXT_KEY + executionId;
        String dependencyKey = DEPENDENCY_KEY + executionId;

        ExecutionContext context = ExecutionContext.builder()
                .executionId(executionId)
                .workflowId(workflow.getId())
                .status(ExecutionContext.ExecutionStatus.RUNNING)
                .startTime(Instant.now())
                .globalVariables(new HashMap<>())
                .nodeOutputs(new HashMap<>())
                .build();

        redisTemplate.opsForValue().set(contextKey, context);

        DependencyGraph dependencyGraph = kahnAlgoService.buildDependencyGraph(workflow);
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

    public void storeContext(UUID executionId, ExecutionContext context) {
        String contextKey = CONTEXT_KEY + executionId;
        redisTemplate.opsForValue().set(contextKey, context);
        redisTemplate.expire(contextKey, DEFAULT_EXPIRATION);
    }

    public void updateContext(UUID executionId, ExecutionContext context) {
        storeContext(executionId, context);
    }

    public void storeDependencyGraph(UUID executionId, DependencyGraph graph) {
        String dependencyKey = DEPENDENCY_KEY + executionId;
        redisTemplate.opsForValue().set(dependencyKey, graph);
        redisTemplate.expire(dependencyKey, DEFAULT_EXPIRATION);
    }
}

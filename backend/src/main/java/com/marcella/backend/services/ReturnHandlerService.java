package com.marcella.backend.services;

import com.marcella.backend.workflow.ExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReturnHandlerService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ExecutionContextService contextService;

    private static final String RETURN_VARIABLES_KEY = "execution:return_vars:";
    private static final Duration DEFAULT_EXPIRATION = Duration.ofHours(24);

    public void storeReturnVariables(UUID executionId, List<String> returnVariables) {
        if (returnVariables == null || returnVariables.isEmpty()) {
            log.info("No return variables specified for execution: {}", executionId);
            return;
        }

        String key = RETURN_VARIABLES_KEY + executionId;

        redisTemplate.opsForList().rightPushAll(key, returnVariables.toArray());
        redisTemplate.expire(key, DEFAULT_EXPIRATION);

        log.info("Stored {} return variables for execution: {}", returnVariables.size(), executionId);
        log.debug("Return variables: {}", returnVariables);
    }

    public List<String> getReturnVariables(UUID executionId) {
        String key = RETURN_VARIABLES_KEY + executionId;

        List<Object> variables = redisTemplate.opsForList().range(key, 0, -1);
        if (variables == null) {
            return new ArrayList<>();
        }
        List<String> returnVariables = variables.stream()
                .map(Object::toString)
                .toList();
        return returnVariables;
    }

    public Map<String, Object> extractReturnVariables(UUID executionId) {
        List<String> requestedVariables = getReturnVariables(executionId);


        ExecutionContext context = contextService.getContext(executionId);
        if (context == null) {
            log.warn("No execution context found for: {}", executionId);
            return new HashMap<>();
        }

        Map<String, Object> allVariables = new HashMap<>();

        if (context.getGlobalVariables() != null) {
            allVariables.putAll(context.getGlobalVariables());
        }

        if (context.getNodeOutputs() != null) {
            for (Map.Entry<String, Map<String, Object>> nodeEntry : context.getNodeOutputs().entrySet()) {
                if (nodeEntry.getValue() != null) {
                    allVariables.putAll(nodeEntry.getValue());
                }
            }
        }

        Map<String, Object> returnVariables = new HashMap<>();

        for (String varName : requestedVariables) {
            if (allVariables.containsKey(varName)) {
                returnVariables.put(varName, allVariables.get(varName));
                log.debug("Added return variable: {} = {}", varName, allVariables.get(varName));
            } else {
                log.warn("Requested return variable '{}' not found in execution context", varName);
                returnVariables.put(varName, null);
            }
        }

        log.info("Extracted {} return variables for execution: {}", returnVariables.size(), executionId);
        return returnVariables;
    }

    public boolean isReturnVariable(UUID executionId, String variableName) {
        List<String> requestedVariables = getReturnVariables(executionId);
        return requestedVariables.contains(variableName);
    }

    public void clearReturnVariables(UUID executionId) {
        String key = RETURN_VARIABLES_KEY + executionId;
        redisTemplate.delete(key);
        log.debug("Cleared return variables for execution: {}", executionId);
    }

    public Map<String, Object> createReturnPayload(UUID executionId, String status) {
        Map<String, Object> returnVariables = extractReturnVariables(executionId);
        List<String> requestedVariables = getReturnVariables(executionId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("executionId", executionId);
        payload.put("status", status);
        payload.put("variables", returnVariables);
        payload.put("requestedVariables", requestedVariables);
        payload.put("returnedVariableCount", returnVariables.size());
        payload.put("timestamp", Instant.now().toString());

        List<String> missingVariables = new ArrayList<>();
        for (String requested : requestedVariables) {
            if (!returnVariables.containsKey(requested) || returnVariables.get(requested) == null) {
                missingVariables.add(requested);
            }
        }

        if (!missingVariables.isEmpty()) {
            payload.put("missingVariables", missingVariables);
            log.warn("Some requested variables were not found: {}", missingVariables);
        }

        return payload;
    }
}
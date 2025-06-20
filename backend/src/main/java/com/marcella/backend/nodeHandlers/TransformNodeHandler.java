package com.marcella.backend.nodeHandlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class TransformNodeHandler implements NodeHandler {

    @Override
    public boolean canHandle(String type) {
        return "transform".equalsIgnoreCase(type);
    }

    @Override
    public Map<String, Object> executeWithResult(Map<String, Object> node, Map<String, Object> input) {
        Map<String, Object> data = (Map<String, Object>) node.get("data");
        Map<String, Object> mapping = (Map<String, Object>) data.get("mapping");

        Map<String, Object> inputData = input != null ? (Map<String, Object>) input.get("output") : null;

        if (mapping == null || inputData == null) {
            log.warn("Missing mapping or input in transform node: {}", node.get("id"));
            return null;
        }

        Map<String, Object> transformed = new HashMap<>();

        for (Map.Entry<String, Object> entry : mapping.entrySet()) {
            String targetField = entry.getKey();
            String sourcePath = entry.getValue().toString();

            Object value = resolvePath(inputData, sourcePath);
            transformed.put(targetField, value);
        }

        log.info("Transform node output for {}: {}", node.get("id"), transformed);
        return Map.of("output", transformed);
    }

    private Object resolvePath(Map<String, Object> data, String path) {
        String[] parts = path.split("\\.");
        Object current = data;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }
}

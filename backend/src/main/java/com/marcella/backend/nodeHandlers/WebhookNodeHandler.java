package com.marcella.backend.nodeHandlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class WebhookNodeHandler implements NodeHandler {

    @Override
    public boolean canHandle(String type) {
        return "webhook".equalsIgnoreCase(type);
    }

    @Override
    public Map<String, Object> executeWithResult(Map<String, Object> node, Map<String, Object> input) {
        log.info("Webhook node executed: {}", node.get("id"));

        Map<String, Object> data = (Map<String, Object>) node.getOrDefault("data", Map.of());
        Map<String, Object> context = (Map<String, Object>) data.getOrDefault("context", Map.of());

        Map<String, Object> result = input != null && !input.isEmpty() ? input : context;

        return Map.of("output", result);
    }
}

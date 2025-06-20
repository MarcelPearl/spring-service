package com.marcella.backend.nodeHandlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class WebhookNodeHandler implements NodeHandler {

    @Override
    public boolean canHandle(String type) {
        return "webhook".equalsIgnoreCase(type);
    }

    @Override
    public Map<String, Object> executeWithResult(Map<String, Object> node, Map<String, Object> input) {
        log.info("Webhook node executed: {}", node.get("id"));
        return Map.of("output", input);
    }
}

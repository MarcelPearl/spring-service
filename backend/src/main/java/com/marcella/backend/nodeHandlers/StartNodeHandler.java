package com.marcella.backend.nodeHandlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class StartNodeHandler implements NodeHandler {

    @Override
    public boolean canHandle(String type) {
        return "start".equalsIgnoreCase(type);
    }

    @Override
    public Map<String, Object> executeWithResult(Map<String, Object> node, Map<String, Object> input) {
        Map<String, Object> data = (Map<String, Object>) node.get("data");
        Map<String, Object> context = (Map<String, Object>) data.get("context");

        log.info("Start node executed: {}", node.get("id"));
        return Map.of("output", context != null ? context : Map.of());
    }
}



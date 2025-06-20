package com.marcella.backend.nodeHandlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class DelayNodeHandler implements NodeHandler {

    @Override
    public boolean canHandle(String type) {
        return "delay".equalsIgnoreCase(type);
    }

    @Override
    public Map<String, Object> executeWithResult(Map<String, Object> node, Map<String, Object> input) {
        Map<String, Object> data = (Map<String, Object>) node.get("data");
        Integer duration = (Integer) data.getOrDefault("duration", 1000);

        try {
            log.info("Delaying for {} milliseconds", duration);
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Delay node interrupted", e);
        }

        Object output = input != null ? input.get("output") : null;
        return Map.of("output", output);
    }
}

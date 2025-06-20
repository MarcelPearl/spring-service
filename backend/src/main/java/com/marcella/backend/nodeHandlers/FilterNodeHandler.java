package com.marcella.backend.nodeHandlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class FilterNodeHandler implements NodeHandler {

    @Override
    public boolean canHandle(String type) {
        return "condition".equalsIgnoreCase(type);
    }

    @Override
    public Map<String, Object> executeWithResult(Map<String, Object> node, Map<String, Object> input) {
        Map<String, Object> data = (Map<String, Object>) node.get("data");
        Map<String, Object> condition = (Map<String, Object>) data.get("condition");

        if (condition == null) {
            log.warn("No condition specified in filter node: {}", node.get("id"));
            return null;
        }

        String field = (String) condition.get("field");
        String operator = (String) condition.get("operator");
        Object expectedValue = condition.get("value");

        Object actualValue = null;
        if (input != null && input.get("output") instanceof Map<?, ?> outputContext) {
            actualValue = ((Map<?, ?>) outputContext).get(field);
        }

        boolean passed = evaluateCondition(actualValue, operator, expectedValue);

        if (passed) {
            log.info("Filter passed for node {}: {} {} {}", node.get("id"), actualValue, operator, expectedValue);
            return Map.of("true", input.get("output"));
        } else {
            log.info("Filter failed for node {}: {} {} {}", node.get("id"), actualValue, operator, expectedValue);
            return Map.of("false", input.get("output"));
        }
    }

    private boolean evaluateCondition(Object actual, String operator, Object expected) {
        if (actual == null || operator == null || expected == null) return false;

        try {
            double a = Double.parseDouble(actual.toString());
            double b = Double.parseDouble(expected.toString());

            return switch (operator) {
                case ">", "gt" -> a > b;
                case "<", "lt" -> a < b;
                case ">=", "gte" -> a >= b;
                case "<=", "lte" -> a <= b;
                case "==", "eq" -> a == b;
                case "!=", "neq" -> a != b;
                default -> false;
            };
        } catch (NumberFormatException e) {
            return switch (operator) {
                case "==", "eq" -> actual.equals(expected);
                case "!=", "neq" -> !actual.equals(expected);
                default -> false;
            };
        }
    }
}

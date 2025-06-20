package com.marcella.backend.nodeHandlers;

import java.util.Map;

public interface NodeHandler {
    boolean canHandle(String type);
    Map<String, Object> executeWithResult(Map<String, Object> node, Map<String, Object> input);
}

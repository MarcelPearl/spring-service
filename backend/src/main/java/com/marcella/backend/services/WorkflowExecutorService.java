package com.marcella.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marcella.backend.entities.Workflows;
import com.marcella.backend.nodeHandlers.NodeHandler;
import com.marcella.backend.repositories.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutorService {
    private final WorkflowRepository workflowRepository;
    private final ObjectMapper objectMapper;
    private final List<NodeHandler> nodeHandlers;

    public void executeWorkflow(UUID workflowId) {
        Workflows workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new RuntimeException("Workflow not found"));

        Map<String, Object> workflowData = parseWorkflowData(workflow);
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) workflowData.get("nodes");
        List<Map<String, Object>> edges = (List<Map<String, Object>>) workflowData.get("edges");

        Map<String, Map<String, Object>> nodeMap = nodes.stream()
                .collect(java.util.stream.Collectors.toMap(n -> n.get("id").toString(), n -> n));

        for (Map<String, Object> node : nodes) {
            String nodeId = node.get("id").toString();
            boolean hasIncoming = edges.stream().anyMatch(e -> nodeId.equals(e.get("target")));
            if (!hasIncoming) {
                log.info("Starting execution at trigger/root node: {}", nodeId);
                executeNodeRecursively(node, nodeMap, edges, null);
                break;
            }
        }
    }

    private void executeNodeRecursively(
            Map<String, Object> currentNode,
            Map<String, Map<String, Object>> nodeMap,
            List<Map<String, Object>> edges,
            Map<String, Object> inputContext
    ) {
        String type = (String) currentNode.get("type");

        nodeHandlers.stream()
                .filter(h -> h.canHandle(type))
                .findFirst()
                .ifPresentOrElse(handler -> {
                    Map<String, Object> result = handler.executeWithResult(currentNode, inputContext);

                    Map<String, Object> mergedContext = new HashMap<>();
                    if (inputContext != null) mergedContext.putAll(inputContext);
                    if (result != null) mergedContext.putAll(result);

                    List<Map<String, Object>> outgoingEdges = edges.stream()
                            .filter(e -> currentNode.get("id").equals(e.get("source")))
                            .toList();

                    for (Map<String, Object> edge : outgoingEdges) {
                        String targetId = (String) edge.get("target");
                        String sourceHandle = (String) edge.get("sourceHandle");
                        String targetHandle = (String) edge.get("targetHandle");

                        Object outputValue = result != null ? result.getOrDefault(sourceHandle, result.get("output")) : null;

                        if (outputValue == null) {
                            log.warn("No output for handle '{}', skipping node {}", sourceHandle, targetId);
                            continue;
                        }

                        Map<String, Object> nextNode = nodeMap.get(targetId);
                        if (nextNode != null) {
                            Map<String, Object> nextData = (Map<String, Object>) nextNode.getOrDefault("data", new HashMap<>());
                            Map<String, Object> context = (Map<String, Object>) nextData.getOrDefault("context", new HashMap<>());

                            context.put(targetHandle, outputValue);
                            nextData.put("context", context);
                            nextNode.put("data", nextData);

                            executeNodeRecursively(nextNode, nodeMap, edges, mergedContext);
                        }
                    }
                }, () -> log.warn("No handler for node type {}", type));
    }

    public void executeFromNode(UUID workflowId, String nodeId, Map<String, Object> input) {
        Workflows workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new RuntimeException("Workflow not found"));

        Map<String, Object> workflowData = parseWorkflowData(workflow);
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) workflowData.get("nodes");
        List<Map<String, Object>> edges = (List<Map<String, Object>>) workflowData.get("edges");

        Map<String, Map<String, Object>> nodeMap = nodes.stream()
                .collect(Collectors.toMap(n -> n.get("id").toString(), n -> n));

        Map<String, Object> startNode = nodeMap.get(nodeId);
        if (startNode == null) {
            throw new RuntimeException("Node not found: " + nodeId);
        }

        log.info("Resuming execution from node: {}", nodeId);

        Map<String, Object> data = (Map<String, Object>) startNode.getOrDefault("data", new HashMap<>());
        if (input == null) {
            input = (Map<String, Object>) data.getOrDefault("context", new HashMap<>());
        }

        data.put("context", input);
        startNode.put("data", data);

        executeNodeRecursively(startNode, nodeMap, edges, input);
    }

    private Map<String, Object> parseWorkflowData(Workflows workflow) {
        try {
            return objectMapper.readValue(workflow.getWorkflowData(), Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Invalid workflowData", e);
        }
    }
}
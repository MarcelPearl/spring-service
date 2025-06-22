package com.marcella.backend.services;

import com.marcella.backend.workflow.NodeCompletionMessage;
import com.marcella.backend.workflow.NodeExecutionMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishNodeExecution(NodeExecutionMessage message) {
        String topic = determineTopicByNodeType(message.getNodeType());

        kafkaTemplate.send(topic, message.getNodeId(), message)
                .thenAccept(result -> log.info("Node execution message sent: {}", message.getNodeId()))
                .exceptionally(ex -> {
                    log.error("Failed to send node execution message", ex);
                    return null;
                });
    }

    public void publishNodeCompletion(NodeCompletionMessage message) {
        kafkaTemplate.send("node-completion", message.getNodeId(), message);
        log.info("Node completion message sent: {} with status: {}", message.getNodeId(), message.getStatus());
    }

    private String determineTopicByNodeType(String nodeType) {
        return switch (nodeType.toLowerCase()) {
            case "start", "email", "transform", "delay", "webhook" ,"calculator" -> "spring-nodes";
            case "python-task", "text-generation", "question-answer", "k-means","clusterization" -> "fastapi-nodes";
            default -> "spring-nodes";
        };
    }
}

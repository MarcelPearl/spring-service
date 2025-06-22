package com.marcella.backend.nodeHandlers;

import com.marcella.backend.services.WorkflowEventProducer;
import com.marcella.backend.utils.TemplateUtils;
import com.marcella.backend.workflow.NodeCompletionMessage;
import com.marcella.backend.workflow.NodeExecutionMessage;
import com.marcella.backend.nodeHandlers.NodeHandler;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class CalculatorNodeHandler implements NodeHandler{

    private final WorkflowEventProducer eventProducer;
    @PostConstruct
    public void init() {
        log.info("✅ CalculatorNodeHandler initialized");
    }
    @Override
    public boolean canHandle(String nodeType) {
        return Objects.equals(nodeType, "calculator");
    }

    @Override
    public Map<String, Object> execute(NodeExecutionMessage message)throws ScriptException {
        long startTime = System.currentTimeMillis();
        log.info("Executing calculator node: {}", message.getNodeId());

        try {
            Map<String, Object> nodeData = message.getNodeData();
            Map<String, Object> context = message.getContext();

            log.info("Calculator node context variables: {}", context.keySet());

            // Substitute any variables in the expression
            String rawExpression = (String) nodeData.get("expression");
            String expression = TemplateUtils.substitute(rawExpression, context);
            log.info("Evaluating expression: {}", expression);

            // Evaluate the arithmetic expression using JavaScript engine
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");
            Object evalResult = engine.eval(expression);

            // Prepare output
            Map<String, Object> output = new HashMap<>(context);
            output.put("expression", expression);
            output.put("result", evalResult);
            output.put("node_type", "calculator");
            output.put("executed_at", Instant.now().toString());

            long processingTime = System.currentTimeMillis() - startTime;
            publishCompletionEvent(message, output, "COMPLETED", processingTime);

            log.info("Expression evaluated successfully: {} = {}", expression, evalResult);
            return output;

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("Calculator node failed: {}", message.getNodeId(), e);

            Map<String, Object> errorOutput = new HashMap<>();
            if (message.getContext() != null) {
                errorOutput.putAll(message.getContext());
            }
            errorOutput.put("error", e.getMessage());
            errorOutput.put("expression", message.getNodeData().get("expression"));
            errorOutput.put("result", null);
            errorOutput.put("failed_at", Instant.now().toString());

            publishCompletionEvent(message, errorOutput, "FAILED", processingTime);
            throw new RuntimeException("Error evaluating expression", e);
        }
    }

    private void publishCompletionEvent(NodeExecutionMessage message,
                                        Map<String, Object> output,
                                        String status,
                                        long processingTime) {
        NodeCompletionMessage completionMessage = NodeCompletionMessage.builder()
                .executionId(message.getExecutionId())
                .workflowId(message.getWorkflowId())
                .nodeId(message.getNodeId())
                .nodeType(message.getNodeType())
                .status(status)
                .output(output)
                .timestamp(Instant.now())
                .processingTime(processingTime)
                .build();

        eventProducer.publishNodeCompletion(completionMessage);
        log.info("Published completion event for calculator node: {} with status: {} in {}ms",
                message.getNodeId(), status, processingTime);
    }
}

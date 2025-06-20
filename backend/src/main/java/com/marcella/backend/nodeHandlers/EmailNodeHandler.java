package com.marcella.backend.nodeHandlers;

import com.marcella.backend.services.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNodeHandler implements NodeHandler {

    private final EmailService emailService;

    @Override
    public boolean canHandle(String type) {
        return "action".equalsIgnoreCase(type);
    }

    @Override
    public Map<String, Object> executeWithResult(Map<String, Object> node, Map<String, Object> input) {
        Map<String, Object> data = (Map<String, Object>) node.get("data");

        String to = (String) data.get("to");
        String subject = (String) data.get("subject");
        String body = (String) data.get("body");

        Map<String, Object> outputContext = input != null ? (Map<String, Object>) input.get("output") : null;

        if (to == null && outputContext != null) to = (String) outputContext.get("to");
        if (subject == null && outputContext != null) subject = (String) outputContext.get("subject");
        if (body == null && outputContext != null) body = (String) outputContext.get("body");

        if (to == null || subject == null || body == null) {
            throw new IllegalArgumentException("Missing fields in email node");
        }

        emailService.sendEmail(to, subject, body);
        log.info("Email sent to {} with subject '{}'", to, subject);

        return Map.of("output", Map.of("status", "sent", "to", to));
    }
}

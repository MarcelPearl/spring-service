package com.marcella.backend.nodeHandlers;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.marcella.backend.configurations.GoogleCalendarConfig;
import com.marcella.backend.entities.Users;
import com.marcella.backend.repositories.UserRepository;
import com.marcella.backend.services.JwtService;
import com.marcella.backend.services.WorkflowEventProducer;
import com.marcella.backend.workflow.NodeCompletionMessage;
import com.marcella.backend.workflow.NodeExecutionMessage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleCalendarNodeHandler implements NodeHandler {

    private final WorkflowEventProducer eventProducer;

    @Override
    public boolean canHandle(String nodeType) {
        return "googleCalendar".equalsIgnoreCase(nodeType);
    }

    @Override
    public Map<String, Object> execute(NodeExecutionMessage message) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> output = new HashMap<>();

        try {
            String googleToken = (String) message.getContext().get("googleAccessToken");
            if (googleToken == null || googleToken.isBlank()) {
                throw new IllegalStateException("Missing Google access token");
            }

            Map<String, Object> context = message.getContext();
            String userEmail = (context != null && context.containsKey("user_email"))
                    ? context.get("user_email").toString()
                    : "unknown";
            log.info("Google Calendar event being created by user: {}", userEmail);
            Map<String, Object> data = message.getNodeData();
            String summary = (String) data.get("summary");
            String start = (String) data.get("startTime");
            String end = (String) data.get("endTime");

            Calendar service = GoogleCalendarConfig.getCalendarService(googleToken);

            Event event = new Event()
                    .setSummary(summary)
                    .setStart(new EventDateTime().setDateTime(new DateTime(start)))
                    .setEnd(new EventDateTime().setDateTime(new DateTime(end)));

            Event createdEvent = service.events().insert("primary", event).execute();

            output.put("calendar_event_summary", summary);
            output.put("calendar_event_id", createdEvent.getId());
            output.put("calendar_event_link", createdEvent.getHtmlLink());
            output.put("event_created", true);
            output.put("node_type", "googleCalendar");

            publishCompletionEvent(message, output, "COMPLETED", System.currentTimeMillis() - startTime);
            return output;

        } catch (Exception e) {
            log.error("Google Calendar Node Error", e);
            output.put("error", e.getMessage());
            output.put("event_created", false);
            publishCompletionEvent(message, output, "FAILED", System.currentTimeMillis() - startTime);
            throw new RuntimeException("Google Calendar Node failed", e);
        }
    }

    private void publishCompletionEvent(NodeExecutionMessage message, Map<String, Object> output, String status, long duration) {
        NodeCompletionMessage completion = NodeCompletionMessage.builder()
                .executionId(message.getExecutionId())
                .workflowId(message.getWorkflowId())
                .nodeId(message.getNodeId())
                .nodeType(message.getNodeType())
                .status(status)
                .output(output)
                .timestamp(Instant.now())
                .processingTime(duration)
                .build();
        eventProducer.publishNodeCompletion(completion);
    }
}

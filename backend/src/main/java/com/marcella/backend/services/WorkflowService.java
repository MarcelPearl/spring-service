package com.marcella.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marcella.backend.entities.Workflows;
import com.marcella.backend.events.WorkflowEventREM;
import com.marcella.backend.kafka.WorkflowEventProducerMOD;
import com.marcella.backend.mappers.WorkflowMapperMOD;
import com.marcella.backend.repositories.UserRepository;
import com.marcella.backend.repositories.WorkflowRepository;
import com.marcella.backend.workflow.CreateWorkflowRequest;
import com.marcella.backend.workflow.WorkflowDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowService {
    private final WorkflowRepository workflowRepository;
    private final WorkflowEventProducerMOD eventProducer;
    private final WorkflowMapperMOD workflowMapper;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WorkflowDto createWorkflow(CreateWorkflowRequest request, UUID userId) {
        String workflowDataJson;
        try {
            workflowDataJson = objectMapper.writeValueAsString(request.getWorkflowData());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize workflowData", e);
        }
        Workflows workflow = Workflows.builder()
                .name(request.getName())
                .description(request.getDescription())
                .owner(userRepository.findById(userId).orElseThrow())
                .status("DRAFT")
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .workflowData(workflowDataJson)
                .build();

        workflowRepository.save(workflow);

        WorkflowDto dto = workflowMapper.toDto(workflow);

        Map<String, Object> payload = Map.of(
                "name", dto.getName(),
                "description", dto.getDescription(),
                "status", dto.getStatus(),
                "version", dto.getVersion(),
                "createdAt", dto.getCreatedAt().toString()
        );

        WorkflowEventREM event = new WorkflowEventREM(
                dto.getId(),
                userId,
                "CREATED",
                Instant.now(),
                payload
        );

        eventProducer.publishWorkflowEvent(event);

        return dto;
    }

    public Page<WorkflowDto> getWorkflows(UUID userId, String search, Pageable pageable) {
        Page<Workflows> page = workflowRepository.findByOwnerIdAndIsActiveTrue(userId, pageable);
        return page.map(workflowMapper::toDto);
    }
}

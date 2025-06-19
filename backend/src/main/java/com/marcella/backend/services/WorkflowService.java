package com.marcella.backend.services;

import com.marcella.backend.entities.Workflows;
import com.marcella.backend.events.WorkflowEvent;
import com.marcella.backend.kafka.WorkflowEventProducer;
import com.marcella.backend.mappers.WorkflowMapper;
import com.marcella.backend.repositories.UserRepository;
import com.marcella.backend.repositories.WorkflowRepository;
import com.marcella.backend.workflowDtos.CreateWorkflowRequest;
import com.marcella.backend.workflowDtos.WorkflowDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;

@Service
public class WorkflowService {
    private final WorkflowRepository workflowRepository;
    private final WorkflowEventProducer eventProducer;
    private final WorkflowMapper workflowMapper;
    private final UserRepository userRepository;

    public WorkflowService(WorkflowRepository workflowRepository,
                           WorkflowEventProducer eventProducer,
                           WorkflowMapper workflowMapper,
                           UserRepository usersRepository) {
        this.workflowRepository = workflowRepository;
        this.eventProducer = eventProducer;
        this.workflowMapper = workflowMapper;
        this.userRepository = usersRepository;
    }

    public WorkflowDto createWorkflow(CreateWorkflowRequest request, UUID userId) {
        Workflows workflow = Workflows.builder()
                .name(request.getName())
                .description(request.getDescription())
                .owner(userRepository.findById(userId).orElseThrow())
                .status("DRAFT")
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .version(0L)
                .build();

        workflow.setWorkflowData(request.getWorkflowData());

        workflowRepository.save(workflow);

        WorkflowDto dto = workflowMapper.toDto(workflow);
        eventProducer.publishWorkflowEvent(new WorkflowEvent(dto.getId(), userId, "CREATED"));

        return dto;
    }

    public Page<WorkflowDto> getWorkflows(UUID userId, String search, Pageable pageable) {
        Page<Workflows> page = workflowRepository.findByOwnerIdAndIsActiveTrue(userId, pageable);
        return page.map(workflowMapper::toDto);
    }
}

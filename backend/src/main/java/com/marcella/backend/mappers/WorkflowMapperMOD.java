package com.marcella.backend.mappers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marcella.backend.entities.Workflows;
import com.marcella.backend.workflow.WorkflowDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class WorkflowMapperMOD {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WorkflowDto toDto(Workflows entity) {

        WorkflowDto dto = new WorkflowDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        try {
            Map<String, Object> workflowDataMap = objectMapper.readValue(
                    entity.getWorkflowData(), new TypeReference<>() {}
            );
            dto.setWorkflowData(workflowDataMap);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse workflowData JSON", e);
        }
        dto.setStatus(entity.getStatus());
        dto.setActive(entity.isActive());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setVersion(entity.getVersion());
        dto.setOwnerId(entity.getOwner().getId());
        return dto;
    }
}

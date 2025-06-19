package com.marcella.backend.mappers;

import com.marcella.backend.entities.Workflows;
import com.marcella.backend.workflowDtos.WorkflowDto;
import org.springframework.stereotype.Component;

@Component
public class WorkflowMapper {
    public WorkflowDto toDto(Workflows entity) {
        WorkflowDto dto = new WorkflowDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setWorkflowData(entity.getWorkflowData());
        dto.setStatus(entity.getStatus());
        dto.setActive(entity.isActive());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setVersion(entity.getVersion());
        dto.setOwnerId(entity.getOwner().getId());
        return dto;
    }
}

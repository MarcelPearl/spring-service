package com.marcella.backend.sidebar;

import com.marcella.backend.entities.Execution;
import com.marcella.backend.repositories.ExecutionRepository;
import com.marcella.backend.repositories.WorkflowRepository;
import com.marcella.backend.sidebar.SidebarStatsResponse;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SidebarStatsService {

    private final WorkflowRepository workflowRepository;
    private final ExecutionRepository executionRepository;

    public SidebarStatsResponse getStats(UUID userId) {
        long draftWorkflows = workflowRepository.countByOwnerIdAndStatusIgnoreCase(userId, "DRAFT");
        long activeWorkflows = workflowRepository.countByOwnerIdAndStatusIgnoreCase(userId, "ACTIVE");
        long failedExecutions = executionRepository.countByOwnerIdAndStatusIgnoreCase(userId, "FAILED");

        List<Execution> recentRuns = executionRepository.findTop5ByOwnerIdOrderByStartedAtDesc(userId);
        List<Execution> scheduled = executionRepository.findByOwnerIdAndStatusIgnoreCase(userId, "SCHEDULED");

        return SidebarStatsResponse.builder()
                .draftWorkflows(draftWorkflows)
                .activeWorkflows(activeWorkflows)
                .failedExecutions(failedExecutions)
                .recentRuns(recentRuns)
                .scheduledExecutions(scheduled)
                .build();
    }
}

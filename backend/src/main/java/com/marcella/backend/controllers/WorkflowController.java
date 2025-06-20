package com.marcella.backend.controllers;

import com.marcella.backend.entities.Users;
import com.marcella.backend.repositories.ExecutionRepository;
import com.marcella.backend.repositories.WorkflowRepository;
import com.marcella.backend.responses.PageResponse;
import com.marcella.backend.services.ExecutionService;
import com.marcella.backend.services.WorkflowExecutorService;
import com.marcella.backend.services.WorkflowService;
import com.marcella.backend.sidebar.SidebarStatsResponse;
import com.marcella.backend.sidebar.SidebarStatsService;
import com.marcella.backend.workflowDtos.CreateWorkflowRequest;
import com.marcella.backend.workflowDtos.WorkflowDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows")
@Validated
@RequiredArgsConstructor
public class WorkflowController {
    private final WorkflowService workflowService;
    private final WorkflowExecutorService workflowExecutorService;
    private final ExecutionRepository executionRepository;
    private final WorkflowRepository workflowRepository;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PageResponse<WorkflowDto>> getWorkflows(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String search,
            Authentication authentication
    ) {
        UUID userId = getUserIdFromAuth(authentication);
        Pageable pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());

        Page<WorkflowDto> workflows = workflowService.getWorkflows(userId, search, pageable);
        return ResponseEntity.ok(PageResponse.of(workflows));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowDto createWorkflow(
            @Valid @RequestBody CreateWorkflowRequest request,
            Authentication authentication
    ) {
        UUID userId = getUserIdFromAuth(authentication);
        return workflowService.createWorkflow(request, userId);
    }

    private UUID getUserIdFromAuth(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Unauthenticated");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof Users user) {
            return user.getId();
        }

        throw new RuntimeException("Invalid authentication principal: " + principal);
    }

    @PostMapping("/{workflowId}/run")
    public ResponseEntity<String> runWorkflowDirectly(@PathVariable UUID workflowId) {
        workflowExecutorService.executeWorkflow(workflowId);
        return ResponseEntity.ok("Workflow executed");
    }

    @GetMapping("/sidebar-stats")
    public ResponseEntity<Map<String, Object>> getSidebarStats(Authentication authentication) {
        UUID userId = getUserIdFromAuth(authentication);
        System.out.println("Sidebar stats requested for user: " + userId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("activeWorkflows", workflowRepository.countByOwnerIdAndStatusIgnoreCase(userId, "ACTIVE"));
        stats.put("draftWorkflows", workflowRepository.countByOwnerIdAndStatusIgnoreCase(userId, "DRAFT"));
        stats.put("recentRuns", executionRepository.findTop5ByOwnerIdOrderByStartedAtDesc(userId).size());
        stats.put("failedExecutions", executionRepository.countByOwnerIdAndStatusIgnoreCase(userId, "FAILED"));
        stats.put("scheduled", 0);

        return ResponseEntity.ok(stats);
    }
}

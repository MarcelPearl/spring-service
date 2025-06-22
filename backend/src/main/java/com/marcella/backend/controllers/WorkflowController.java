package com.marcella.backend.controllers;

import com.marcella.backend.entities.Users;
import com.marcella.backend.repositories.ExecutionRepository;
import com.marcella.backend.repositories.WorkflowRepository;
import com.marcella.backend.responses.PageResponse;
import com.marcella.backend.services.DistributedWorkflowCoordinator;
import com.marcella.backend.services.JwtService;
import com.marcella.backend.services.WorkflowService;
import com.marcella.backend.sidebar.SidebarStatsResponse;
import com.marcella.backend.sidebar.SidebarStatsService;
import com.marcella.backend.workflow.CreateWorkflowRequest;
import com.marcella.backend.workflow.WorkflowDto;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final DistributedWorkflowCoordinator workflowCoordinator;
    private final JwtService jwtService;

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

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowDto> getWorkflow(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID userId = getUserIdFromAuth(authentication);
        WorkflowDto workflow = workflowService.getWorkflow(id, userId);
        return ResponseEntity.ok(workflow);
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

    @PutMapping("/{id}")
    public ResponseEntity<WorkflowDto> updateWorkflow(
            @PathVariable UUID id,
            @Valid @RequestBody CreateWorkflowRequest request,
            Authentication authentication) {
        UUID userId = getUserIdFromAuth(authentication);
        WorkflowDto updated = workflowService.updateWorkflow(id, request, userId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkflow(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID userId = getUserIdFromAuth(authentication);
        workflowService.deleteWorkflow(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<WorkflowDto> duplicateWorkflow(
            @PathVariable UUID id,
            @RequestParam(required = false) String name,
            Authentication authentication) {
        UUID userId = getUserIdFromAuth(authentication);
        WorkflowDto duplicated = workflowService.duplicateWorkflow(id, userId, name);
        return ResponseEntity.ok(duplicated);
    }

    @PostMapping("/{workflowId}/run")
    public ResponseEntity<Map<String, Object>> runWorkflow(
            @PathVariable UUID workflowId,
            @RequestBody(required = false) Map<String, Object> payload,
            HttpServletRequest request
    ) {
        try {
            if (payload == null) {
                payload = new HashMap<>();
            }

            String googleToken = request.getHeader("X-Google-Access-Token");
            if (googleToken != null && !googleToken.isBlank()) {
                payload.put("googleAccessToken", googleToken);
            }

            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String jwt = authHeader.substring(7);
                String userEmail = jwtService.extractEmail(jwt);
                payload.put("user_email", userEmail);
            }

            UUID executionId = workflowCoordinator.startWorkflowExecution(workflowId, payload);

            return ResponseEntity.ok(Map.of(
                    "message", "Workflow execution started",
                    "workflowId", workflowId,
                    "executionId", executionId,
                    "status", "INITIATED"
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage(),
                    "workflowId", workflowId,
                    "status", "FAILED"
            ));
        }
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
}

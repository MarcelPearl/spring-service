package com.marcella.backend.controllers;

import com.marcella.backend.entities.Users;
import com.marcella.backend.responses.PageResponse;
import com.marcella.backend.services.DistributedWorkflowCoordinator;
import com.marcella.backend.services.JwtService;
import com.marcella.backend.services.WorkflowService;
import com.marcella.backend.workflow.CreateWorkflowRequest;
import com.marcella.backend.workflow.WorkflowDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflows")
@Validated
@RequiredArgsConstructor
@Slf4j
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

            log.info("🚀 Starting workflow execution: {} with payload keys: {}",
                    workflowId, payload.keySet());

            String googleToken = request.getHeader("X-Google-Access-Token");
            if (googleToken != null && !googleToken.isBlank()) {
                payload.put("googleAccessToken", googleToken);
                log.info("✅ Added Google access token to payload");
            } else {
                log.warn("⚠️ No Google access token found in headers");
            }

            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String jwt = authHeader.substring(7);
                try {
                    String userEmail = jwtService.extractEmail(jwt);
                    payload.put("user_email", userEmail);
                    log.info("✅ Added user email to payload: {}", userEmail);
                } catch (Exception e) {
                    log.warn("⚠️ Failed to extract user email from JWT: {}", e.getMessage());
                }
            }

            payload.put("execution_started_at", Instant.now().toString());
            payload.put("workflow_id", workflowId.toString());

            Map<String, Object> logPayload = new HashMap<>(payload);
            logPayload.remove("googleAccessToken"); // Don't log sensitive tokens
            log.info("📦 Final execution payload: {}", logPayload);

            UUID executionId = workflowCoordinator.startWorkflowExecution(workflowId, payload);

            return ResponseEntity.ok(Map.of(
                    "message", "Workflow execution started successfully",
                    "workflowId", workflowId,
                    "executionId", executionId,
                    "status", "INITIATED",
                    "timestamp", Instant.now().toString()
            ));

        } catch (Exception e) {
            log.error("❌ Failed to start workflow execution: {}", workflowId, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage(),
                    "workflowId", workflowId,
                    "status", "FAILED",
                    "timestamp", Instant.now().toString()
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

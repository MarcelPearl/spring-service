package com.marcella.backend.workflowDtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateWorkflowRequest {
    @NotBlank
    private String name;

    private String description;

    @NotNull
    private String workflowData;
}
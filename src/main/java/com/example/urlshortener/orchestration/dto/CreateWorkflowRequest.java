package com.example.urlshortener.orchestration.dto; import jakarta.validation.constraints.NotBlank; public record CreateWorkflowRequest(@NotBlank String requirement, String scenario) {}

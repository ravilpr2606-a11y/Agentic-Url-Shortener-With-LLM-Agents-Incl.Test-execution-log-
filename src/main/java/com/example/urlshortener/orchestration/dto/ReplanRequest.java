package com.example.urlshortener.orchestration.dto; import jakarta.validation.constraints.NotBlank; public record ReplanRequest(@NotBlank String revisedRequirement,String reason) {}

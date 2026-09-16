package com.example.urlshortener.orchestration.dto;
import jakarta.validation.constraints.NotBlank;
public record ApprovalRequest(@NotBlank String decision,@NotBlank String approver,String comment,String revisedRequirement) {}

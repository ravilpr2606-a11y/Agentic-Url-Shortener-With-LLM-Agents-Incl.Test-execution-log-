package com.example.urlshortener.orchestration.controller;
import com.example.urlshortener.orchestration.dto.*; import com.example.urlshortener.orchestration.service.OrchestrationService; import jakarta.validation.Valid; import org.springframework.http.ResponseEntity; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/v1/workflows")
public class WorkflowController { private final OrchestrationService service; public WorkflowController(OrchestrationService service){this.service=service;}
 @PostMapping public ResponseEntity<WorkflowResponse> create(@Valid @RequestBody CreateWorkflowRequest r){return ResponseEntity.accepted().body(service.create(r));}
 @GetMapping("/metrics") public Object metrics(){return service.metrics();}
 @GetMapping("/{id}") public Object inspect(@PathVariable UUID id){return service.inspect(id);}
 @PostMapping("/{id}/approvals/{gate}") public WorkflowResponse approve(@PathVariable UUID id,@PathVariable String gate,@Valid @RequestBody ApprovalRequest r){return service.approve(id,gate.toUpperCase(),r);}
 @PostMapping("/{id}/clarification") public WorkflowResponse clarify(@PathVariable UUID id,@RequestParam String decision,@RequestParam String approver,@RequestParam(required=false,defaultValue="") String comment){return service.clarify(id,decision,approver,comment);}
 @PostMapping("/{id}/replan") public WorkflowResponse replan(@PathVariable UUID id,@Valid @RequestBody ReplanRequest r){return service.replan(id,r);}
 @PostMapping("/{id}/retry") public WorkflowResponse retry(@PathVariable UUID id){return service.retry(id);}
 @PostMapping("/{id}/simulate-failure") public WorkflowResponse failure(@PathVariable UUID id,@RequestParam(defaultValue="transient") String kind){return service.simulateFailure(id,kind);}
 @PostMapping("/{id}/resume") public WorkflowResponse resume(@PathVariable UUID id){return service.resume(id);}
}

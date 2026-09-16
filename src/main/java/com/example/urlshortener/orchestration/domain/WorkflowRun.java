package com.example.urlshortener.orchestration.domain;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="workflow_runs")
public class WorkflowRun {
 @Id private UUID id; @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private WorkflowStatus status;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=60) private WorkflowNode currentNode;
 @Enumerated(EnumType.STRING) @Column(length=60) private WorkflowNode resumeNode; @Column(nullable=false,columnDefinition="text") private String requirement;
 @Column(nullable=false,length=30) private String scenario; @Column(nullable=false,length=30) private String policyVersion;
 @Column(nullable=false) private Instant createdAt; @Column(nullable=false) private Instant updatedAt;
 @Column(nullable=false) private int retryCount; @Column(nullable=false) private int version;
 @Version private long persistenceVersion;
 protected WorkflowRun(){}
 public WorkflowRun(UUID id,String requirement,String scenario,String policyVersion){this.id=id;this.requirement=requirement;this.scenario=scenario;this.policyVersion=policyVersion;this.status=WorkflowStatus.RUNNING;this.currentNode=WorkflowNode.REQUIREMENT_INGESTION;this.resumeNode=null;this.createdAt=Instant.now();this.updatedAt=createdAt;}
 public UUID getId(){return id;} public WorkflowStatus getStatus(){return status;} public WorkflowNode getCurrentNode(){return currentNode;} public String getRequirement(){return requirement;} public String getScenario(){return scenario;} public String getPolicyVersion(){return policyVersion;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public int getRetryCount(){return retryCount;} public int getVersion(){return version;} public long getPersistenceVersion(){return persistenceVersion;} public WorkflowNode getResumeNode(){return resumeNode;} public void setResumeNode(WorkflowNode n){resumeNode=n;}
 public void move(WorkflowNode n, WorkflowStatus s){currentNode=n;status=s;updatedAt=Instant.now();version++;}
 public void setRequirement(String r){requirement=r;updatedAt=Instant.now();version++;}
 public void retry(){retryCount++;updatedAt=Instant.now();version++;}
}

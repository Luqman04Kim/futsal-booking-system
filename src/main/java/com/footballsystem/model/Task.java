package com.footballsystem.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long taskId;

    private String description;
    private String status; // "PENDING", "COMPLETED"
    private LocalDateTime dueDate;
    private LocalDateTime completedAt;

    // Proof of completion (by Staff)
    private String proofImageUrl;
    private String proofImageUrl2;
    private String proofImageUrl3;
    private String proofImageUrl4;
    private String proofImageUrl5;

    // NEW: Image provided by Manager when assigning
    private String taskImageUrl;

    // NEW: Note added by Staff when completing
    private String completionNote;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User assignedStaff;

    @ManyToOne
    @JoinColumn(name = "report_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ProblemReport problemReport;

    // Groups sibling tasks assigned together — when one completes, all in the group auto-complete
    private String taskGroupId;

    private LocalDateTime creationDate = LocalDateTime.now();

    public Task() {
    }

    public Task(String description, String status, LocalDateTime dueDate, User assignedStaff) {
        this.description = description;
        this.status = status;
        this.dueDate = dueDate;
        this.assignedStaff = assignedStaff;
    }

    // Getters & Setters
    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public User getAssignedStaff() {
        return assignedStaff;
    }

    public void setAssignedStaff(User assignedStaff) {
        this.assignedStaff = assignedStaff;
    }

    public String getProofImageUrl() {
        return proofImageUrl;
    }

    public void setProofImageUrl(String proofImageUrl) {
        this.proofImageUrl = proofImageUrl;
    }

    public String getProofImageUrl2() { return proofImageUrl2; }
    public void setProofImageUrl2(String proofImageUrl2) { this.proofImageUrl2 = proofImageUrl2; }

    public String getProofImageUrl3() { return proofImageUrl3; }
    public void setProofImageUrl3(String proofImageUrl3) { this.proofImageUrl3 = proofImageUrl3; }

    public String getProofImageUrl4() { return proofImageUrl4; }
    public void setProofImageUrl4(String proofImageUrl4) { this.proofImageUrl4 = proofImageUrl4; }

    public String getProofImageUrl5() { return proofImageUrl5; }
    public void setProofImageUrl5(String proofImageUrl5) { this.proofImageUrl5 = proofImageUrl5; }

    public String getTaskImageUrl() {
        return taskImageUrl;
    }

    public void setTaskImageUrl(String taskImageUrl) {
        this.taskImageUrl = taskImageUrl;
    }

    public String getCompletionNote() {
        return completionNote;
    }

    public void setCompletionNote(String completionNote) {
        this.completionNote = completionNote;
    }

    public ProblemReport getProblemReport() {
        return problemReport;
    }

    public void setProblemReport(ProblemReport problemReport) {
        this.problemReport = problemReport;
    }

    public String getTaskGroupId() {
        return taskGroupId;
    }

    public void setTaskGroupId(String taskGroupId) {
        this.taskGroupId = taskGroupId;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(LocalDateTime creationDate) {
        this.creationDate = creationDate;
    }
}

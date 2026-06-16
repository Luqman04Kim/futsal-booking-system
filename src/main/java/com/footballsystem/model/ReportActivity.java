package com.footballsystem.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "report_activities")
public class ReportActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long activityId;

    @ManyToOne
    @JoinColumn(name = "report_id")
    private ProblemReport report;

    private String action; // e.g. "Report submitted by Ronaldo"
    private LocalDateTime timestamp;
    private String performedBy; // username of the person who performed the action

    public ReportActivity() {
    }

    public ReportActivity(ProblemReport report, String action, String performedBy) {
        this.report = report;
        this.action = action;
        this.performedBy = performedBy;
        this.timestamp = LocalDateTime.now();
    }

    // Getters & Setters
    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public ProblemReport getReport() {
        return report;
    }

    public void setReport(ProblemReport report) {
        this.report = report;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }
}

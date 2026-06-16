package com.footballsystem.repository;

import com.footballsystem.model.ReportActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportActivityRepository extends JpaRepository<ReportActivity, Long> {
    List<ReportActivity> findByReport_ReportIdOrderByTimestampAsc(Long reportId);
    void deleteByReport_ReportId(Long reportId);
}

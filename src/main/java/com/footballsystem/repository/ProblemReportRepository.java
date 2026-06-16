package com.footballsystem.repository;

import com.footballsystem.model.ProblemReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProblemReportRepository extends JpaRepository<ProblemReport, Long> {
    boolean existsByField_FieldIdAndStatusNot(Long fieldId, String status);
}

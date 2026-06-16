package com.footballsystem.repository;

import com.footballsystem.model.Task;
import com.footballsystem.model.ProblemReport;
import com.footballsystem.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    
    List<Task> findByAssignedStaff(User assignedStaff);

    List<Task> findByProblemReport(ProblemReport problemReport);

    List<Task> findByTaskGroupId(String taskGroupId);
    
}
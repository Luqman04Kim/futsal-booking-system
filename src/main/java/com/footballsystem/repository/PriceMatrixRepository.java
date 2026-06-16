package com.footballsystem.repository;

import com.footballsystem.model.Field;
import com.footballsystem.model.PriceMatrix;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PriceMatrixRepository extends JpaRepository<PriceMatrix, Long> {
    List<PriceMatrix> findByField(Field field);
    Optional<PriceMatrix> findByFieldAndDayOfWeekAndStartTime(Field field, DayOfWeek dayOfWeek, LocalTime startTime);
}
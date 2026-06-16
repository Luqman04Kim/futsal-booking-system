package com.footballsystem.repository;

import com.footballsystem.model.Booking;
import com.footballsystem.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalTime;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

       List<Booking> findByUser(User user);

       // CRITICAL: Custom Query to find overlapping bookings
       // Returns a list of bookings that clash with the requested time
       @Query("SELECT b FROM Booking b WHERE b.field.fieldId = :fieldId " +
                     "AND b.date = :date " +
                     "AND b.status <> 'REJECTED' " + // Ignore rejected bookings
                     "AND ((b.startTime < :endTime AND b.endTime > :startTime))") // Overlap logic
       List<Booking> findOverlappingBookings(@Param("fieldId") Long fieldId,
                     @Param("date") LocalDate date,
                     @Param("startTime") LocalTime startTime,
                     @Param("endTime") LocalTime endTime);

       // Filter bookings by Branch
       List<Booking> findByField_Branch_BranchId(Long branchId);

       // Delete item association from join table to prevent foreign key errors on deletion
       @org.springframework.data.jpa.repository.Modifying
       @org.springframework.transaction.annotation.Transactional
       @Query(value = "DELETE FROM booking_inventory_items WHERE inventory_item_id = :itemId", nativeQuery = true)
       void deleteInventoryItemAssociations(@Param("itemId") Long itemId);
}
package com.footballsystem.repository;

import com.footballsystem.model.Booking;
import com.footballsystem.model.Field;
import com.footballsystem.model.Review;
import com.footballsystem.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByBooking(Booking booking);

    List<Review> findByUser(User user);

    List<Review> findByField(Field field);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.field = :field")
    Double getAverageRatingByField(Field field);
}

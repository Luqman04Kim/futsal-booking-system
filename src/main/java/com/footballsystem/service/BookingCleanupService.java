package com.footballsystem.service;

import com.footballsystem.model.Booking;
import com.footballsystem.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookingCleanupService {

    @Autowired
    private BookingRepository bookingRepository;

    // Run every minute
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredBookings() {
        LocalDateTime expirationTime = LocalDateTime.now().minusMinutes(10);

        List<Booking> pendingBookings = bookingRepository.findAll();

        for (Booking booking : pendingBookings) {
            if ("PENDING".equals(booking.getStatus()) && booking.getCreatedAt() != null
                    && booking.getCreatedAt().isBefore(expirationTime)) {
                // Expire the booking
                booking.setStatus("REJECTED"); // Using REJECTED as it frees the slot in existing logic
                bookingRepository.save(booking);
                System.out.println("Expired booking ID: " + booking.getBookingId());
            }
        }
    }
}

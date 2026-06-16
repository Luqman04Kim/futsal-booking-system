package com.footballsystem.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookingId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "field_id")
    private Field field;

    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private String result; // Match Result
    private String paymentStatus; // NEW: Payment Status (Pay Deposit / Full Payment)

    private Double price;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // QR Attendance
    @Column(unique = true)
    private String qrToken;
    private boolean checkedIn = false;
    private LocalDateTime checkedInAt;

    // Optional Services
    private boolean hirePhotographer;
    private boolean rentJersey;

    // Manager-set Google Drive / photo album link for match photos
    @Column(name = "photo_link", columnDefinition = "TEXT")
    private String photoLink;

    // Dynamic Inventory Services
    @ManyToMany
    @JoinTable(
        name = "booking_inventory_items",
        joinColumns = @JoinColumn(name = "booking_id"),
        inverseJoinColumns = @JoinColumn(name = "inventory_item_id")
    )
    private List<InventoryItem> inventoryItems = new ArrayList<>();

    public Booking() {
    }

    // ** REQUIRED CONSTRUCTOR **
    public Booking(User user, Field field, LocalDate date, LocalTime startTime, LocalTime endTime, double price,
            String status) {
        this.user = user;
        this.field = field;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.price = price;
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.qrToken = UUID.randomUUID().toString();
    }

    // Full Constructor
    public Booking(User user, Field field, LocalDate date, LocalTime startTime, LocalTime endTime, String status,
            Double price, boolean hirePhotographer, boolean rentJersey) {
        this.user = user;
        this.field = field;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.price = price;
        this.hirePhotographer = hirePhotographer;
        this.rentJersey = rentJersey;
        this.createdAt = LocalDateTime.now();
        this.qrToken = UUID.randomUUID().toString();
    }

    // Getters and Setters
    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Field getField() {
        return field;
    }

    public void setField(Field field) {
        this.field = field;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isHirePhotographer() {
        return hirePhotographer;
    }

    public void setHirePhotographer(boolean hirePhotographer) {
        this.hirePhotographer = hirePhotographer;
    }

    public void setPhotographer(boolean hirePhotographer) {
        this.hirePhotographer = hirePhotographer;
    } // Alias

    public boolean isRentJersey() {
        return rentJersey;
    }

    public void setRentJersey(boolean rentJersey) {
        this.rentJersey = rentJersey;
    }

    public String getPhotoLink() {
        return photoLink;
    }

    public void setPhotoLink(String photoLink) {
        this.photoLink = photoLink;
    }

    public List<InventoryItem> getInventoryItems() {
        return inventoryItems;
    }

    public void setInventoryItems(List<InventoryItem> inventoryItems) {
        this.inventoryItems = inventoryItems;
    }

    public void setJersey(boolean rentJersey) {
        this.rentJersey = rentJersey;
    } // Alias

    // QR Attendance getters/setters
    public String getQrToken() {
        return qrToken;
    }

    public void setQrToken(String qrToken) {
        this.qrToken = qrToken;
    }

    public boolean isCheckedIn() {
        return checkedIn;
    }

    public void setCheckedIn(boolean checkedIn) {
        this.checkedIn = checkedIn;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }

    public void setCheckedInAt(LocalDateTime checkedInAt) {
        this.checkedInAt = checkedInAt;
    }

    // Dynamic Match Status Logic
    public String getMatchStatus() {
        if (date == null || startTime == null || endTime == null) {
            return "Unknown";
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDateTime = LocalDateTime.of(date, startTime);
        LocalDateTime endDateTime = LocalDateTime.of(date, endTime);

        if (now.isAfter(endDateTime)) {
            return "Finished";
        } else if (now.isAfter(startDateTime) || now.isEqual(startDateTime)) {
            return "In Match";
        } else {
            return "Upcoming";
        }
    }
}
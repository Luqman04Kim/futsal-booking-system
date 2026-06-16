package com.footballsystem.dto;

public class SlotDTO {
    private String timeLabel;
    private String startTime;
    private String endTime;
    private double price; // Final price (after discount if applicable)
    private double originalPrice; // Original price before discount
    private Integer discountPercentage; // Discount percentage (null if no discount)
    private boolean hasDiscount; // Whether this slot has a discount
    private boolean booked;

    // Original constructor (for backward compatibility)
    public SlotDTO(String timeLabel, String startTime, String endTime, double price, boolean booked) {
        this.timeLabel = timeLabel;
        this.startTime = startTime;
        this.endTime = endTime;
        this.price = price;
        this.originalPrice = price;
        this.hasDiscount = false;
        this.discountPercentage = null;
        this.booked = booked;
    }

    // New constructor with discount info
    public SlotDTO(String timeLabel, String startTime, String endTime, double price,
            double originalPrice, Integer discountPercentage, boolean hasDiscount, boolean booked) {
        this.timeLabel = timeLabel;
        this.startTime = startTime;
        this.endTime = endTime;
        this.price = price;
        this.originalPrice = originalPrice;
        this.discountPercentage = discountPercentage;
        this.hasDiscount = hasDiscount;
        this.booked = booked;
    }

    // Getters
    public String getTimeLabel() {
        return timeLabel;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public double getPrice() {
        return price;
    }

    public double getOriginalPrice() {
        return originalPrice;
    }

    public Integer getDiscountPercentage() {
        return discountPercentage;
    }

    public boolean isHasDiscount() {
        return hasDiscount;
    }

    public boolean isBooked() {
        return booked;
    }
}
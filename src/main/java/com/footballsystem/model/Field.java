package com.footballsystem.model;

import jakarta.persistence.*;

@Entity
@Table(name = "fields")
public class Field {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long fieldId;

    private String name;
    private String type;
    private String status;
    private String size;
    private String imageUrl;
    private String imageUrl2;
    private String imageUrl3;
    private String imageUrl4;
    private String imageUrl5;

    // RE-ADDED FIELDS
    private Double pricePerHour;
    private Double weekendPrice;

    // DISCOUNT FIELDS
    private Integer discountPercentage;
    private Boolean discountActive;

    @ManyToOne
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne
    @JoinColumn(name = "supervisor_id")
    private User supervisor;

    public Field() {
    }

    public Field(String name, String type, String size, String status, Branch branch) {
        this.name = name;
        this.type = type;
        this.size = size;
        this.status = status;
        this.branch = branch;
    }

    // Getters and Setters
    public Long getFieldId() {
        return fieldId;
    }

    public void setFieldId(Long fieldId) {
        this.fieldId = fieldId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImageUrl2() {
        return imageUrl2;
    }

    public void setImageUrl2(String imageUrl2) {
        this.imageUrl2 = imageUrl2;
    }

    public String getImageUrl3() {
        return imageUrl3;
    }

    public void setImageUrl3(String imageUrl3) {
        this.imageUrl3 = imageUrl3;
    }

    public String getImageUrl4() {
        return imageUrl4;
    }

    public void setImageUrl4(String imageUrl4) {
        this.imageUrl4 = imageUrl4;
    }

    public String getImageUrl5() {
        return imageUrl5;
    }

    public void setImageUrl5(String imageUrl5) {
        this.imageUrl5 = imageUrl5;
    }

    public Branch getBranch() {
        return branch;
    }

    public void setBranch(Branch branch) {
        this.branch = branch;
    }

    public User getSupervisor() {
        return supervisor;
    }

    public void setSupervisor(User supervisor) {
        this.supervisor = supervisor;
    }

    // RESTORED GETTERS/SETTERS
    public Double getPricePerHour() {
        return pricePerHour;
    }

    public void setPricePerHour(Double pricePerHour) {
        this.pricePerHour = pricePerHour;
    }

    public Double getWeekendPrice() {
        return weekendPrice;
    }

    public void setWeekendPrice(Double weekendPrice) {
        this.weekendPrice = weekendPrice;
    }

    // DISCOUNT GETTERS/SETTERS
    public Integer getDiscountPercentage() {
        return discountPercentage;
    }

    public void setDiscountPercentage(Integer discountPercentage) {
        this.discountPercentage = discountPercentage;
    }

    public Boolean getDiscountActive() {
        return discountActive;
    }

    public void setDiscountActive(Boolean discountActive) {
        this.discountActive = discountActive;
    }

    // Helper method to calculate discounted price
    public Double getDiscountedPrice(Double originalPrice) {
        if (discountActive != null && discountActive && discountPercentage != null && discountPercentage > 0) {
            return originalPrice * (1 - discountPercentage / 100.0);
        }
        return originalPrice;
    }

    // Check if discount is currently active
    public boolean hasActiveDiscount() {
        return discountActive != null && discountActive && discountPercentage != null && discountPercentage > 0;
    }
}
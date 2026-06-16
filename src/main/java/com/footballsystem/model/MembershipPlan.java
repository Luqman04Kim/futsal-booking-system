package com.footballsystem.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "membership_plans")
public class MembershipPlan {

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "membership_free_services",
        joinColumns = @JoinColumn(name = "membership_plan_id"),
        inverseJoinColumns = @JoinColumn(name = "inventory_item_id")
    )
    private List<InventoryItem> freeServices = new ArrayList<>();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private boolean active = true;

    // Financials
    private Double price;
    private int discountPercentage;

    // Custom Perks
    private boolean freeAddonsIncluded = false;
    private boolean priorityBooking = false;

    @Column(columnDefinition = "TEXT")
    private String perks;

    @Column(name = "card_color")
    private String cardColor = "#fbbf24";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public MembershipPlan() {}

    public MembershipPlan(String name, String description, Double price, int discountPercentage, 
                          boolean freeAddonsIncluded, boolean priorityBooking) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.discountPercentage = discountPercentage;
        this.freeAddonsIncluded = freeAddonsIncluded;
        this.priorityBooking = priorityBooking;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public int getDiscountPercentage() { return discountPercentage; }
    public void setDiscountPercentage(int discountPercentage) { this.discountPercentage = discountPercentage; }

    public boolean isFreeAddonsIncluded() { return freeAddonsIncluded; }
    public void setFreeAddonsIncluded(boolean freeAddonsIncluded) { this.freeAddonsIncluded = freeAddonsIncluded; }

    public boolean isPriorityBooking() { return priorityBooking; }
    public void setPriorityBooking(boolean priorityBooking) { this.priorityBooking = priorityBooking; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getCardColor() { return cardColor; }
    public void setCardColor(String cardColor) { this.cardColor = cardColor; }

    public String getPerks() { return perks; }
    public void setPerks(String perks) { this.perks = perks; }

    public String[] getPerksList() {
        if (perks == null || perks.trim().isEmpty()) {
            return new String[0];
        }
        return perks.split("\\s*,\\s*");
    }

    public List<InventoryItem> getFreeServices() { return freeServices; }
    public void setFreeServices(List<InventoryItem> freeServices) { this.freeServices = freeServices; }

    public String getFreeServiceIdsCsv() {
        if (freeServices == null || freeServices.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < freeServices.size(); i++) {
            sb.append(freeServices.get(i).getId());
            if (i < freeServices.size() - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }
}

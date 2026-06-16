package com.footballsystem.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Double basePrice;

    // If set and < basePrice, display as discounted
    private Double discountedPrice;

    // If true, effective price = 0 and "Free" is displayed
    private boolean free;

    // Soft delete / visibility toggle
    private boolean active = true;

    @Column(name = "icon_class")
    private String iconClass = "fas fa-box"; // FontAwesome class for the icon

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // ── Constructors ──────────────────────────────────
    public InventoryItem() {
        this.createdAt = LocalDateTime.now();
        this.active = true;
    }

    public InventoryItem(String name, String description, Double basePrice) {
        this();
        this.name = name;
        this.description = description;
        this.basePrice = basePrice;
    }

    // ── Business helpers ─────────────────────────────

    /** Price the customer actually pays */
    public double getEffectivePrice() {
        if (free) return 0.0;
        if (discountedPrice != null && discountedPrice < basePrice) return discountedPrice;
        return basePrice != null ? basePrice : 0.0;
    }

    /** Whether a discount is actively applied */
    public boolean hasDiscount() {
        return !free && discountedPrice != null && discountedPrice < basePrice;
    }

    /** Discount percentage (0 if none) */
    public int getDiscountPercentage() {
        if (!hasDiscount()) return 0;
        return (int) Math.round((1 - discountedPrice / basePrice) * 100);
    }

    // ── Getters & Setters ────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getBasePrice() { return basePrice; }
    public void setBasePrice(Double basePrice) { this.basePrice = basePrice; }

    public Double getDiscountedPrice() { return discountedPrice; }
    public void setDiscountedPrice(Double discountedPrice) { this.discountedPrice = discountedPrice; }

    public boolean isFree() { return free; }
    public void setFree(boolean free) { this.free = free; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getIconClass() { return iconClass; }
    public void setIconClass(String iconClass) { this.iconClass = iconClass; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

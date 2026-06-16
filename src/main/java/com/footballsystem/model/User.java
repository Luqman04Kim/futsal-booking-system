package com.footballsystem.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.io.Serializable;

@Entity
@Table(name = "users")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    private String username;
    @Column(unique = true)
    private String email;
    private String password;
    private String role;

    private String imageUrl;
    private String position;
    private String phoneNumber;
    private String staffId; // NEW: Staff ID

    // NEW: Age field
    private Integer age;

    // NEW: Visit Count
    private int visitCount = 0;

    // NEW: Reset Token fields
    private String resetToken;
    private LocalDateTime resetTokenExpiry;

    // Legacy VIP tracking
    private boolean isVip = false;
    private LocalDateTime vipExpiryDate;
    private LocalDateTime vipStartDate;

    // NEW: Dynamic Membership Plan
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "membership_plan_id")
    private MembershipPlan membershipPlan;

    // NEW: Blocked status
    private boolean isBlocked = false;

    @ManyToOne
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @OneToMany(mappedBy = "assignedStaff", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<Task> tasks;

    public User() {
    }

    // --- GETTERS AND SETTERS ---
    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Branch getBranch() {
        return branch;
    }

    public void setBranch(Branch branch) {
        this.branch = branch;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getStaffId() {
        return staffId;
    }

    public void setStaffId(String staffId) {
        this.staffId = staffId;
    }

    // NEW Getter/Setter for Age
    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public int getVisitCount() {
        return visitCount;
    }

    public void setVisitCount(int visitCount) {
        this.visitCount = visitCount;
    }

    // NEW Getter/Setter for Reset Token
    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public LocalDateTime getResetTokenExpiry() {
        return resetTokenExpiry;
    }

    public void setResetTokenExpiry(LocalDateTime resetTokenExpiry) {
        this.resetTokenExpiry = resetTokenExpiry;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
    }

    // Retain legacy method name to avoid breaking frontend logic unexpectedly
    public boolean isVip() {
        if (membershipPlan != null && membershipPlan.isActive()) {
            return true;
        }
        return isVip; // Fallback to legacy flag
    }

    public void setVip(boolean vip) {
        isVip = vip;
    }

    public LocalDateTime getVipExpiryDate() {
        return vipExpiryDate;
    }

    public void setVipExpiryDate(LocalDateTime vipExpiryDate) {
        this.vipExpiryDate = vipExpiryDate;
    }

    public LocalDateTime getVipStartDate() {
        return vipStartDate;
    }

    public void setVipStartDate(LocalDateTime vipStartDate) {
        this.vipStartDate = vipStartDate;
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public void setBlocked(boolean blocked) {
        isBlocked = blocked;
    }

    public MembershipPlan getMembershipPlan() {
        return membershipPlan;
    }

    public void setMembershipPlan(MembershipPlan membershipPlan) {
        this.membershipPlan = membershipPlan;
    }
}
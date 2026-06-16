package com.footballsystem.repository;

import com.footballsystem.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsernameIgnoreCase(String username);

    // NEW: Find users by their role (e.g., "STAFF")
    List<User> findByRole(String role);

    // Search by keyword in username or email
    List<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(String username, String email);

    // Search by role AND keyword
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.role = ?1 AND (LOWER(u.username) LIKE LOWER(CONCAT('%', ?2, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', ?2, '%')))")
    List<User> searchByRoleAndKeyword(String role, String keyword);
}
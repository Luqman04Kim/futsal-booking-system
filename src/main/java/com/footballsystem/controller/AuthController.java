package com.footballsystem.controller;

import com.footballsystem.model.User;
import com.footballsystem.repository.UserRepository;
import com.footballsystem.service.EmailService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
public class AuthController {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EmailService emailService;

    // --- ROOT URL (Landing Page) ---
    @GetMapping("/")
    public String home(HttpSession session) {
        // If user is ALREADY logged in, redirect to their dashboard
        if (session.getAttribute("loggedUser") != null) {
            String role = (String) session.getAttribute("role");
            if ("MANAGER".equalsIgnoreCase(role))
                return "redirect:/manager/dashboard";
            if ("STAFF".equalsIgnoreCase(role))
                return "redirect:/staff/dashboard";
            if ("CUSTOMER".equalsIgnoreCase(role))
                return "redirect:/home";
        }

        // If NOT logged in, show the public Landing Page
        return "index";
    }

    // --- LOGIN ---
    @GetMapping("/login")
    public String showLoginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String processLogin(@RequestParam String email,
            @RequestParam String password,
            HttpSession session,
            Model model) {

        String cleanEmail = email.trim().toLowerCase();

        Optional<User> userOptional = userRepository.findByEmail(cleanEmail);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            if (user.getPassword().equals(password)) {
                session.setAttribute("loggedUser", user);
                session.setAttribute("role", user.getRole());

                // NEW: Track Customer Visits
                if ("CUSTOMER".equalsIgnoreCase(user.getRole())) {
                    user.setVisitCount(user.getVisitCount() + 1);
                    userRepository.save(user);
                }

                // Redirect based on Role
                if ("MANAGER".equalsIgnoreCase(user.getRole())) {
                    return "redirect:/manager/dashboard";
                } else if ("STAFF".equalsIgnoreCase(user.getRole())) {
                    return "redirect:/staff/dashboard";
                } else {
                    return "redirect:/home";
                }
            }
        }

        model.addAttribute("error", "Invalid email or password");
        return "login";
    }

    // --- LOGOUT ---
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/"; // Redirect to Landing Page on logout
    }

    // --- REGISTER ---
    @GetMapping("/register")
    public String showRegisterPage() {
        return "register";
    }

    @PostMapping("/register")
    public String processRegister(@RequestParam String username,
            @RequestParam String email,
            @RequestParam String phoneNumber,
            @RequestParam String password,
            @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
            Model model) {

        String cleanEmail = email.trim().toLowerCase();
        String cleanUsername = username.trim();

        // Validate passwords match (server-side safety net)
        if (confirmPassword != null && !password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match. Please try again.");
            return "register";
        }

        // Check if email already exists
        if (userRepository.findByEmail(cleanEmail).isPresent()) {
            model.addAttribute("error", "Email already registered!");
            return "register";
        }

        // Check if username already exists
        List<User> allUsers = userRepository.findAll();
        boolean usernameExists = allUsers.stream()
                .anyMatch(u -> u.getUsername() != null && u.getUsername().equalsIgnoreCase(cleanUsername));

        if (usernameExists) {
            model.addAttribute("error", "Username already taken!");
            return "register";
        }

        // Validate phone number (numeric only)
        if (phoneNumber != null && !phoneNumber.isEmpty() && !phoneNumber.matches("[0-9]+")) {
            model.addAttribute("error", "Phone number must contain only digits!");
            return "register";
        }

        // Create new customer using Setters (Best Practice)
        User newUser = new User();
        newUser.setUsername(cleanUsername);
        newUser.setEmail(cleanEmail);
        newUser.setPhoneNumber(phoneNumber);
        newUser.setPassword(password);
        newUser.setRole("CUSTOMER");
        newUser.setImageUrl("/img/undraw_profile.svg");

        userRepository.save(newUser);

        // Send Welcome Email
        emailService.sendWelcomeEmail(cleanEmail, username);

        return "redirect:/login?success";
    }

    // --- FORGOT PASSWORD ---
    @GetMapping("/forgot-password")
    public String showForgotPassword() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam String email, Model model) {
        String cleanEmail = email.trim().toLowerCase();
        Optional<User> userOptional = userRepository.findByEmail(cleanEmail);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            // Generate Token
            String token = UUID.randomUUID().toString();
            user.setResetToken(token);
            user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(15));
            userRepository.save(user);

            // Send Email
            emailService.sendPasswordReset(cleanEmail, token);

            model.addAttribute("success", "A password reset link has been sent to your email.");
        } else {
            model.addAttribute("error", "Email not found in our system.");
        }

        return "forgot-password";
    }

    // --- RESET PASSWORD ---
    @GetMapping("/reset-password")
    public String showResetPasswordPage(@RequestParam(required = false) String token, Model model) {
        if (token == null || token.isEmpty()) {
            model.addAttribute("error", "Invalid password reset token.");
            return "login";
        }

        Optional<User> userOptional = userRepository.findAll().stream()
                .filter(u -> token.equals(u.getResetToken()))
                .findFirst();

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
                model.addAttribute("error", "Token has expired. Please request a new one.");
                return "login";
            }
            model.addAttribute("token", token);
            return "reset-password";
        } else {
            model.addAttribute("error", "Invalid token.");
            return "login";
        }
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam String token,
            @RequestParam String password,
            Model model) {

        Optional<User> userOptional = userRepository.findAll().stream()
                .filter(u -> token.equals(u.getResetToken()))
                .findFirst();

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            if (user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
                model.addAttribute("error", "Token has expired.");
                return "login";
            }

            // Update Password
            user.setPassword(password); // In real app, hash this!
            user.setResetToken(null);
            user.setResetTokenExpiry(null);
            userRepository.save(user);

            return "redirect:/login?resetSuccess";
        } else {
            model.addAttribute("error", "Invalid token.");
            return "login";
        }
    }
}
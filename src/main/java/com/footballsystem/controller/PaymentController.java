package com.footballsystem.controller;

import com.footballsystem.model.Booking;
import com.footballsystem.model.InventoryItem;
import com.footballsystem.model.PriceMatrix;
import com.footballsystem.model.User;
import com.footballsystem.repository.BookingRepository;
import com.footballsystem.repository.PriceMatrixRepository;
import com.footballsystem.service.EmailService;
import com.footballsystem.service.PdfService;
import com.footballsystem.service.ToyyibPayService;
import com.footballsystem.repository.UserRepository;
import com.footballsystem.repository.MembershipPlanRepository;
import com.footballsystem.model.MembershipPlan;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Controller
public class PaymentController {

    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private PriceMatrixRepository priceMatrixRepository;
    @Autowired
    private EmailService emailService;
    @Autowired
    private PdfService pdfService;
    @Autowired
    private ToyyibPayService toyyibPayService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MembershipPlanRepository membershipPlanRepository;

    private boolean isCustomer(HttpSession session) {
        Object roleObj = session.getAttribute("role");
        return roleObj != null && "CUSTOMER".equals(roleObj.toString());
    }

    // ─────────────────────────────────────────────────────────────
    // SHOW PAYMENT PAGE
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/payment/{bookingId}")
    public String showPaymentPage(@PathVariable Long bookingId,
            @RequestParam(value = "error", required = false) String error,
            HttpSession session, Model model) {
        if (!isCustomer(session))
            return "redirect:/login";

        Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
        if (bookingOpt.isPresent()) {
            Booking booking = bookingOpt.get();

            // 1. Calculate Base Field Price (Matrix or Standard)
            double fieldPrice = 0.0;
            Optional<PriceMatrix> matrixPrice = priceMatrixRepository
                    .findByFieldAndDayOfWeekAndStartTime(
                            booking.getField(),
                            booking.getDate().getDayOfWeek(),
                            booking.getStartTime());

            if (matrixPrice.isPresent()) {
                fieldPrice = matrixPrice.get().getPrice();
            } else {
                fieldPrice = booking.getField().getPricePerHour() != null ? booking.getField().getPricePerHour() : 0.0;
            }

            // Apply Manager Discount if active
            double originalFieldPrice = fieldPrice;
            double fieldDiscountAmount = 0.0;
            if (booking.getField().hasActiveDiscount()) {
                fieldPrice = booking.getField().getDiscountedPrice(fieldPrice);
                fieldDiscountAmount = originalFieldPrice - fieldPrice;
                model.addAttribute("hasFieldDiscount", true);
                model.addAttribute("fieldDiscountAmount", fieldDiscountAmount);
                model.addAttribute("fieldDiscountPercentage", booking.getField().getDiscountPercentage());
            }
            model.addAttribute("originalFieldPrice", originalFieldPrice);

            // User Plan Details
            boolean hasFreeAddons = booking.getUser().getMembershipPlan() != null && booking.getUser().getMembershipPlan().isFreeAddonsIncluded();
            double planDiscountPercentage = booking.getUser().getMembershipPlan() != null ? booking.getUser().getMembershipPlan().getDiscountPercentage() : 0;

            model.addAttribute("hasFreeAddons", hasFreeAddons);
            java.util.Set<Long> freeServiceIds = new java.util.HashSet<>();
            if (booking.getUser().getMembershipPlan() != null && booking.getUser().getMembershipPlan().getFreeServices() != null) {
                for (InventoryItem freeSvc : booking.getUser().getMembershipPlan().getFreeServices()) {
                    freeServiceIds.add(freeSvc.getId());
                }
            }
            model.addAttribute("freeServiceIds", freeServiceIds);
            if (booking.getUser().getMembershipPlan() != null) {
                model.addAttribute("planName", booking.getUser().getMembershipPlan().getName());
            } else if (booking.getUser().isVip()) {
                model.addAttribute("planName", "VIP");
            }

            // 2. Addons
            double addons = 0.0;
            if (!hasFreeAddons) {
                if (booking.isRentJersey())
                    addons += 50.0;
                if (booking.isHirePhotographer())
                    addons += 200.0;
                if (booking.getInventoryItems() != null) {
                    for (InventoryItem item : booking.getInventoryItems()) {
                        boolean isFree = false;
                        if (booking.getUser().getMembershipPlan() != null && booking.getUser().getMembershipPlan().getFreeServices() != null) {
                            isFree = booking.getUser().getMembershipPlan().getFreeServices().stream()
                                    .anyMatch(freeSvc -> freeSvc.getId().equals(item.getId()));
                        }
                        if (!isFree) {
                            addons += item.getEffectivePrice();
                        }
                    }
                }
            }

            double subtotal = fieldPrice + addons;

            // 3. Plan Discount
            double discount = 0.0;
            if (booking.getUser().isVip() || booking.getUser().getMembershipPlan() != null) {
                double pct = planDiscountPercentage > 0 ? planDiscountPercentage : (booking.getUser().isVip() ? 10.0 : 0.0);
                discount = subtotal * (pct / 100.0);
                model.addAttribute("vipDiscount", discount);
                model.addAttribute("vipDiscountPct", pct);
                model.addAttribute("planName", booking.getUser().getMembershipPlan() != null ? booking.getUser().getMembershipPlan().getName() : "VIP");
            }

            // 4. Final Total
            double totalPrice = subtotal - discount;

            booking.setPrice(totalPrice);
            bookingRepository.save(booking);

            model.addAttribute("booking", booking);
            model.addAttribute("totalPrice", totalPrice);
            model.addAttribute("fieldPrice", fieldPrice);

            boolean isOverduePayment = "APPROVED".equals(booking.getStatus())
                    && "Pay Deposit".equals(booking.getPaymentStatus());
            model.addAttribute("isOverduePayment", isOverduePayment);

            double remainingBalance = isOverduePayment ? Math.max(0, totalPrice - 200.0) : totalPrice;
            model.addAttribute("remainingBalance", remainingBalance);

            // Pass gateway error message if redirected back after failure
            if (error != null) {
                model.addAttribute("gatewayError", true);
            }

            return "payment";
        }
        return "redirect:/my-bookings";
    }

    // ─────────────────────────────────────────────────────────────
    // PROCESS PAYMENT → Redirect to ToyyibPay
    // ─────────────────────────────────────────────────────────────
    @PostMapping("/payment/process")
    public String processPayment(@RequestParam Long bookingId,
            @RequestParam String paymentType,
            HttpSession session,
            Model model) {
        if (!isCustomer(session))
            return "redirect:/login";

        Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
        if (bookingOpt.isEmpty()) {
            return "redirect:/my-bookings";
        }

        Booking booking = bookingOpt.get();

        // Determine the amount to charge
        double totalPrice = booking.getPrice() != null ? booking.getPrice() : 0.0;
        double chargeAmount;
        if ("DEPOSIT".equals(paymentType)) {
            chargeAmount = 200.0;
        } else if ("BALANCE".equals(paymentType)) {
            // Paying outstanding balance (totalPrice - already paid RM200)
            chargeAmount = Math.max(0, totalPrice - 200.0);
        } else {
            chargeAmount = totalPrice;
        }

        // Store paymentType in session for use by return/callback
        session.setAttribute("pendingPaymentType", paymentType);
        session.setAttribute("pendingBookingId", bookingId);

        try {
            String redirectUrl = toyyibPayService.createBillAndGetUrl(booking, paymentType, chargeAmount);
            System.out.println("Redirecting customer to ToyyibPay: " + redirectUrl);
            return "redirect:" + redirectUrl;
        } catch (Exception e) {
            System.err.println("ToyyibPay createBill failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            // Redirect back to payment page with error flag
            return "redirect:/payment/" + bookingId + "?error=1";
        }
    }

    // ─────────────────────────────────────────────────────────────
    // CALLBACK from ToyyibPay (background POST after payment)
    // ─────────────────────────────────────────────────────────────
    @PostMapping("/payment/callback")
    @ResponseBody
    public ResponseEntity<String> handleCallback(
            @RequestParam(value = "refno", required = false) String refNo,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "reason", required = false) String reason,
            @RequestParam(value = "billcode", required = false) String billCode,
            @RequestParam(value = "order_id", required = false) String orderId) {

        System.out.println("=== ToyyibPay Callback Received ===");
        System.out.println("refno=" + refNo + " | status=" + status + " | reason=" + reason
                + " | billcode=" + billCode + " | order_id=" + orderId);

        // status: "1" = success, "2" = pending, "3" = failed
        if ("1".equals(status) && orderId != null) {
            processSuccessfulPayment(orderId, refNo);
        } else {
            System.out.println("Payment not successful. Status: " + status + " | Reason: " + reason);
        }

        return ResponseEntity.ok("OK");
    }

    // ─────────────────────────────────────────────────────────────
    // RETURN URL (browser redirect after ToyyibPay payment)
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/payment/return")
    public String handleReturn(
            @RequestParam(value = "billcode", required = false) String billCode,
            @RequestParam(value = "status_id", required = false) String statusId,
            @RequestParam(value = "order_id", required = false) String orderId,
            @RequestParam(value = "msg", required = false) String msg,
            HttpSession session,
            Model model) {

        System.out.println("=== ToyyibPay Return ===");
        System.out.println("billcode=" + billCode + " | status_id=" + statusId + " | order_id=" + orderId + " | msg=" + msg);

        Long bookingId = null;
        boolean success = "1".equals(statusId);

        // Extract bookingId from orderId (format: FH-{bookingId}-{type})
        if (orderId != null && orderId.startsWith("FH-")) {
            try {
                String[] parts = orderId.split("-");
                if (parts.length >= 2) {
                    bookingId = Long.parseLong(parts[1]);
                }
            } catch (NumberFormatException ignored) {}
        }

        // Fall back to session if orderId not in params
        if (bookingId == null) {
            Object sessionBookingId = session.getAttribute("pendingBookingId");
            if (sessionBookingId != null) {
                bookingId = (Long) sessionBookingId;
            }
        }

        if (success && bookingId != null) {
            // Process payment success (callback may have already done this, but handle it here too)
            String paymentType = (String) session.getAttribute("pendingPaymentType");
            processSuccessfulPayment(
                    orderId != null ? orderId : ("FH-" + bookingId + "-" + (paymentType != null ? paymentType : "FULL")),
                    null
            );
             // Refresh user in session to update membership status
             User loggedUser = (User) session.getAttribute("loggedUser");
             if (loggedUser != null) {
                 userRepository.findById(loggedUser.getUserId()).ifPresent(dbUser -> {
                     session.setAttribute("loggedUser", dbUser);
                     session.setAttribute("role", dbUser.getRole());
                 });
             }

             // Clean up session
             session.removeAttribute("pendingPaymentType");
             session.removeAttribute("pendingBookingId");

            Optional<Booking> bookingOpt = bookingId != null ? bookingRepository.findById(bookingId) : Optional.empty();
            model.addAttribute("booking", bookingOpt.orElse(null));
            model.addAttribute("bookingId", bookingId);
            model.addAttribute("paymentSuccess", true);
            model.addAttribute("billCode", billCode);
            return "payment-success";
        } else {
            // Payment failed or cancelled
            model.addAttribute("paymentSuccess", false);
            model.addAttribute("bookingId", bookingId);
            model.addAttribute("errorMsg", msg != null ? msg : "Payment was not completed.");
            return "payment-success";
        }
    }

    // ─────────────────────────────────────────────────────────────
    // INTERNAL: Apply payment approval to booking
    // ─────────────────────────────────────────────────────────────
    private void processSuccessfulPayment(String orderId, String refNo) {
        if (orderId == null) return;

        // Parse: FH-{bookingId}-{paymentType}
        String[] parts = orderId.split("-");
        if (parts.length < 3) {
            System.err.println("Cannot parse orderId: " + orderId);
            return;
        }

        try {
            Long bookingId = Long.parseLong(parts[1]);
            String paymentType = parts[2]; // FULL, DEPOSIT, or BALANCE

            Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
            if (bookingOpt.isEmpty()) {
                System.err.println("Booking not found: " + bookingId);
                return;
            }

            Booking booking = bookingOpt.get();

            // Avoid double-processing
            if ("APPROVED".equals(booking.getStatus()) && ("Full Payment".equals(booking.getPaymentStatus()) || (booking.getPaymentStatus() != null && booking.getPaymentStatus().contains("Subscription")))) {
                System.out.println("Booking " + bookingId + " already approved, skipping.");
                return;
            }

            booking.setStatus("APPROVED");
            if ("MEMBERSHIP".equals(paymentType)) {
                String statusStr = booking.getPaymentStatus();
                if (statusStr != null && statusStr.startsWith("PENDING_MEMBERSHIP_")) {
                    try {
                        Long planId = Long.parseLong(statusStr.substring("PENDING_MEMBERSHIP_".length()));
                        Optional<MembershipPlan> planOpt = membershipPlanRepository.findById(planId);
                        if (planOpt.isPresent()) {
                            MembershipPlan plan = planOpt.get();
                            User user = booking.getUser();
                            if (user != null) {
                                user = userRepository.findById(user.getUserId()).orElse(user);
                                user.setMembershipPlan(plan);
                                user.setVipStartDate(java.time.LocalDateTime.now());
                                user.setVipExpiryDate(java.time.LocalDateTime.now().plusYears(1));
                                user.setVip(true); // Enable VIP legacy flag as well
                                userRepository.save(user);
                                System.out.println("User " + user.getUsername() + " successfully subscribed to plan: " + plan.getName());
                            }
                            booking.setPaymentStatus(plan.getName() + " Subscription");
                        } else {
                            booking.setPaymentStatus("Membership Subscription");
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing membership payment success: " + e.getMessage());
                        booking.setPaymentStatus("Membership Subscription");
                    }
                } else {
                    booking.setPaymentStatus("Membership Subscription");
                }
            } else if ("DEPOSIT".equals(paymentType)) {
                booking.setPaymentStatus("Pay Deposit");
            } else {
                booking.setPaymentStatus("Full Payment");
            }

            bookingRepository.save(booking);
            System.out.println("Booking " + bookingId + " approved. PaymentType=" + paymentType + " | Ref=" + refNo);

            // Send confirmation email with PDF receipt
            try {
                byte[] pdfReceipt = pdfService.generateReceipt(booking);
                emailService.sendBookingConfirmationWithReceipt(
                        booking.getUser().getEmail(),
                        booking.getUser().getUsername(),
                        booking,
                        pdfReceipt);
            } catch (Exception e) {
                System.err.println("PDF/email failed, sending simple email: " + e.getMessage());
                try {
                    emailService.sendBookingConfirmation(
                            booking.getUser().getEmail(),
                            booking.getUser().getUsername(),
                            booking.getField().getName(),
                            booking.getDate().toString(),
                            booking.getStartTime() + " - " + booking.getEndTime(),
                            booking.getPrice());
                } catch (Exception ex) {
                    System.err.println("Simple email also failed: " + ex.getMessage());
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid bookingId in orderId: " + orderId);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // CANCEL BOOKING
    // ─────────────────────────────────────────────────────────────
    @PostMapping("/payment/cancel")
    public String cancelBooking(@RequestParam Long bookingId, HttpSession session) {
        if (!isCustomer(session))
            return "redirect:/login";

        Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
        if (bookingOpt.isPresent()) {
            bookingRepository.delete(bookingOpt.get());
        }
        return "redirect:/home";
    }

    // ─────────────────────────────────────────────────────────────
    // RECEIPT DOWNLOAD
    // ─────────────────────────────────────────────────────────────
    @GetMapping("/receipt/{bookingId}")
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable Long bookingId, HttpSession session) {
        User loggedUser = (User) session.getAttribute("loggedUser");
        if (loggedUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
        if (bookingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Booking booking = bookingOpt.get();

        String role = (String) session.getAttribute("role");
        boolean isOwner = booking.getUser().getUserId().equals(loggedUser.getUserId());
        boolean isManagerOrStaff = "MANAGER".equals(role) || "STAFF".equals(role);

        if (!isOwner && !isManagerOrStaff) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!"APPROVED".equals(booking.getStatus())) {
            return ResponseEntity.badRequest().build();
        }

        try {
            byte[] pdfBytes = pdfService.generateReceipt(booking);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "Receipt_" + bookingId + ".pdf");
            headers.setContentLength(pdfBytes.length);
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

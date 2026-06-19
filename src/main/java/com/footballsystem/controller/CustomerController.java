package com.footballsystem.controller;

import com.footballsystem.dto.SlotDTO;
import com.footballsystem.model.Booking;
import com.footballsystem.model.Field;
import com.footballsystem.model.InventoryItem;
import com.footballsystem.model.Review;
import com.footballsystem.model.User;
import com.footballsystem.model.PriceMatrix;
import com.footballsystem.repository.BookingRepository;
import com.footballsystem.repository.BranchRepository;
import com.footballsystem.repository.FieldRepository;
import com.footballsystem.repository.InventoryItemRepository;
import com.footballsystem.repository.ReviewRepository;
import com.footballsystem.repository.UserRepository;
import com.footballsystem.repository.PriceMatrixRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import java.time.LocalDateTime;

@Controller
public class CustomerController {

    @Autowired
    private FieldRepository fieldRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BranchRepository branchRepository;
    @Autowired
    private PriceMatrixRepository priceMatrixRepository;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private InventoryItemRepository inventoryItemRepository;
    @Autowired
    private com.footballsystem.repository.MembershipPlanRepository membershipPlanRepository;
    @Autowired
    private com.footballsystem.service.ToyyibPayService toyyibPayService;

    // Helper: Security Check
    private boolean isCustomer(HttpSession session) {
        Object roleObj = session.getAttribute("role");
        return roleObj != null && "CUSTOMER".equals(roleObj.toString());
    }

    @Value("${upload.dir:uploads}")
    private String uploadDirProperty;

    // Helper: Resolve the absolute upload directory
    private Path getUploadDir() throws Exception {
        Path dir = Paths.get(uploadDirProperty).toAbsolutePath();
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        return dir;
    }

    // Helper: Save Image
    private String saveImage(MultipartFile file, String prefix) {
        if (file.isEmpty())
            return null;
        try {
            Path uploadDir = getUploadDir();
            String fileName = prefix + "_" + UUID.randomUUID().toString() + ".png";
            Path dest = uploadDir.resolve(fileName);
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/" + fileName;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Helper: Find overdue deposit bookings (paid deposit but match has ended
    // without full payment)
    private List<Booking> getOverdueDepositBookings(User user) {
        LocalDateTime now = LocalDateTime.now();
        return bookingRepository.findByUser(user).stream()
                .filter(b -> "Pay Deposit".equals(b.getPaymentStatus()))
                .filter(b -> "APPROVED".equals(b.getStatus()))
                .filter(b -> {
                    // Check if match has ended
                    if (b.getDate() != null && b.getEndTime() != null) {
                        LocalDateTime matchEnd = LocalDateTime.of(b.getDate(), b.getEndTime());
                        return now.isAfter(matchEnd);
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    // 1. HOME PAGE
    @GetMapping("/home")
    public String showCustomerHome(@RequestParam(required = false) Long branchId, HttpSession session, Model model) {
        if (!isCustomer(session))
            return "redirect:/login";

        model.addAttribute("branches", branchRepository.findAll());

        if (branchId != null) {
            // Filter fields by branch
            List<Field> fields = fieldRepository.findAll().stream()
                    .filter(f -> f.getBranch() != null && f.getBranch().getBranchId().equals(branchId))
                    .collect(Collectors.toList());

            // Update status display
            for (Field field : fields) {
                boolean isBookedNow = bookingRepository.findAll().stream()
                        .anyMatch(b -> b.getField() != null && b.getField().getFieldId().equals(field.getFieldId())
                                && "APPROVED".equals(b.getStatus())
                                && b.getDate().equals(LocalDate.now())
                                && !LocalTime.now().isBefore(b.getStartTime())
                                && !LocalTime.now().isAfter(b.getEndTime()));

                if (isBookedNow && !"Maintenance".equals(field.getStatus())) {
                    field.setStatus("Booked");
                }
            }

            model.addAttribute("fields", fields);
            model.addAttribute("selectedBranchId", branchId);
            branchRepository.findById(branchId).ifPresent(b -> model.addAttribute("branchName", b.getName()));
        } else {
            model.addAttribute("fields", List.of());
        }
        return "home";
    }

    // 2. BOOKING PAGE
    @GetMapping("/book/{fieldId}")
    public String showBookingPage(@PathVariable Long fieldId,
            @RequestParam(required = false) String date,
            HttpSession session, Model model) {
        if (!isCustomer(session))
            return "redirect:/login";

        // Check for overdue deposit payments
        User loggedUser = (User) session.getAttribute("loggedUser");
        if (loggedUser != null) {
            List<Booking> overdueBookings = getOverdueDepositBookings(loggedUser);
            if (!overdueBookings.isEmpty()) {
                model.addAttribute("overdueBookings", overdueBookings);
                model.addAttribute("hasOverduePayments", true);

                // Calculate total due amount (remaining balance after deposit)
                double totalDue = overdueBookings.stream()
                        .mapToDouble(b -> b.getPrice() != null ? b.getPrice() - 200.0 : 0.0)
                        .sum();
                model.addAttribute("totalDueAmount", totalDue);
            }
        }

        Optional<Field> fieldOpt = fieldRepository.findById(fieldId);
        if (fieldOpt.isPresent()) {
            Field field = fieldOpt.get();
            model.addAttribute("field", field);
            model.addAttribute("selectedDate", (date != null && !date.isEmpty()) ? date : LocalDate.now().toString());
            // Pass active inventory items as add-on services
            model.addAttribute("inventoryItems", inventoryItemRepository.findByActiveTrueOrderByCreatedAtDesc());
            return "book";
        }
        return "redirect:/home";
    }

    // 3. SAVE BOOKING
    @PostMapping("/book/save")
    public String saveBooking(@RequestParam Long fieldId,
            @RequestParam String date,
            @RequestParam String startTime,
            @RequestParam(required = false) List<Long> serviceIds,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!isCustomer(session))
            return "redirect:/login";

        User user = (User) session.getAttribute("loggedUser");
        if (user == null) {
            return "redirect:/login";
        }
        // Refresh user from DB to be safe
        user = userRepository.findById(user.getUserId()).orElse(user);

        // Constraint 1: No new booking if PENDING exists
        boolean hasPending = bookingRepository.findByUser(user).stream()
                .anyMatch(b -> "PENDING".equals(b.getStatus()));
        if (hasPending) {
            redirectAttributes.addFlashAttribute("error",
                    "Please complete the payment for your current booking first.");
            return "redirect:/book/" + fieldId;
        }

        // Constraint 2: No new booking if overdue deposit payments exist
        List<Booking> overdueBookings = getOverdueDepositBookings(user);
        if (!overdueBookings.isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    "You have overdue payments. Please complete your outstanding balance before making a new booking.");
            return "redirect:/book/" + fieldId;
        }

        Optional<Field> fieldOpt = fieldRepository.findById(fieldId);
        if (fieldOpt.isEmpty())
            return "redirect:/home";

        Field field = fieldOpt.get();
        LocalDate bookingDate = LocalDate.parse(date);
        LocalTime start = LocalTime.parse(startTime);
        LocalTime end = start.plusHours(2);

        // Overlap Check
        boolean overlap = bookingRepository.findAll().stream()
                .anyMatch(b -> b.getField() != null
                        && b.getField().getFieldId().equals(fieldId)
                        && b.getDate().equals(bookingDate)
                        && !"REJECTED".equals(b.getStatus())
                        && b.getStartTime().equals(start));

        if (overlap) {
            redirectAttributes.addFlashAttribute("error", "This slot is already booked.");
            return "redirect:/book/" + fieldId;
        }

        // Calculate Price
        double price = 0.0;
        Optional<PriceMatrix> matrixPrice = priceMatrixRepository
                .findByFieldAndDayOfWeekAndStartTime(field, bookingDate.getDayOfWeek(), start);

        if (matrixPrice.isPresent()) {
            price = matrixPrice.get().getPrice();
        } else {
            Double base = field.getPricePerHour();
            price = (base != null) ? base : 0.0;
        }

        // Apply field discount if active
        if (field.hasActiveDiscount()) {
            price = field.getDiscountedPrice(price);
        }

        Booking booking = new Booking(user, field, bookingDate, start, end, price, "PENDING");

        // Add inventory service prices & set the relationship
        if (serviceIds != null && !serviceIds.isEmpty()) {
            double[] serviceTotal = {0.0};
            final User finalUser = user;
            serviceIds.forEach(svcId ->
                inventoryItemRepository.findById(svcId).ifPresent(item -> {
                    boolean isFree = false;
                    if (finalUser.getMembershipPlan() != null && finalUser.getMembershipPlan().getFreeServices() != null) {
                        isFree = finalUser.getMembershipPlan().getFreeServices().stream()
                                .anyMatch(freeSvc -> freeSvc.getId().equals(item.getId()));
                    }
                    if (!isFree) {
                        serviceTotal[0] += item.getEffectivePrice();
                    }
                    booking.getInventoryItems().add(item);
                })
            );
            booking.setPrice(price + serviceTotal[0]);
        }

        bookingRepository.save(booking);

        return "redirect:/payment/" + booking.getBookingId();
    }

    // 4. MY BOOKINGS PAGE
    @GetMapping("/my-bookings")
    public String showMyBookings(HttpSession session, Model model) {
        if (!isCustomer(session))
            return "redirect:/login";

        User user = (User) session.getAttribute("loggedUser");
        if (user == null)
            return "redirect:/login";

        List<Booking> myBookings = bookingRepository.findByUser(user);

        // Sort: Latest first
        myBookings.sort((b1, b2) -> {
            int dateComp = b2.getDate().compareTo(b1.getDate());
            if (dateComp != 0)
                return dateComp;
            return b2.getStartTime().compareTo(b1.getStartTime());
        });

        // Collect booking IDs that already have a review (to hide the Rate button)
        Set<Long> reviewedIds = new HashSet<>();
        for (Booking b : myBookings) {
            reviewRepository.findByBooking(b).ifPresent(r -> reviewedIds.add(b.getBookingId()));
        }

        model.addAttribute("bookings", myBookings);
        model.addAttribute("reviewedBookingIds", reviewedIds);
        return "my-bookings";
    }

    // 4b. REVIEW FORM (GET)
    @GetMapping("/review/{bookingId}")
    public String showReviewForm(@PathVariable Long bookingId, HttpSession session, Model model) {
        if (!isCustomer(session))
            return "redirect:/login";

        User user = (User) session.getAttribute("loggedUser");
        Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
        if (bookingOpt.isEmpty() || !bookingOpt.get().getUser().getUserId().equals(user.getUserId()))
            return "redirect:/my-bookings";

        Booking booking = bookingOpt.get();
        // Only allow review if full payment completed
        if (!"Full Payment".equals(booking.getPaymentStatus()) && !"FULL".equals(booking.getPaymentStatus()))
            return "redirect:/my-bookings";

        // Already reviewed? Redirect back
        if (reviewRepository.findByBooking(booking).isPresent())
            return "redirect:/my-bookings";

        model.addAttribute("booking", booking);
        return "review";
    }

    // 4c. SUBMIT REVIEW (POST)
    @PostMapping("/review/{bookingId}")
    public String submitReview(@PathVariable Long bookingId,
            @RequestParam int rating,
            @RequestParam(required = false) String comment,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isCustomer(session))
            return "redirect:/login";

        User user = (User) session.getAttribute("loggedUser");
        Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);
        if (bookingOpt.isEmpty() || !bookingOpt.get().getUser().getUserId().equals(user.getUserId()))
            return "redirect:/my-bookings";

        Booking booking = bookingOpt.get();
        if (reviewRepository.findByBooking(booking).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "You have already reviewed this booking.");
            return "redirect:/my-bookings";
        }

        if (rating < 1 || rating > 5)
            rating = 3;
        Review review = new Review(booking, user, booking.getField(), rating, comment);
        reviewRepository.save(review);

        redirectAttributes.addFlashAttribute("success", "Thank you for your review!");
        return "redirect:/my-bookings";
    }

    // 5. MY TEAM INFO PAGE (UPDATED)
    @GetMapping("/my-team")
    public String showMyTeamPage(@RequestParam(required = false) Long branchId, HttpSession session, Model model) {
        if (!isCustomer(session))
            return "redirect:/login";

        User user = (User) session.getAttribute("loggedUser");
        if (user == null)
            return "redirect:/login";

        // Refresh user from DB
        user = userRepository.findById(user.getUserId()).orElse(user);

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        // 1. Match Results — personal past bookings (field bookings only, past dates)
        List<Booking> myBookings = bookingRepository.findByUser(user).stream()
                .filter(b -> b.getField() != null)  // only field bookings, exclude VIP transactions
                .collect(Collectors.toList());

        List<Booking> pastBookings = myBookings.stream()
                .filter(b -> {
                    if (b.getDate() == null) return false;
                    if (b.getDate().isBefore(today)) return true;
                    // Today's bookings — include if end time has passed
                    if (b.getDate().equals(today) && b.getEndTime() != null) {
                        return b.getEndTime().isBefore(now);
                    }
                    return false;
                })
                .sorted(Comparator.comparing(Booking::getDate).reversed()
                        .thenComparing(Comparator.comparing(Booking::getStartTime).reversed()))
                .collect(Collectors.toList());

        model.addAttribute("pastBookings", pastBookings);
        model.addAttribute("matchesWithResult", pastBookings.stream().filter(b -> b.getResult() != null).count());
        model.addAttribute("matchesNoResult", pastBookings.stream().filter(b -> b.getResult() == null).count());

        // 2. Match Schedule — public view, all approved/pending bookings for selected branch
        //    Shows last 30 days + all upcoming matches
        List<Booking> matchSchedule = new ArrayList<>();

        if (branchId != null) {
            LocalDate thirtyDaysAgo = today.minusDays(30);

            matchSchedule = bookingRepository.findAll().stream()
                    .filter(b -> b.getField() != null && b.getField().getBranch() != null)
                    .filter(b -> b.getField().getBranch().getBranchId().equals(branchId))
                    .filter(b -> "APPROVED".equals(b.getStatus()) || "PENDING".equals(b.getStatus()))
                    .filter(b -> b.getDate() != null && !b.getDate().isBefore(thirtyDaysAgo))
                    .sorted(Comparator.comparing(Booking::getDate)
                            .thenComparing(Booking::getStartTime))
                    .collect(Collectors.toList());

            model.addAttribute("selectedBranchId", branchId);
        }

        model.addAttribute("matchSchedule", matchSchedule);
        model.addAttribute("branches", branchRepository.findAll());
        model.addAttribute("today", today);

        return "my-team";
    }

    // API: Get Slots
    @GetMapping("/api/slots")
    @ResponseBody
    public List<SlotDTO> getSlots(@RequestParam Long fieldId, @RequestParam String date) {
        List<SlotDTO> slots = new ArrayList<>();
        Optional<Field> fieldOpt = fieldRepository.findById(fieldId);
        if (fieldOpt.isEmpty())
            return slots;

        Field field = fieldOpt.get();
        LocalDate selectedDate = LocalDate.parse(date);
        int[] startHours = { 8, 10, 12, 14, 16, 18, 20, 22, 0 };

        List<Booking> existingBookings = bookingRepository.findAll().stream()
                .filter(b -> b.getField() != null
                        && b.getField().getFieldId().equals(fieldId)
                        && b.getDate().equals(selectedDate)
                        && !"REJECTED".equals(b.getStatus()))
                .collect(Collectors.toList());

        // Check if field has active discount
        boolean hasDiscount = field.hasActiveDiscount();
        Integer discountPercentage = hasDiscount ? field.getDiscountPercentage() : null;

        for (int startHour : startHours) {
            int endHour = (startHour + 2) % 24;
            LocalTime startTime = LocalTime.of(startHour, 0);

            // Price Logic - Original price from matrix or field
            double originalPrice = 0.0;
            Optional<PriceMatrix> matrixPrice = priceMatrixRepository
                    .findByFieldAndDayOfWeekAndStartTime(field, selectedDate.getDayOfWeek(), startTime);

            if (matrixPrice.isPresent()) {
                originalPrice = matrixPrice.get().getPrice();
            } else {
                Double base = field.getPricePerHour();
                originalPrice = (base != null) ? base : 0.0;
            }

            // Apply discount if active
            double finalPrice = hasDiscount ? field.getDiscountedPrice(originalPrice) : originalPrice;

            boolean isBooked = existingBookings.stream().anyMatch(b -> b.getStartTime().equals(startTime));

            // Prevent booking past slots
            if (selectedDate.equals(LocalDate.now()) && startTime.isBefore(LocalTime.now())) {
                isBooked = true;
            }

            // Format Label: "8-10 am", "10-12 pm", "12-2 pm"
            String label = formatSlotLabel(startHour, endHour);
            LocalTime endTime = LocalTime.of(endHour, 0);

            // Use new constructor with discount info
            slots.add(new SlotDTO(label, startTime.toString(), endTime.toString(),
                    finalPrice, originalPrice, discountPercentage, hasDiscount, isBooked));
        }
        return slots;
    }

    private String formatSlotLabel(int start, int end) {
        int displayStart = (start > 12) ? start - 12 : (start == 0 || start == 12 ? 12 : start);
        int displayEnd = (end > 12) ? end - 12 : (end == 0 || end == 12 ? 12 : end);

        String startSuffix = (start >= 12 && start != 24) ? "pm" : "am";
        String endSuffix = (end >= 12 && end != 24 && end != 0) ? "pm" : "am";

        // Special case for midnight
        if (start == 0)
            startSuffix = "am";
        if (end == 0)
            endSuffix = "am";

        if (startSuffix.equals(endSuffix)) {
            return displayStart + "-" + displayEnd + " " + startSuffix;
        } else {
            return displayStart + " " + startSuffix + " - " + displayEnd + " " + endSuffix;
        }
    }

    // PROFILE PAGE
    @GetMapping("/profile")
    public String showProfilePage(HttpSession session, Model model) {
        if (!isCustomer(session))
            return "redirect:/login";

        User user = (User) session.getAttribute("loggedUser");
        if (user != null) {
            // Refresh from DB to get latest data
            Optional<User> dbUserOpt = userRepository.findById(user.getUserId());
            if (dbUserOpt.isPresent()) {
                model.addAttribute("user", dbUserOpt.get());
            }
        }
        return "profile";
    }

    @PostMapping("/change-password")
    public String processChangePassword(@RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isCustomer(session))
            return "redirect:/login";

        User sessionUser = (User) session.getAttribute("loggedUser");
        Optional<User> dbUserOpt = userRepository.findById(sessionUser.getUserId());

        if (dbUserOpt.isPresent()) {
            User user = dbUserOpt.get();
            if (!user.getPassword().equals(currentPassword)) {
                redirectAttributes.addFlashAttribute("error", "Incorrect current password.");
                return "redirect:/profile";
            }
            if (!newPassword.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("error", "New passwords do not match.");
                return "redirect:/profile";
            }
            user.setPassword(newPassword);
            userRepository.save(user);
            redirectAttributes.addFlashAttribute("success", "Password updated successfully!");
        }
        return "redirect:/profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(@RequestParam String username,
            @RequestParam String email,
            @RequestParam String phoneNumber,
            @RequestParam("profileImage") MultipartFile profileImage,
            HttpSession session) {

        if (!isCustomer(session))
            return "redirect:/login";
        User sessionUser = (User) session.getAttribute("loggedUser");

        if (sessionUser != null) {
            Optional<User> dbUserOpt = userRepository.findById(sessionUser.getUserId());
            if (dbUserOpt.isPresent()) {
                User dbUser = dbUserOpt.get();
                dbUser.setUsername(username);
                dbUser.setEmail(email);
                dbUser.setPhoneNumber(phoneNumber);

                String newImage = saveImage(profileImage, "team");
                if (newImage != null)
                    dbUser.setImageUrl(newImage);

                userRepository.save(dbUser);
                session.setAttribute("loggedUser", dbUser);
            }
        }
        return "redirect:/profile?success=true";
    }

    // --- MEMBERSHIP FEATURE ---

    @GetMapping("/membership")
    public String showMembershipPage(HttpSession session, Model model) {
        if (!isCustomer(session))
            return "redirect:/login";
            
        model.addAttribute("plans", membershipPlanRepository.findByActiveTrue());
        return "membership";
    }

    @GetMapping("/payment/membership")
    public String showMembershipPaymentPage(@RequestParam Long planId, HttpSession session, Model model) {
        if (!isCustomer(session))
            return "redirect:/login";

        com.footballsystem.model.MembershipPlan plan = membershipPlanRepository.findById(planId).orElse(null);
        if (plan == null) return "redirect:/membership";

        model.addAttribute("plan", plan);
        return "payment-membership";
    }

    @PostMapping("/payment/membership/process")
    public String processMembershipPayment(@RequestParam Long planId, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!isCustomer(session))
            return "redirect:/login";

        User user = (User) session.getAttribute("loggedUser");
        if (user != null) {
            Optional<User> dbUserOpt = userRepository.findById(user.getUserId());
            com.footballsystem.model.MembershipPlan plan = membershipPlanRepository.findById(planId).orElse(null);
            
            if (dbUserOpt.isPresent() && plan != null) {
                User dbUser = dbUserOpt.get();

                // --- CREATE PENDING SUBSCRIPTION TRANSACTION RECORD ---
                Booking vipTransaction = new Booking();
                vipTransaction.setUser(dbUser);
                vipTransaction.setPrice(plan.getPrice() != null ? plan.getPrice() : 0.0);
                vipTransaction.setStatus("PENDING");
                vipTransaction.setPaymentStatus("PENDING_MEMBERSHIP_" + plan.getId());
                vipTransaction.setDate(java.time.LocalDate.now());
                vipTransaction.setStartTime(java.time.LocalTime.now());
                vipTransaction.setEndTime(java.time.LocalTime.now());
                vipTransaction.setCreatedAt(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kuala_Lumpur")));

                bookingRepository.save(vipTransaction);

                // Store in session for use by return/callback
                session.setAttribute("pendingPaymentType", "MEMBERSHIP");
                session.setAttribute("pendingBookingId", vipTransaction.getBookingId());

                try {
                    String redirectUrl = toyyibPayService.createBillAndGetUrl(vipTransaction, "MEMBERSHIP", vipTransaction.getPrice());
                    System.out.println("Redirecting customer to ToyyibPay for membership: " + redirectUrl);
                    return "redirect:" + redirectUrl;
                } catch (Exception e) {
                    System.err.println("ToyyibPay membership bill creation failed: " + e.getMessage());
                    redirectAttributes.addFlashAttribute("error", "Payment gateway error. Please try again.");
                    return "redirect:/membership";
                }
            }
        }
        return "redirect:/membership";
    }

    @PostMapping("/subscribe-vip")
    public String subscribeVip(@RequestParam Long planId, HttpSession session) {
        return "redirect:/payment/membership?planId=" + planId;
    }

    // CONTACT US PAGE
    @GetMapping("/contact")
    public String showContactPage(HttpSession session, Model model) {
        if (!isCustomer(session))
            return "redirect:/login";
        model.addAttribute("branches", branchRepository.findAll());
        return "contact";
    }
}
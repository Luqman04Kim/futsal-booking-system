package com.footballsystem.controller;

import com.footballsystem.model.*;
import com.footballsystem.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;

@Controller
@RequestMapping("/staff")
public class StaffController {

    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private ProblemReportRepository problemReportRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BranchRepository branchRepository;
    @Autowired
    private FieldRepository fieldRepository;
    @Autowired
    private ReportActivityRepository reportActivityRepository;

    // Helper: Check if user is Staff
    private boolean isStaff(HttpSession session) {
        String role = (String) session.getAttribute("role");
        return role != null && role.equalsIgnoreCase("STAFF");
    }

    // Helper: Get project root dynamically
    private Path getProjectRoot() {
        try {
            String classPath = getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
            String decodedPath = java.net.URLDecoder.decode(classPath, "UTF-8");
            Path path = Paths.get(decodedPath);
            while (path != null) {
                if (Files.exists(path.resolve("pom.xml"))) {
                    return path;
                }
                path = path.getParent();
            }
        } catch (Exception e) {
            // Ignore
        }
        Path cwd = Paths.get("").toAbsolutePath();
        if (Files.exists(cwd.resolve("FYP3").resolve("pom.xml"))) {
            return cwd.resolve("FYP3");
        }
        return cwd;
    }

    // Helper: Save Image
    private String saveImage(MultipartFile file, String prefix) {
        if (file.isEmpty())
            return null;
        try {
            // Read file bytes ONCE
            byte[] fileBytes = file.getBytes();

            Path projectRoot = getProjectRoot();
            Path uploadPath = projectRoot.resolve("src/main/resources/static/img");
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            String fileName = prefix + "_" + UUID.randomUUID().toString() + ".png";
            Path path = uploadPath.resolve(fileName);
            Files.write(path, fileBytes);

            // Copy to target for immediate display
            Path targetPath = projectRoot.resolve("target/classes/static/img").resolve(fileName);
            if (!Files.exists(targetPath.getParent()))
                Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, fileBytes);

            return "/img/" + fileName;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // --- 1. DASHBOARD (View Tasks) ---
    @GetMapping("/dashboard")
    public String showDashboard(HttpSession session, Model model) {
        try {
            if (!isStaff(session))
                return "redirect:/login";

            User user = (User) session.getAttribute("loggedUser");
            // Reload user to ensure we have the latest data and attached entity
            if (user != null) {
                user = userRepository.findById(user.getUserId()).orElse(null);
            }

            // Get tasks assigned to the logged-in staff
            List<Task> tasks = taskRepository.findByAssignedStaff(user);

            model.addAttribute("myTasks", tasks); // Fixed: changed "tasks" to "myTasks"
            model.addAttribute("user", user); // Pass user to display name etc.

            // --- NEW: Top 5 Staff Leaderboard Logic (Same as Manager) ---
            List<User> allStaff = userRepository.findAll().stream()
                    .filter(u -> "STAFF".equals(u.getRole()))
                    .collect(Collectors.toList());

            List<User> topStaff = allStaff.stream()
                    .sorted((u1, u2) -> {
                        long c1 = u1.getTasks() != null
                                ? u1.getTasks().stream().filter(t -> "COMPLETED".equalsIgnoreCase(t.getStatus()))
                                        .count()
                                : 0;
                        long c2 = u2.getTasks() != null
                                ? u2.getTasks().stream().filter(t -> "COMPLETED".equalsIgnoreCase(t.getStatus()))
                                        .count()
                                : 0;
                        return Long.compare(c2, c1);
                    })
                    .limit(5)
                    .collect(Collectors.toList());
            model.addAttribute("topStaff", topStaff);

            Map<Long, Long> staffTaskCounts = new HashMap<>();
            Map<Long, Integer> staffTotalTaskCounts = new HashMap<>();
            Map<Long, Integer> staffTaskPercentages = new HashMap<>();

            for (User u : topStaff) {
                long count = u.getTasks() != null
                        ? u.getTasks().stream().filter(t -> "COMPLETED".equalsIgnoreCase(t.getStatus())).count()
                        : 0;
                int total = u.getTasks() != null ? u.getTasks().size() : 0;
                int percentage = (total > 0) ? (int) ((count * 100) / total) : 0;

                staffTaskCounts.put(u.getUserId(), count);
                staffTotalTaskCounts.put(u.getUserId(), total);
                staffTaskPercentages.put(u.getUserId(), percentage);
            }
            model.addAttribute("staffTaskCounts", staffTaskCounts);
            model.addAttribute("staffTotalTaskCounts", staffTotalTaskCounts);
            model.addAttribute("staffTaskPercentages", staffTaskPercentages);
            // ---------------------------------------------------------

            return "staff-dashboard";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "An error occurred: " + e.getMessage());
            return "error"; // Or a specific error page
        }
    }

    @PostMapping("/task/complete/{id}")
    public String completeTask(@PathVariable Long id,
            @RequestParam(required = false) String completionNote,
            @RequestParam(value = "proofImages", required = false) List<MultipartFile> proofImages,
            HttpSession session) {
        if (!isStaff(session))
            return "redirect:/login";

        Task task = taskRepository.findById(id).orElse(null);
        if (task != null) {
            task.setStatus("Completed");
            task.setCompletedAt(LocalDateTime.now());

            if (completionNote != null && !completionNote.trim().isEmpty()) {
                task.setCompletionNote(completionNote);
            }

            // Save up to 5 proof images
            String[] savedUrls = new String[5];
            int saved = 0;
            if (proofImages != null) {
                for (MultipartFile img : proofImages) {
                    if (saved >= 5) break;
                    if (img != null && !img.isEmpty()) {
                        String url = saveImage(img, "proof");
                        if (url != null) {
                            savedUrls[saved] = url;
                            switch (saved) {
                                case 0: task.setProofImageUrl(url); break;
                                case 1: task.setProofImageUrl2(url); break;
                                case 2: task.setProofImageUrl3(url); break;
                                case 3: task.setProofImageUrl4(url); break;
                                case 4: task.setProofImageUrl5(url); break;
                            }
                            saved++;
                        }
                    }
                }
            }

            taskRepository.save(task);

            // Auto-complete all sibling tasks in the same group
            if (task.getTaskGroupId() != null && !task.getTaskGroupId().isEmpty()) {
                List<Task> siblingTasks = taskRepository.findByTaskGroupId(task.getTaskGroupId());
                for (Task sibling : siblingTasks) {
                    if (!sibling.getTaskId().equals(task.getTaskId()) && !"Completed".equalsIgnoreCase(sibling.getStatus())) {
                        sibling.setStatus("Completed");
                        sibling.setCompletedAt(LocalDateTime.now());
                        sibling.setCompletionNote(task.getCompletionNote());
                        // Copy proof images to sibling
                        sibling.setProofImageUrl(task.getProofImageUrl());
                        sibling.setProofImageUrl2(task.getProofImageUrl2());
                        sibling.setProofImageUrl3(task.getProofImageUrl3());
                        sibling.setProofImageUrl4(task.getProofImageUrl4());
                        sibling.setProofImageUrl5(task.getProofImageUrl5());
                        taskRepository.save(sibling);
                    }
                }
            }

            // Auto-update linked report to REVIEW status
            ProblemReport report = task.getProblemReport();
            if (report != null) {
                User user = (User) session.getAttribute("loggedUser");
                String staffName = user != null ? user.getUsername() : "Staff";

                // Log: staff completed the work
                reportActivityRepository.save(new ReportActivity(report,
                        "Staff " + staffName + " completed the repair work", staffName));

                // Change report status to REVIEW
                report.setStatus("REVIEW");
                problemReportRepository.save(report);

                reportActivityRepository.save(new ReportActivity(report,
                        "Status updated to Review (pending manager verification)", staffName));
            }
        }
        return "redirect:/staff/dashboard";
    }

    @GetMapping("/task/delete/{id}")
    public String deleteTask(@PathVariable Long id, HttpSession session) {
        if (!isStaff(session))
            return "redirect:/login";

        try {
            Task task = taskRepository.findById(id).orElse(null);
            if (task != null) {
                // Remove task from User's tasks list to avoid cascade issues
                User assignedStaff = task.getAssignedStaff();
                if (assignedStaff != null) {
                    assignedStaff = userRepository.findById(assignedStaff.getUserId()).orElse(null);
                    if (assignedStaff != null && assignedStaff.getTasks() != null) {
                        assignedStaff.getTasks().removeIf(t -> t.getTaskId().equals(id));
                        userRepository.save(assignedStaff);
                    }
                    // Clear the relationship from task side
                    task.setAssignedStaff(null);
                    taskRepository.save(task);
                }

                // Now delete the task
                taskRepository.deleteById(id);
                System.out.println("Task deleted successfully: ID " + id);
            } else {
                System.out.println("Task not found: ID " + id);
            }
        } catch (Exception e) {
            System.err.println("Error deleting task ID " + id + ": " + e.getMessage());
            e.printStackTrace();
        }
        return "redirect:/staff/dashboard?deleted=true";
    }

    // --- 2. REPORTS (View Problem Reports in Staff's Branch) ---
    @GetMapping("/reports")
    public String showReports(@RequestParam(required = false) String search,
            @RequestParam(required = false) Long branchId,
            HttpSession session, Model model) {
        if (!isStaff(session))
            return "redirect:/login";

        User user = (User) session.getAttribute("loggedUser");
        if (user != null) {
            user = userRepository.findById(user.getUserId()).orElse(null);
        }
        if (user == null) return "redirect:/login";

        Branch staffBranch = user.getBranch();
        List<ProblemReport> reports = problemReportRepository.findAll();

        // Filter by staff's branch
        if (staffBranch != null) {
            final Long staffBranchId = staffBranch.getBranchId();
            reports = reports.stream()
                    .filter(r -> r.getBranch() != null && r.getBranch().getBranchId().equals(staffBranchId))
                    .collect(Collectors.toList());
        }

        // Search by ID (e.g., "R1" or "1")
        if (search != null && !search.isEmpty()) {
            String searchClean = search.toUpperCase().replace("R", "");
            try {
                Long searchId = Long.parseLong(searchClean);
                reports = reports.stream()
                        .filter(r -> r.getReportId().equals(searchId))
                        .collect(Collectors.toList());
            } catch (NumberFormatException e) {
                // Ignore if not a number
            }
        }

        // Sort by date descending
        reports.sort(Comparator.comparing(ProblemReport::getTimestamp).reversed());

        model.addAttribute("reports", reports);
        model.addAttribute("staffBranch", staffBranch);
        // Only show fields from staff's branch
        if (staffBranch != null) {
            final Long staffBranchId = staffBranch.getBranchId();
            List<Field> branchFields = fieldRepository.findAll().stream()
                    .filter(f -> f.getBranch() != null && f.getBranch().getBranchId().equals(staffBranchId))
                    .collect(Collectors.toList());
            model.addAttribute("fields", branchFields);
        } else {
            model.addAttribute("fields", List.of());
        }
        return "staff-reports";
    }

    @PostMapping("/submit-report")
    public String submitReport(@RequestParam String description,
            @RequestParam Long fieldId,
            @RequestParam(value = "photos", required = false) java.util.List<MultipartFile> photos,
            HttpSession session) {
        if (!isStaff(session))
            return "redirect:/login";

        User user = (User) session.getAttribute("loggedUser");
        Field field = fieldRepository.findById(fieldId).orElse(null);

        ProblemReport report = new ProblemReport();
        report.setDescription(description);
        report.setStatus("REPORTED");
        report.setTimestamp(LocalDateTime.now());
        report.setReporter(user);
        report.setField(field);
        if (field != null)
            report.setBranch(field.getBranch());

        // Save up to 5 photos
        if (photos != null) {
            String[] savedUrls = new String[5];
            int saved = 0;
            for (MultipartFile photo : photos) {
                if (saved >= 5)
                    break;
                if (photo != null && !photo.isEmpty()) {
                    String url = saveImage(photo, "report");
                    if (url != null)
                        savedUrls[saved++] = url;
                }
            }
            if (saved > 0)
                report.setImageUrl(savedUrls[0]);
            if (saved > 1)
                report.setImageUrl2(savedUrls[1]);
            if (saved > 2)
                report.setImageUrl3(savedUrls[2]);
            if (saved > 3)
                report.setImageUrl4(savedUrls[3]);
            if (saved > 4)
                report.setImageUrl5(savedUrls[4]);
        }

        problemReportRepository.save(report);

        // Log activity: Report submitted by staff
        String staffName = user != null ? user.getUsername() : "Staff";
        reportActivityRepository.save(new ReportActivity(report,
                "Report submitted by " + staffName, staffName));

        // Mark field as under Maintenance
        if (field != null) {
            field.setStatus("Maintenance");
            fieldRepository.save(field);
        }

        return "redirect:/staff/reports?success=Report Submitted";
    }

    @GetMapping("/report/delete/{id}")
    public String deleteReport(@PathVariable Long id, HttpSession session) {
        if (!isStaff(session))
            return "redirect:/login";

        ProblemReport report = problemReportRepository.findById(id).orElse(null);
        if (report != null) {
            Field field = report.getField();
            problemReportRepository.delete(report);

            // Check if there are other active reports for this field
            if (field != null) {
                boolean hasActiveReports = problemReportRepository.findAll().stream()
                        .anyMatch(r -> r.getField() != null
                                && r.getField().getFieldId().equals(field.getFieldId())
                                && !r.getStatus().equalsIgnoreCase("FIXED"));

                if (!hasActiveReports) {
                    field.setStatus("Available");
                    fieldRepository.save(field);
                }
            }
        }
        return "redirect:/staff/reports?deleted=true";
    }

    // --- 3. MANAGE REPORTS (Update Status) ---
    @PostMapping("/report/update-status")
    public String updateReportStatus(@RequestParam Long reportId, @RequestParam String status,
            @RequestParam(required = false) Long assignedToUserId,
            HttpSession session) {
        if (!isStaff(session))
            return "redirect:/login";

        ProblemReport report = problemReportRepository.findById(reportId).orElse(null);
        if (report != null) {
            report.setStatus(status);
            problemReportRepository.save(report);

            // If status is "FIXED", update field status to "Available"
            if ("FIXED".equalsIgnoreCase(status) && report.getField() != null) {
                Field field = report.getField();
                // Check if there are other active reports for this field
                boolean hasActiveReports = problemReportRepository.findAll().stream()
                        .anyMatch(r -> r.getField() != null
                                && r.getField().getFieldId().equals(field.getFieldId())
                                && !r.getStatus().equalsIgnoreCase("FIXED")
                                && !r.getReportId().equals(report.getReportId())); // Exclude current report

                if (!hasActiveReports) {
                    field.setStatus("Available");
                    fieldRepository.save(field);
                }
            }

            // If status is "IN PROGRESS" and assignedToUserId is provided, create a task
            if ("IN PROGRESS".equalsIgnoreCase(status) && assignedToUserId != null) {
                User assignedTo = userRepository.findById(assignedToUserId).orElse(null);
                if (assignedTo != null) {
                    Task newTask = new Task();
                    newTask.setDescription("Fix problem report: " + report.getDescription());
                    newTask.setCreationDate(LocalDateTime.now());
                    newTask.setStatus("Pending");
                    newTask.setAssignedStaff(assignedTo);
                    newTask.setProblemReport(report); // Link task to report
                    taskRepository.save(newTask);
                }
            }
        }
        return "redirect:/staff/reports";
    }

    // --- 4. MAINTENANCE LOG (History - Branch Level) ---
    @GetMapping("/maintenance")
    public String showMaintenanceLog(@RequestParam(required = false) String search,
            @RequestParam(required = false) Long branchId,
            HttpSession session, Model model) {
        if (!isStaff(session))
            return "redirect:/login";
        User user = (User) session.getAttribute("loggedUser");
        if (user == null)
            return "redirect:/login";
        // Reload from DB to get fresh data
        user = userRepository.findById(user.getUserId()).orElse(null);
        if (user == null)
            return "redirect:/login";

        Branch staffBranch = user.getBranch();
        List<ProblemReport> myReports = problemReportRepository.findAll();

        // Filter by staff's branch
        if (staffBranch != null) {
            final Long staffBranchId = staffBranch.getBranchId();
            myReports = myReports.stream()
                    .filter(r -> r.getBranch() != null && r.getBranch().getBranchId().equals(staffBranchId))
                    .collect(Collectors.toList());
        }

        // Search by ID (e.g., "M1" or "1")
        if (search != null && !search.isEmpty()) {
            String searchClean = search.toUpperCase().replace("M", "");
            try {
                Long searchId = Long.parseLong(searchClean);
                myReports = myReports.stream()
                        .filter(r -> r.getReportId().equals(searchId))
                        .collect(Collectors.toList());
            } catch (NumberFormatException e) {
                // Ignore if not a number
            }
        }

        // Sort by date descending
        myReports.sort(Comparator.comparing(ProblemReport::getTimestamp).reversed());

        model.addAttribute("reports", myReports);
        model.addAttribute("staffBranch", staffBranch);
        // Only show fields from staff's branch
        if (staffBranch != null) {
            final Long staffBranchId = staffBranch.getBranchId();
            List<Field> branchFields = fieldRepository.findAll().stream()
                    .filter(f -> f.getBranch() != null && f.getBranch().getBranchId().equals(staffBranchId))
                    .collect(Collectors.toList());
            model.addAttribute("fields", branchFields);
        } else {
            model.addAttribute("fields", List.of());
        }
        return "staff-maintenance";
    }

    @PostMapping("/add-maintenance")
    public String addMaintenanceReport(
            @RequestParam String description,
            @RequestParam(required = false) Long fieldId,
            @RequestParam(value = "photos", required = false) List<MultipartFile> photos,
            HttpSession session,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (!isStaff(session))
            return "redirect:/login";

        User user = (User) session.getAttribute("loggedUser");
        Field field = (fieldId != null) ? fieldRepository.findById(fieldId).orElse(null) : null;

        ProblemReport report = new ProblemReport();
        report.setDescription(description);
        report.setStatus("REPORTED");
        report.setTimestamp(LocalDateTime.now());
        report.setReporter(user);
        report.setField(field);
        if (field != null)
            report.setBranch(field.getBranch());

        // Save up to 5 photos
        if (photos != null) {
            String[] savedUrls = new String[5];
            int saved = 0;
            for (MultipartFile photo : photos) {
                if (saved >= 5)
                    break;
                if (photo != null && !photo.isEmpty()) {
                    String url = saveImage(photo, "maint");
                    if (url != null)
                        savedUrls[saved++] = url;
                }
            }
            if (saved > 0)
                report.setImageUrl(savedUrls[0]);
            if (saved > 1)
                report.setImageUrl2(savedUrls[1]);
            if (saved > 2)
                report.setImageUrl3(savedUrls[2]);
            if (saved > 3)
                report.setImageUrl4(savedUrls[3]);
            if (saved > 4)
                report.setImageUrl5(savedUrls[4]);
        }

        // Mark field as under Maintenance
        if (field != null) {
            field.setStatus("Maintenance");
            fieldRepository.save(field);
        }

        problemReportRepository.save(report);
        redirectAttributes.addFlashAttribute("success", "Maintenance report submitted successfully!");
        return "redirect:/staff/maintenance";
    }

    @GetMapping("/maintenance/delete/{id}")
    public String deleteMaintenance(@PathVariable Long id, HttpSession session) {
        if (!isStaff(session))
            return "redirect:/login";

        ProblemReport report = problemReportRepository.findById(id).orElse(null);
        if (report != null) {
            Field field = report.getField();
            problemReportRepository.delete(report);

            // Check if there are other active reports for this field
            if (field != null) {
                boolean hasActiveReports = problemReportRepository.findAll().stream()
                        .anyMatch(r -> r.getField() != null
                                && r.getField().getFieldId().equals(field.getFieldId())
                                && !r.getStatus().equalsIgnoreCase("FIXED"));

                if (!hasActiveReports) {
                    field.setStatus("Available");
                    fieldRepository.save(field);
                }
            }
        }
        return "redirect:/staff/maintenance?deleted=true";
    }

    // --- 5. MATCH SCHEDULE ---
    @GetMapping("/schedule")
    public String showSchedule(HttpSession session, Model model) {
        if (!isStaff(session))
            return "redirect:/login";
        User user = (User) session.getAttribute("loggedUser");
        if (user != null) {
            user = userRepository.findById(user.getUserId()).orElse(null);
        }

        // Show bookings only for the branch this staff belongs to
        List<Booking> branchBookings;
        if (user != null && user.getBranch() != null) {
            Long branchId = user.getBranch().getBranchId();
            branchBookings = bookingRepository.findAll().stream()
                    .filter(b -> b.getField().getBranch().getBranchId().equals(branchId)
                            && ("APPROVED".equals(b.getStatus()) || "PENDING".equals(b.getStatus())))
                    .toList();
        } else {
            // If staff has no branch, show none or all (choosing none for safety)
            branchBookings = List.of();
        }

        model.addAttribute("bookings", branchBookings);
        return "staff-schedule";
    }

    // --- 6. BOOKING REQUESTS (If staff can approve) ---
    @GetMapping("/requests")
    public String showRequests(HttpSession session, Model model) {
        if (!isStaff(session))
            return "redirect:/login";
        // Reuse logic from schedule but filter only PENDING
        // ... (implementation depends if staff can approve)
        return "redirect:/staff/schedule";
    }

    // --- 7. BOOKINGS (Read-Only/Manage) ---
    @GetMapping("/bookings")
    public String showBookingsPage(@RequestParam(required = false) String search,
            @RequestParam(required = false) Long branchId,
            HttpSession session, Model model) {
        if (!isStaff(session))
            return "redirect:/login";

        List<Booking> bookings = bookingRepository.findAll().stream()
                .filter(b -> b.getField() != null) // Exclude VIP Membership transactions
                .collect(Collectors.toList());

        // Filter by Branch
        if (branchId != null) {
            bookings = bookings.stream()
                    .filter(b -> b.getField().getBranch() != null
                            && b.getField().getBranch().getBranchId().equals(branchId))
                    .collect(Collectors.toList());
            model.addAttribute("selectedBranchId", branchId);
        }

        // Search by ID or Username
        if (search != null && !search.isEmpty()) {
            bookings = bookings.stream()
                    .filter(b -> String.valueOf(b.getBookingId()).contains(search) ||
                            b.getUser().getUsername().toLowerCase().contains(search.toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Sort by date descending
        bookings.sort(Comparator.comparing(Booking::getDate).reversed());

        model.addAttribute("bookings", bookings);
        model.addAttribute("branches", branchRepository.findAll());
        return "booking-field-staff";
    }

    @GetMapping("/approve-booking/{id}")
    public String approveBooking(@PathVariable Long id, HttpSession session) {
        if (!isStaff(session))
            return "redirect:/login";
        Booking booking = bookingRepository.findById(id).orElse(null);
        if (booking != null) {
            booking.setStatus("APPROVED");
            bookingRepository.save(booking);
        }
        return "redirect:/staff/bookings";
    }

    @GetMapping("/reject-booking/{id}")
    public String rejectBooking(@PathVariable Long id, HttpSession session) {
        if (!isStaff(session))
            return "redirect:/login";
        Booking booking = bookingRepository.findById(id).orElse(null);
        if (booking != null) {
            booking.setStatus("REJECTED");
            bookingRepository.save(booking);
        }
        return "redirect:/staff/bookings";
    }

    @GetMapping("/delete-booking/{id}")
    public String deleteBooking(@PathVariable Long id, HttpSession session) {
        if (!isStaff(session))
            return "redirect:/login";
        bookingRepository.deleteById(id);
        return "redirect:/staff/bookings?deleted=true";
    }

    @PostMapping("/update-booking-result")
    public String updateBookingResult(@RequestParam Long bookingId, @RequestParam String result, HttpSession session) {
        if (!isStaff(session))
            return "redirect:/login";
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking != null) {
            booking.setResult(result);
            bookingRepository.save(booking);
        }
        return "redirect:/staff/bookings";
    }

    @PostMapping("/update-booking-photo")
    public String updateBookingPhoto(@RequestParam Long bookingId,
            @RequestParam(required = false) String photoLink,
            HttpSession session) {
        if (!isStaff(session))
            return "redirect:/login";
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking != null) {
            booking.setPhotoLink(photoLink != null && !photoLink.trim().isEmpty() ? photoLink.trim() : null);
            bookingRepository.save(booking);
        }
        return "redirect:/staff/bookings";
    }

    // --- 8. PAYMENTS (Read-Only/Manage) ---
    @GetMapping("/payment-field")
    public String showPaymentFieldPage(@RequestParam(required = false) String search,
            @RequestParam(required = false) Long branchId,
            HttpSession session, Model model) {
        if (!isStaff(session))
            return "redirect:/login";

        List<Booking> bookings = bookingRepository.findAll().stream()
                .filter(b -> b.getField() != null)
                .collect(Collectors.toList());

        // Filter by Branch
        if (branchId != null) {
            bookings = bookings.stream()
                    .filter(b -> b.getField().getBranch() != null
                            && b.getField().getBranch().getBranchId().equals(branchId))
                    .collect(Collectors.toList());
            model.addAttribute("selectedBranchId", branchId);
        }

        // Search by ID or Username
        if (search != null && !search.isEmpty()) {
            bookings = bookings.stream()
                    .filter(b -> String.valueOf(b.getBookingId()).contains(search) ||
                            b.getUser().getUsername().toLowerCase().contains(search.toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Sort by date descending
        bookings.sort(Comparator.comparing(Booking::getDate).reversed());

        model.addAttribute("bookings", bookings);
        model.addAttribute("branches", branchRepository.findAll());
        return "payment-field-staff";
    }

    @PostMapping("/payment/update")
    public String updatePaymentStatus(@RequestParam Long bookingId, HttpSession session) {
        if (!isStaff(session))
            return "redirect:/login";
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking != null && "Pay Deposit".equals(booking.getPaymentStatus())) {
            booking.setPaymentStatus("Full Payment");
            bookingRepository.save(booking);
        }
        return "redirect:/staff/payment-field";
    }

    @GetMapping("/payment/delete/{id}")
    public String deletePayment(@PathVariable Long id, HttpSession session) {
        if (!isStaff(session))
            return "redirect:/login";
        bookingRepository.deleteById(id);
        return "redirect:/staff/payment-field?deleted=true";
    }

    @GetMapping("/attendance")
    public String showAttendance(HttpSession session, Model model) {
        if (!isStaff(session))
            return "redirect:/login";

        User user = (User) session.getAttribute("loggedUser");
        if (user != null) {
            user = userRepository.findById(user.getUserId()).orElse(null);
        }

        List<Booking> attendances;
        if (user != null && user.getBranch() != null) {
            attendances = bookingRepository.findByField_Branch_BranchId(user.getBranch().getBranchId()).stream()
                    .filter(Booking::isCheckedIn)
                    .sorted((b1, b2) -> {
                        if (b1.getCheckedInAt() == null && b2.getCheckedInAt() == null) return 0;
                        if (b1.getCheckedInAt() == null) return 1;
                        if (b2.getCheckedInAt() == null) return -1;
                        return b2.getCheckedInAt().compareTo(b1.getCheckedInAt());
                    })
                    .collect(Collectors.toList());
        } else {
            attendances = bookingRepository.findAll().stream()
                    .filter(Booking::isCheckedIn)
                    .sorted((b1, b2) -> {
                        if (b1.getCheckedInAt() == null && b2.getCheckedInAt() == null) return 0;
                        if (b1.getCheckedInAt() == null) return 1;
                        if (b2.getCheckedInAt() == null) return -1;
                        return b2.getCheckedInAt().compareTo(b1.getCheckedInAt());
                    })
                    .collect(Collectors.toList());
        }

        model.addAttribute("attendances", attendances);
        model.addAttribute("user", user);
        return "staff-attendance";
    }
}

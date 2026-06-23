package com.footballsystem.controller;

import com.footballsystem.model.*;
import com.footballsystem.repository.*;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.time.temporal.TemporalAdjusters;

@Controller
@RequestMapping("/manager")
public class ManagerController {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FieldRepository fieldRepository;
    @Autowired
    private BranchRepository branchRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private ProblemReportRepository problemReportRepository;
    @Autowired
    private ReportActivityRepository reportActivityRepository;
    @Autowired
    private com.footballsystem.repository.MembershipPlanRepository membershipPlanRepository;
    @Autowired
    private com.footballsystem.repository.InventoryItemRepository inventoryItemRepository;
    @Autowired
    private com.footballsystem.repository.SystemSettingRepository systemSettingRepository;

    // Upload directory — set via application.properties (upload.dir)
    // Defaults to a folder called "uploads" next to the running JAR
    @Value("${upload.dir:uploads}")
    private String uploadDirProperty;

    // Helper: Check if user is Manager
    private boolean isManager(HttpSession session) {
        String role = (String) session.getAttribute("role");
        return role != null && role.equalsIgnoreCase("MANAGER");
    }

    // Helper: Resolve the absolute upload directory
    private Path getUploadDir() throws Exception {
        Path dir = Paths.get(uploadDirProperty).toAbsolutePath();
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        return dir;
    }

    // Helper: Save Image to external upload directory
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

    // Helper: Save up to 5 field images
    private void saveFieldImages(List<MultipartFile> files, Field field) {
        if (files == null)
            return;
        String[] slots = new String[5];
        int saved = 0;
        for (MultipartFile f : files) {
            if (saved >= 5)
                break;
            if (f != null && !f.isEmpty()) {
                String url = saveImage(f, "field");
                if (url != null)
                    slots[saved++] = url;
            }
        }
        if (saved > 0)
            field.setImageUrl(slots[0]);
        if (saved > 1)
            field.setImageUrl2(slots[1]);
        if (saved > 2)
            field.setImageUrl3(slots[2]);
        if (saved > 3)
            field.setImageUrl4(slots[3]);
        if (saved > 4)
            field.setImageUrl5(slots[4]);
    }

    // Helper: Generate Staff ID (S001, S002, S003...)
    private String generateStaffId() {
        List<User> allUsers = userRepository.findAll();
        int maxNumber = 0;

        for (User user : allUsers) {
            String staffId = user.getStaffId();
            if (staffId != null && staffId.startsWith("S") && staffId.length() == 4) {
                try {
                    int number = Integer.parseInt(staffId.substring(1));
                    if (number > maxNumber) {
                        maxNumber = number;
                    }
                } catch (NumberFormatException e) {
                    // Skip invalid IDs
                }
            }
        }

        return String.format("S%03d", maxNumber + 1);
    }

    // Helper: Generate Manager ID (M001, M002, M003...)
    private String generateManagerId() {
        List<User> allUsers = userRepository.findAll();
        int maxNumber = 0;

        for (User user : allUsers) {
            String staffId = user.getStaffId();
            if (staffId != null && staffId.startsWith("M") && staffId.length() == 4) {
                try {
                    int number = Integer.parseInt(staffId.substring(1));
                    if (number > maxNumber) {
                        maxNumber = number;
                    }
                } catch (NumberFormatException e) {
                    // Skip invalid IDs
                }
            }
        }

        return String.format("M%03d", maxNumber + 1);
    }

    // =========================
    // API FOR VALIDATION (AJAX)
    // =========================
    @GetMapping("/api/check-email")
    @ResponseBody
    public boolean checkEmail(@RequestParam String email) {
        return userRepository.findByEmail(email.trim().toLowerCase()).isPresent();
    }

    @GetMapping("/api/check-branch")
    @ResponseBody
    public boolean checkBranch(@RequestParam String name) {
        return branchRepository.findAll().stream()
                .anyMatch(b -> b.getName().equalsIgnoreCase(name.trim()));
    }

    @GetMapping("/api/next-staff-id")
    @ResponseBody
    public Map<String, String> getNextStaffId() {
        return Collections.singletonMap("nextId", generateStaffId());
    }

    @GetMapping("/api/next-manager-id")
    @ResponseBody
    public Map<String, String> getNextManagerId() {
        return Collections.singletonMap("nextId", generateManagerId());
    }

    // =========================
    // DASHBOARD
    // =========================
    @GetMapping("/dashboard")
    public String showDashboard(HttpSession session, Model model) {
        if (!isManager(session))
            return "redirect:/login";

        long totalUsers = userRepository.count();
        long totalFields = fieldRepository.count();
        long totalBranches = branchRepository.count();
        long totalBookings = bookingRepository.count();

        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("managerName", session.getAttribute("username"));
        model.addAttribute("totalFields", totalFields);
        model.addAttribute("totalBranches", totalBranches);
        model.addAttribute("totalBookings", totalBookings);
        model.addAttribute("totalVisits", totalUsers * 5 + 120);

        List<Booking> allBookings = bookingRepository.findAll();
        List<Booking> approvedBookings = allBookings.stream()
                .filter(b -> "APPROVED".equalsIgnoreCase(b.getStatus()) || "COMPLETED".equalsIgnoreCase(b.getStatus()))
                .collect(Collectors.toList());

        double revenue = approvedBookings.stream()
                .mapToDouble(b -> b.getPrice() != null ? b.getPrice() : 0.0)
                .sum();
        model.addAttribute("totalRevenue", revenue);

        List<Booking> recentBookings = allBookings.stream()
                .sorted(Comparator.comparing(Booking::getDate).reversed())
                .limit(5)
                .collect(Collectors.toList());
        model.addAttribute("recentBookings", recentBookings);

        Map<String, Double> dailyRevenue = new LinkedHashMap<>();
        Map<String, Double> weeklyRevenue = new LinkedHashMap<>();
        Map<String, Double> monthlyRevenue = new LinkedHashMap<>();

        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            dailyRevenue.put(today.minusDays(i).getDayOfWeek().toString(), 0.0);
        }

        for (Booking b : approvedBookings) {
            if (b.getDate() == null) continue;
            double amount = b.getPrice() != null ? b.getPrice() : 0.0;

            String dayKey = b.getDate().getDayOfWeek().toString();
            if (dailyRevenue.containsKey(dayKey)) {
                dailyRevenue.put(dayKey, dailyRevenue.get(dayKey) + amount);
            }

            weeklyRevenue.put("Week " + (b.getDate().getDayOfMonth() / 7 + 1),
                    weeklyRevenue.getOrDefault("Week " + (b.getDate().getDayOfMonth() / 7 + 1), 0.0) + amount);

            monthlyRevenue.put(b.getDate().getMonth().toString(),
                    monthlyRevenue.getOrDefault(b.getDate().getMonth().toString(), 0.0) + amount);
        }

        model.addAttribute("dailyRevenue", dailyRevenue);
        model.addAttribute("weeklyRevenue", weeklyRevenue);
        model.addAttribute("monthlyRevenue", monthlyRevenue);

        Map<String, Long> branchBookings = allBookings.stream()
                .filter(b -> b.getField() != null)
                .collect(Collectors.groupingBy(b -> b.getField().getBranch().getName(), Collectors.counting()));
        model.addAttribute("branchBookingCounts", branchBookings);

        long managerCount = userRepository.findAll().stream().filter(u -> "MANAGER".equals(u.getRole())).count();
        long staffCount = userRepository.findAll().stream().filter(u -> "STAFF".equals(u.getRole())).count();
        long custCount = totalUsers - managerCount - staffCount;

        model.addAttribute("managerPct", totalUsers > 0 ? (managerCount * 100 / totalUsers) : 0);
        model.addAttribute("staffPct", totalUsers > 0 ? (staffCount * 100 / totalUsers) : 0);
        model.addAttribute("custPct", totalUsers > 0 ? (custCount * 100 / totalUsers) : 0);

        Map<String, Long> fieldUsage = allBookings.stream()
                .filter(b -> b.getField() != null)
                .collect(Collectors.groupingBy(b -> b.getField().getName(), Collectors.counting()));
        model.addAttribute("fieldUsage", fieldUsage);

        List<User> allStaff = userRepository.findAll().stream()
                .filter(u -> "STAFF".equals(u.getRole()))
                .collect(Collectors.toList());

        List<User> topStaff = allStaff.stream()
                .sorted((u1, u2) -> {
                    long c1 = u1.getTasks() != null
                            ? u1.getTasks().stream().filter(t -> "COMPLETED".equalsIgnoreCase(t.getStatus())).count()
                            : 0;
                    long c2 = u2.getTasks() != null
                            ? u2.getTasks().stream().filter(t -> "COMPLETED".equalsIgnoreCase(t.getStatus())).count()
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

        List<User> allCustomers = userRepository.findAll().stream()
                .filter(u -> "CUSTOMER".equalsIgnoreCase(u.getRole()))
                .collect(Collectors.toList());

        Map<Long, Double> customerSpends = new HashMap<>();
        Map<Long, Long> customerBookingCounts = new HashMap<>();

        for (User customer : allCustomers) {
            double totalSpend = allBookings.stream()
                    .filter(b -> b.getUser().getUserId().equals(customer.getUserId()))
                    .mapToDouble(b -> b.getPrice() != null ? b.getPrice() : 0.0)
                    .sum();

            long bookingCount = allBookings.stream()
                    .filter(b -> b.getUser().getUserId().equals(customer.getUserId()) && b.getField() != null)
                    .count();

            customerSpends.put(customer.getUserId(), totalSpend);
            customerBookingCounts.put(customer.getUserId(), bookingCount);
        }

        List<User> topCustomers = allCustomers.stream()
                .sorted((c1, c2) -> Double.compare(
                        customerSpends.getOrDefault(c2.getUserId(), 0.0),
                        customerSpends.getOrDefault(c1.getUserId(), 0.0)))
                .limit(3)
                .collect(Collectors.toList());

        model.addAttribute("topCustomers", topCustomers);
        model.addAttribute("customerSpends", customerSpends);
        model.addAttribute("customerBookingCounts", customerBookingCounts);

        return "dashboard";
    }

    // =========================
    // FIELD MANAGEMENT
    // =========================
    @GetMapping("/fields")
    public String showFieldsPage(HttpSession session, Model model) {
        if (!isManager(session))
            return "redirect:/login";

        List<Field> fields = fieldRepository.findAll();
        Map<String, List<Field>> fieldsByBranch = fields.stream()
                .collect(Collectors.groupingBy(f -> f.getBranch() != null ? f.getBranch().getName() : "Unassigned"));

        model.addAttribute("fieldsByBranch", fieldsByBranch);
        model.addAttribute("branches", branchRepository.findAll());
        model.addAttribute("staffList", userRepository.findAll().stream()
                .filter(u -> "STAFF".equals(u.getRole()))
                .collect(Collectors.toList()));
        return "manager-fields";
    }

    @PostMapping("/add-field")
    public String addField(@RequestParam String name, @RequestParam String type,
            @RequestParam String size, @RequestParam String status,
            @RequestParam Long branchId,
            @RequestParam(required = false) Long supervisorId,
            @RequestParam(value = "fieldImages", required = false) List<MultipartFile> fieldImages,
            HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";

        Branch branch = branchRepository.findById(branchId).orElse(null);
        Field field = new Field(name, type, size, status, branch);

        if (supervisorId != null) {
            User supervisor = userRepository.findById(supervisorId).orElse(null);
            field.setSupervisor(supervisor);
        }

        saveFieldImages(fieldImages, field);

        fieldRepository.save(field);
        return "redirect:/manager/fields";
    }

    @PostMapping("/update-field")
    public String updateField(@RequestParam Long fieldId, @RequestParam String name,
            @RequestParam String type, @RequestParam String size,
            @RequestParam String status, @RequestParam Long branchId,
            @RequestParam(required = false) Long supervisorId,
            @RequestParam(value = "fieldImages", required = false) List<MultipartFile> fieldImages,
            @RequestParam(value = "existingImageUrl", required = false) String existingImageUrl,
            HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";

        Field field = fieldRepository.findById(fieldId).orElse(null);
        if (field != null) {
            field.setName(name);
            field.setType(type);
            field.setSize(size);
            field.setStatus(status);
            Branch branch = branchRepository.findById(branchId).orElse(null);
            field.setBranch(branch);

            if (supervisorId != null) {
                User supervisor = userRepository.findById(supervisorId).orElse(null);
                field.setSupervisor(supervisor);
            } else {
                field.setSupervisor(null);
            }

            // Only update images if new ones were uploaded
            boolean hasNewImages = fieldImages != null && fieldImages.stream().anyMatch(f -> !f.isEmpty());
            if (hasNewImages) {
                saveFieldImages(fieldImages, field);
            } else if (existingImageUrl != null && !existingImageUrl.isBlank() && field.getImageUrl() == null) {
                // Restore from form if DB somehow lost it
                field.setImageUrl(existingImageUrl);
            }

            fieldRepository.save(field);
        }
        return "redirect:/manager/fields";
    }

    @GetMapping("/delete-field/{id}")
    public String deleteField(@PathVariable Long id, HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";
        fieldRepository.deleteById(id);
        return "redirect:/manager/fields?deleted=true";
    }

    // =========================
    // BRANCH MANAGEMENT
    // =========================
    @GetMapping("/branches")
    public String showBranchesPage(HttpSession session, Model model) {
        if (!isManager(session))
            return "redirect:/login";
        model.addAttribute("branches", branchRepository.findAll());
        model.addAttribute("staffList", userRepository.findAll().stream()
                .filter(u -> "STAFF".equals(u.getRole()))
                .collect(Collectors.toList()));
        return "branches";
    }

    @GetMapping("/api/branches/{branchId}/fields")
    @ResponseBody
    public List<Field> getFieldsByBranch(@PathVariable Long branchId) {
        return fieldRepository.findAll().stream()
                .filter(f -> f.getBranch() != null && f.getBranch().getBranchId().equals(branchId))
                .collect(Collectors.toList());
    }

    @PostMapping("/add-branch")
    public String addBranch(@RequestParam String name, @RequestParam String location,
            @RequestParam String contact,
            @RequestParam(required = false) Long managerId,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isManager(session))
            return "redirect:/login";

        // Check if branch name already exists (case-insensitive)
        boolean branchExists = branchRepository.findAll().stream()
                .anyMatch(b -> b.getName().equalsIgnoreCase(name.trim()));

        if (branchExists) {
            redirectAttributes.addFlashAttribute("error", "Branch name already exists!");
            return "redirect:/manager/branches";
        }

        // Validate phone number (numeric only)
        if (contact != null && !contact.isEmpty() && !contact.matches("[0-9]+")) {
            redirectAttributes.addFlashAttribute("error", "Contact number must contain only digits!");
            return "redirect:/manager/branches";
        }

        Branch branch = new Branch();
        branch.setName(name.trim());
        branch.setLocation(location);
        branch.setContactNumber(contact);

        if (managerId != null) {
            User manager = userRepository.findById(managerId).orElse(null);
            branch.setManager(manager);
        }

        if (latitude != null)
            branch.setLatitude(latitude);
        if (longitude != null)
            branch.setLongitude(longitude);

        if (imageFile != null && !imageFile.isEmpty()) {
            String imageUrl = saveImage(imageFile, "branch");
            if (imageUrl != null)
                branch.setImageUrl(imageUrl);
        }

        branchRepository.save(branch);
        redirectAttributes.addFlashAttribute("success", "Branch added successfully!");
        return "redirect:/manager/branches";
    }

    @PostMapping("/update-branch")
    public String updateBranch(@RequestParam Long branchId, @RequestParam String name,
            @RequestParam String location, @RequestParam String contact,
            @RequestParam(required = false) Long managerId,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestParam(value = "existingImageUrl", required = false) String existingImageUrl,
            HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";

        Branch branch = branchRepository.findById(branchId).orElse(null);
        if (branch != null) {
            branch.setName(name);
            branch.setLocation(location);
            branch.setContactNumber(contact);

            if (managerId != null) {
                User manager = userRepository.findById(managerId).orElse(null);
                branch.setManager(manager);
            } else {
                branch.setManager(null);
            }

            if (latitude != null)
                branch.setLatitude(latitude);
            if (longitude != null)
                branch.setLongitude(longitude);

            if (imageFile != null && !imageFile.isEmpty()) {
                // New image uploaded — save it
                String imageUrl = saveImage(imageFile, "branch");
                if (imageUrl != null)
                    branch.setImageUrl(imageUrl);
            } else if (existingImageUrl != null && !existingImageUrl.isBlank()) {
                // No new image — restore the existing URL passed from the form
                branch.setImageUrl(existingImageUrl);
            }
            // If both are null/empty, leave whatever the DB entity already has

            branchRepository.save(branch);
        }
        return "redirect:/manager/branches";
    }

    @GetMapping("/delete-branch/{id}")
    public String deleteBranch(@PathVariable Long id, HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";
        branchRepository.deleteById(id);
        return "redirect:/manager/branches?deleted=true";
    }

    // =========================
    // STAFF MANAGEMENT
    // =========================
    @GetMapping("/staff")
    public String showStaffPage(@RequestParam(required = false) String search,
            @RequestParam(required = false) String filter,
            HttpSession session, Model model) {
        if (!isManager(session))
            return "redirect:/login";

        List<User> allUsers = userRepository.findAll();
        List<User> staffList = allUsers.stream()
                .filter(u -> "STAFF".equals(u.getRole()) || "MANAGER".equals(u.getRole()))
                .collect(Collectors.toList());

        if (search != null && !search.isEmpty()) {
            staffList = staffList.stream()
                    .filter(u -> u.getUsername().toLowerCase().contains(search.toLowerCase()) ||
                            u.getEmail().toLowerCase().contains(search.toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (filter != null && !filter.isEmpty() && !filter.equals("ALL")) {
            if ("MANAGER".equalsIgnoreCase(filter)) {
                staffList = staffList.stream()
                        .filter(u -> "MANAGER".equalsIgnoreCase(u.getRole()))
                        .collect(Collectors.toList());
            } else if ("MAINTENANCE TEAM".equalsIgnoreCase(filter)) {
                staffList = staffList.stream()
                        .filter(u -> "MAINTENANCE TEAM".equalsIgnoreCase(u.getPosition()))
                        .collect(Collectors.toList());
            } else if ("OTHER".equalsIgnoreCase(filter)) {
                staffList = staffList.stream()
                        .filter(u -> "STAFF".equalsIgnoreCase(u.getRole()) &&
                                !"MANAGER".equalsIgnoreCase(u.getPosition()) &&
                                !"MAINTENANCE TEAM".equalsIgnoreCase(u.getPosition()))
                        .collect(Collectors.toList());
            }
        }

        staffList.sort(Comparator.comparing(User::getRole).thenComparing(User::getUsername));

        // Calculate Task Statistics
        Map<Long, Long> staffTaskCounts = new HashMap<>(); // Completed tasks
        Map<Long, Integer> staffTotalTaskCounts = new HashMap<>();
        Map<Long, Integer> staffTaskPercentages = new HashMap<>();

        for (User staff : staffList) {
            if (staff.getTasks() != null) {
                int total = staff.getTasks().size();
                long completed = staff.getTasks().stream()
                        .filter(t -> "COMPLETED".equalsIgnoreCase(t.getStatus()))
                        .count();

                staffTotalTaskCounts.put(staff.getUserId(), total);
                staffTaskCounts.put(staff.getUserId(), completed);

                if (total > 0) {
                    staffTaskPercentages.put(staff.getUserId(), (int) ((completed * 100) / total));
                } else {
                    staffTaskPercentages.put(staff.getUserId(), 0);
                }
            } else {
                staffTotalTaskCounts.put(staff.getUserId(), 0);
                staffTaskCounts.put(staff.getUserId(), 0L);
                staffTaskPercentages.put(staff.getUserId(), 0);
            }
        }

        model.addAttribute("staffList", staffList);
        model.addAttribute("branches", branchRepository.findAll());
        model.addAttribute("selectedFilter", filter != null ? filter : "ALL");
        model.addAttribute("staffTaskCounts", staffTaskCounts);
        model.addAttribute("staffTotalTaskCounts", staffTotalTaskCounts);
        model.addAttribute("staffTaskPercentages", staffTaskPercentages);

        return "staff";
    }

    @PostMapping("/add-staff")
    public String addStaff(@RequestParam String username, @RequestParam String email,
            @RequestParam String position,
            @RequestParam String password,
            @RequestParam(required = false) String staffId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isManager(session))
            return "redirect:/login";

        // Check if email already exists
        if (userRepository.findByEmail(email.trim().toLowerCase()).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Email already exists!");
            return "redirect:/manager/staff";
        }

        // Validate phone number (numeric only)
        if (phoneNumber != null && !phoneNumber.isEmpty() && !phoneNumber.matches("[0-9]+")) {
            redirectAttributes.addFlashAttribute("error", "Phone number must contain only digits!");
            return "redirect:/manager/staff";
        }

        // Auto-generate Staff ID (S001, S002, S003...)
        String autoStaffId = generateStaffId();

        User staff = new User();
        staff.setUsername(username);
        staff.setEmail(email.trim().toLowerCase());
        staff.setPassword(password);
        staff.setPosition(position);
        staff.setStaffId(autoStaffId); // Use auto-generated ID
        staff.setPhoneNumber(phoneNumber);
        staff.setRole("STAFF");

        if (branchId != null) {
            Branch branch = branchRepository.findById(branchId).orElse(null);
            staff.setBranch(branch);
        }

        if (imageFile != null && !imageFile.isEmpty()) {
            String imageUrl = saveImage(imageFile, "staff");
            if (imageUrl != null)
                staff.setImageUrl(imageUrl);
        }

        userRepository.save(staff);
        redirectAttributes.addFlashAttribute("success", "Staff added successfully with ID: " + autoStaffId);
        return "redirect:/manager/staff";
    }

    @PostMapping("/update-staff")
    public String updateStaff(@RequestParam Long userId, @RequestParam String username,
            @RequestParam String email, @RequestParam String position,
            @RequestParam(required = false) String staffId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) Integer age,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestParam(value = "existingImageUrl", required = false) String existingImageUrl,
            HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";

        User staff = userRepository.findById(userId).orElse(null);
        if (staff != null) {
            staff.setUsername(username);
            staff.setEmail(email);
            staff.setPosition(position);
            staff.setStaffId(staffId);
            staff.setPhoneNumber(phoneNumber);

            if (age != null) {
                staff.setAge(age);
            }

            if (branchId != null) {
                Branch branch = branchRepository.findById(branchId).orElse(null);
                staff.setBranch(branch);
            } else {
                staff.setBranch(null);
            }

            if (imageFile != null && !imageFile.isEmpty()) {
                // New image uploaded — save it
                String imageUrl = saveImage(imageFile, "staff");
                if (imageUrl != null)
                    staff.setImageUrl(imageUrl);
            } else if (existingImageUrl != null && !existingImageUrl.isBlank()) {
                // No new image — restore the existing URL passed from the form
                staff.setImageUrl(existingImageUrl);
            }
            // If both are null/empty, leave whatever the DB entity already has

            userRepository.save(staff);
        }
        return "redirect:/manager/staff";
    }

    @GetMapping("/delete-staff/{id}")
    public String deleteStaff(@PathVariable Long id, HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";
        userRepository.deleteById(id);
        return "redirect:/manager/staff?deleted=true";
    }

    // Add Manager Endpoint
    @PostMapping("/add-manager")
    public String addManager(@RequestParam String username, @RequestParam String email,
            @RequestParam String password,
            @RequestParam(required = false) Integer age,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isManager(session))
            return "redirect:/login";

        // Check if email already exists
        if (userRepository.findByEmail(email.trim().toLowerCase()).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Email already exists!");
            return "redirect:/manager/staff";
        }

        // Validate phone number (numeric only)
        if (phoneNumber != null && !phoneNumber.isEmpty() && !phoneNumber.matches("[0-9]+")) {
            redirectAttributes.addFlashAttribute("error", "Phone number must contain only digits!");
            return "redirect:/manager/staff";
        }

        // Auto-generate Manager ID (M001, M002, M003...)
        String autoManagerId = generateManagerId();

        User manager = new User();
        manager.setUsername(username);
        manager.setEmail(email.trim().toLowerCase());
        manager.setPassword(password);
        manager.setPosition("MANAGER");
        manager.setStaffId(autoManagerId); // Use auto-generated ID
        manager.setRole("MANAGER");
        manager.setAge(age);
        manager.setPhoneNumber(phoneNumber);

        if (imageFile != null && !imageFile.isEmpty()) {
            String imageUrl = saveImage(imageFile, "manager");
            if (imageUrl != null)
                manager.setImageUrl(imageUrl);
        } else {
            manager.setImageUrl("/img/undraw_profile.svg");
        }

        userRepository.save(manager);
        redirectAttributes.addFlashAttribute("success", "Manager added successfully with ID: " + autoManagerId);
        return "redirect:/manager/staff";
    }

    // =========================
    // USER MANAGEMENT
    // =========================
    @GetMapping("/users")
    public String showUsersPage(@RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            HttpSession session, Model model) {
        if (!isManager(session))
            return "redirect:/login";

        List<User> allUsers = userRepository.findAll();
        Stream<User> userStream = allUsers.stream();

        if (search != null && !search.isEmpty()) {
            String s = search.toLowerCase();
            userStream = userStream.filter(u -> u.getUsername().toLowerCase().contains(s) ||
                    u.getEmail().toLowerCase().contains(s));
        }

        if (role != null && !role.equals("ALL")) {
            if ("VIP".equals(role)) {
                userStream = userStream.filter(User::isVip);
            } else {
                userStream = userStream.filter(u -> role.equals(u.getRole()));
            }
        }

        List<User> users = userStream.collect(Collectors.toList());

        model.addAttribute("users", users);
        model.addAttribute("search", search);
        model.addAttribute("selectedRole", role != null ? role : "ALL");

        return "users";
    }

    @GetMapping("/delete-user/{id}")
    public String deleteUser(@PathVariable Long id, HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";
        userRepository.deleteById(id);
        return "redirect:/manager/users?deleted=true";
    }

    @PostMapping("/update-user")
    public String updateUser(@RequestParam Long userId, @RequestParam String username,
            @RequestParam(required = false) String password,
            @RequestParam(required = false, defaultValue = "false") boolean blocked,
            HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";

        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            user.setUsername(username);

            // Only update password if provided
            if (password != null && !password.trim().isEmpty()) {
                user.setPassword(password);
            }

            user.setBlocked(blocked);

            userRepository.save(user);
        }
        return "redirect:/manager/users";
    }

    // =========================
    // MEMBERSHIP MANAGEMENT
    // =========================
    @GetMapping("/memberships")
    public String showMembershipsPage(HttpSession session, Model model) {
        if (!isManager(session))
            return "redirect:/login";

        List<User> allUsers = userRepository.findAll();

        // Separate members and non-members
        List<User> vipUsers = allUsers.stream()
                .filter(User::isVip)
                .collect(Collectors.toList());

        List<User> regularUsers = allUsers.stream()
                .filter(u -> !u.isVip() && "CUSTOMER".equals(u.getRole()))
                .collect(Collectors.toList());

        model.addAttribute("vipUsers", vipUsers);
        model.addAttribute("regularUsers", regularUsers);
        model.addAttribute("plans", membershipPlanRepository.findAll());

        return "manager-memberships"; // was manager-vip
    }

    @GetMapping("/memberships/plans")
    public String showMembershipPlans(HttpSession session, Model model) {
        if (!isManager(session))
            return "redirect:/login";

        model.addAttribute("plans", membershipPlanRepository.findAll());
        model.addAttribute("inventoryItems", inventoryItemRepository.findByActiveTrueOrderByCreatedAtDesc());
        return "manager-plans";
    }

    @PostMapping("/memberships/plans/add")
    public String addMembershipPlan(@RequestParam String name, @RequestParam(required = false) String description,
                                    @RequestParam Double price, @RequestParam int discountPercentage,
                                    @RequestParam(defaultValue = "1") Integer periodYears,
                                    @RequestParam(defaultValue = "false") boolean freeAddonsIncluded,
                                    @RequestParam(defaultValue = "false") boolean priorityBooking,
                                    @RequestParam(required = false) String perks,
                                    @RequestParam(required = false) java.util.List<Long> freeServiceIds,
                                    @RequestParam(defaultValue = "false") boolean active,
                                    @RequestParam(defaultValue = "#fbbf24") String cardColor,
                                    HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";

        com.footballsystem.model.MembershipPlan plan = new com.footballsystem.model.MembershipPlan(
                name, description, price, discountPercentage, freeAddonsIncluded, priorityBooking);
        plan.setActive(active);
        plan.setCardColor(cardColor);
        plan.setPerks(perks);
        plan.setPeriodYears(periodYears);

        if (freeServiceIds != null) {
            java.util.List<com.footballsystem.model.InventoryItem> items = new java.util.ArrayList<>();
            for (Long sId : freeServiceIds) {
                inventoryItemRepository.findById(sId).ifPresent(items::add);
            }
            plan.setFreeServices(items);
        }

        membershipPlanRepository.save(plan);
        return "redirect:/manager/memberships/plans?success=added";
    }

    @PostMapping("/memberships/plans/edit")
    public String editMembershipPlan(@RequestParam Long id, @RequestParam String name, @RequestParam(required = false) String description,
                                     @RequestParam Double price, @RequestParam int discountPercentage,
                                     @RequestParam(defaultValue = "1") Integer periodYears,
                                     @RequestParam(defaultValue = "false") boolean freeAddonsIncluded,
                                     @RequestParam(defaultValue = "false") boolean priorityBooking,
                                     @RequestParam(required = false) String perks,
                                     @RequestParam(required = false) java.util.List<Long> freeServiceIds,
                                     @RequestParam(defaultValue = "false") boolean active,
                                     @RequestParam(defaultValue = "#fbbf24") String cardColor,
                                     HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";

        membershipPlanRepository.findById(id).ifPresent(plan -> {
            plan.setName(name);
            plan.setDescription(description);
            plan.setPrice(price);
            plan.setDiscountPercentage(discountPercentage);
            plan.setPeriodYears(periodYears);
            plan.setFreeAddonsIncluded(freeAddonsIncluded);
            plan.setPriorityBooking(priorityBooking);
            plan.setPerks(perks);

            if (freeServiceIds != null) {
                java.util.List<com.footballsystem.model.InventoryItem> items = new java.util.ArrayList<>();
                for (Long sId : freeServiceIds) {
                    inventoryItemRepository.findById(sId).ifPresent(items::add);
                }
                plan.setFreeServices(items);
            } else {
                plan.setFreeServices(new java.util.ArrayList<>());
            }

            plan.setActive(active);
            plan.setCardColor(cardColor);
            membershipPlanRepository.save(plan);
        });
        return "redirect:/manager/memberships/plans?success=edited";
    }

    @PostMapping("/memberships/plans/delete")
    public String deleteMembershipPlan(@RequestParam Long id, HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";

        membershipPlanRepository.findById(id).ifPresent(plan -> {
            // Nullify for all users pointing to this plan before deleting
            List<User> users = userRepository.findAll().stream()
                    .filter(u -> u.getMembershipPlan() != null && u.getMembershipPlan().getId().equals(id))
                    .collect(Collectors.toList());
            for (User u : users) {
                u.setMembershipPlan(null);
                userRepository.save(u);
            }
            membershipPlanRepository.delete(plan);
        });
        return "redirect:/manager/memberships/plans?success=deleted";
    }

    @PostMapping("/upgrade-user")
    public String upgradeUserToVip(@RequestParam Long userId, @RequestParam(required = false) Long planId, HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            int years = 1;
            if (planId != null) {
                com.footballsystem.model.MembershipPlan plan = membershipPlanRepository.findById(planId).orElse(null);
                if (plan != null) {
                    user.setMembershipPlan(plan);
                    user.setVip(true);
                    if (plan.getPeriodYears() != null) {
                        years = plan.getPeriodYears();
                    }
                }
            } else {
                // Fallback to legacy
                user.setVip(true);
            }
            user.setVipStartDate(java.time.LocalDateTime.now());
            user.setVipExpiryDate(java.time.LocalDateTime.now().plusYears(years));
            userRepository.save(user);
        }
        return "redirect:/manager/memberships";
    }

    @PostMapping("/downgrade-user")
    public String downgradeUserFromVip(@RequestParam Long userId, HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setVip(false);
            user.setMembershipPlan(null);
            user.setVipStartDate(null);
            user.setVipExpiryDate(null);
            userRepository.save(user);
        }
        return "redirect:/manager/memberships";
    }

    // =========================
    // BOOKING MANAGEMENT
    // =========================
    @GetMapping("/bookings")
    public String showBookingsPage(@RequestParam(required = false) String search,
            @RequestParam(required = false) Long branchId,
            HttpSession session, Model model) {
        if (!isManager(session))
            return "redirect:/login";

        List<Booking> allBookings = bookingRepository.findAll();
        Stream<Booking> stream = allBookings.stream()
                .filter(b -> b.getField() != null && b.getUser() != null);

        if (search != null && !search.isEmpty()) {
            stream = stream.filter(b -> String.valueOf(b.getBookingId()).contains(search) ||
                    b.getUser().getUsername().toLowerCase().contains(search.toLowerCase()));
        }

        if (branchId != null) {
            stream = stream.filter(b -> b.getField().getBranch() != null &&
                    b.getField().getBranch().getBranchId().equals(branchId));
        }

        List<Booking> fieldBookings = stream.collect(Collectors.toList());

        model.addAttribute("bookings", fieldBookings);
        model.addAttribute("branches", branchRepository.findAll());
        model.addAttribute("selectedBranchId", branchId);
        return "bookings";
    }

    @PostMapping("/update-booking-status")
    public String updateBookingStatus(@RequestParam Long bookingId, @RequestParam String status, HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking != null) {
            booking.setStatus(status);
            bookingRepository.save(booking);
        }
        return "redirect:/manager/bookings";
    }

    @PostMapping("/update-booking-result")
    public String updateBookingResult(@RequestParam Long bookingId, @RequestParam String result, HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking != null) {
            booking.setResult(result);
            bookingRepository.save(booking);
        }
        return "redirect:/manager/bookings";
    }

    @PostMapping("/update-booking-photo")
    public String updateBookingPhoto(@RequestParam Long bookingId,
            @RequestParam(required = false) String photoLink,
            HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking != null) {
            booking.setPhotoLink(photoLink != null && !photoLink.trim().isEmpty() ? photoLink.trim() : null);
            bookingRepository.save(booking);
        }
        return "redirect:/manager/bookings";
    }

    @GetMapping("/delete-booking/{id}")
    public String deleteBooking(@PathVariable Long id, HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";
        bookingRepository.deleteById(id);
        return "redirect:/manager/bookings?deleted=true";
    }

    // =========================
    // PAYMENT MANAGEMENT
    // =========================
    @GetMapping("/payment-field")
    public String showPaymentFieldPage() {
        return "redirect:/manager/deposit-settings";
    }

    @PostMapping("/payment/update")
    public String updatePaymentStatus(@RequestParam Long bookingId, HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking != null && !"Full Payment".equals(booking.getPaymentStatus())) {
            booking.setPaymentStatus("Full Payment");
            bookingRepository.save(booking);
        }
        return "redirect:/manager/deposit-settings";
    }

    @GetMapping("/payment/delete/{id}")
    public String deletePayment(@PathVariable Long id, HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";
        bookingRepository.deleteById(id);
        return "redirect:/manager/deposit-settings?deleted=true";
    }

    // =========================
    // REPORTS
    // =========================
    @GetMapping("/reports")
    public String showReportsPage(
            @RequestParam(required = false) String searchId,
            @RequestParam(required = false) Long branchFilter,
            HttpSession session, Model model) {
        if (!isManager(session))
            return "redirect:/login";

        List<ProblemReport> reports = problemReportRepository.findAll();

        if (searchId != null && !searchId.trim().isEmpty()) {
            reports = reports.stream()
                    .filter(r -> {
                        String idStr = "R" + r.getReportId();
                        return idStr.toLowerCase().contains(searchId.toLowerCase());
                    })
                    .collect(Collectors.toList());
        }

        if (branchFilter != null) {
            reports = reports.stream()
                    .filter(r -> r.getBranch() != null && r.getBranch().getBranchId().equals(branchFilter))
                    .collect(Collectors.toList());
        }

        // Sort by newest first
        reports.sort((r1, r2) -> r2.getTimestamp().compareTo(r1.getTimestamp()));

        model.addAttribute("reports", reports);
        model.addAttribute("branches", branchRepository.findAll());
        model.addAttribute("searchId", searchId);
        model.addAttribute("branchFilter", branchFilter);

        // Filter staff with MAINTENANCE TEAM position
        List<User> maintenanceStaff = userRepository.findAll().stream()
                .filter(u -> "STAFF".equalsIgnoreCase(u.getRole()) &&
                        "MAINTENANCE TEAM".equalsIgnoreCase(u.getPosition()))
                .collect(Collectors.toList());
        model.addAttribute("maintenanceStaff", maintenanceStaff);

        // Build activity map for each report
        Map<Long, List<ReportActivity>> activitiesMap = new HashMap<>();
        for (ProblemReport report : problemReportRepository.findAll()) {
            activitiesMap.put(report.getReportId(),
                    reportActivityRepository.findByReport_ReportIdOrderByTimestampAsc(report.getReportId()));
        }
        model.addAttribute("activitiesMap", activitiesMap);

        // Build task map for proof images (reportId -> List<Task>)
        Map<Long, List<Task>> tasksMap = new HashMap<>();
        for (Task task : taskRepository.findAll()) {
            if (task.getProblemReport() != null) {
                tasksMap.computeIfAbsent(task.getProblemReport().getReportId(), k -> new ArrayList<>()).add(task);
            }
        }
        model.addAttribute("tasksMap", tasksMap);

        // Build assigned staff IDs map for the multi-select pre-selection (reportId -> Set<Long>)
        Map<Long, Set<Long>> assignedStaffIdsMap = new HashMap<>();
        for (Map.Entry<Long, List<Task>> entry : tasksMap.entrySet()) {
            Set<Long> staffIds = new HashSet<>();
            for (Task t : entry.getValue()) {
                if (t.getAssignedStaff() != null) {
                    staffIds.add(t.getAssignedStaff().getUserId());
                }
            }
            assignedStaffIdsMap.put(entry.getKey(), staffIds);
        }
        model.addAttribute("assignedStaffIdsMap", assignedStaffIdsMap);

        // Build branch -> maintenance staff map for the "View Staff" popup
        Map<Long, List<User>> branchMaintStaffMap = new HashMap<>();
        for (User u : maintenanceStaff) {
            if (u.getBranch() != null) {
                branchMaintStaffMap.computeIfAbsent(u.getBranch().getBranchId(), k -> new ArrayList<>()).add(u);
            }
        }
        model.addAttribute("branchMaintStaffMap", branchMaintStaffMap);

        return "reports";
    }

    @PostMapping("/add-report")
    public String addReport(@RequestParam Long branchId,
            @RequestParam(required = false) Long fieldId,
            @RequestParam String description,
            @RequestParam(value = "reportImage", required = false) MultipartFile reportImage,
            HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";

        try {
            ProblemReport report = new ProblemReport();
            report.setDescription(description);
            report.setStatus("REPORTED");
            report.setTimestamp(LocalDateTime.now());

            // Set branch
            Branch branch = branchRepository.findById(branchId).orElse(null);
            report.setBranch(branch);

            // Set field (optional)
            if (fieldId != null) {
                Field field = fieldRepository.findById(fieldId).orElse(null);
                report.setField(field);
            }

            // Save image if provided
            if (reportImage != null && !reportImage.isEmpty()) {
                String imageUrl = saveImage(reportImage, "report");
                report.setImageUrl(imageUrl);
            }

            problemReportRepository.save(report);

            // Log activity: Report submitted
            User loggedUser = (User) session.getAttribute("loggedUser");
            String performer = loggedUser != null ? loggedUser.getUsername() : "Manager";
            reportActivityRepository.save(new ReportActivity(report,
                    "Report submitted by " + performer, performer));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "redirect:/manager/reports?added=true";
    }

    @PostMapping("/update-report-status")
    public String updateReportStatus(@RequestParam Long reportId,
            @RequestParam String status,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) List<Long> assignedStaffIds,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String dueDate,
            HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";
        ProblemReport report = problemReportRepository.findById(reportId).orElse(null);
        if (report != null) {
            User loggedUser = (User) session.getAttribute("loggedUser");
            String performer = loggedUser != null ? loggedUser.getUsername() : "Manager";

            // Log status change
            String oldStatus = report.getStatus();
            if (!status.equals(oldStatus)) {
                String displayStatus = "FIXED".equals(status) ? "Completed" : ("REVIEW".equals(status) ? "Review" : status);
                
                // Special message when manager verifies completion from REVIEW
                if ("REVIEW".equals(oldStatus) && "FIXED".equals(status)) {
                    reportActivityRepository.save(new ReportActivity(report,
                            "Manager " + performer + " verified completion and approved the repair", performer));
                }
                reportActivityRepository.save(new ReportActivity(report,
                        "Status updated to " + displayStatus, performer));
            }
            report.setStatus(status);

            // Log notes change
            if (notes != null && !notes.isEmpty() && !notes.equals(report.getNotes())) {
                reportActivityRepository.save(new ReportActivity(report,
                        "Manager added note: \"" + notes + "\"", performer));
                report.setNotes(notes);
            }

            // Log priority change
            if (priority != null && !priority.isEmpty()) {
                if (!priority.equals(report.getPriority())) {
                    reportActivityRepository.save(new ReportActivity(report,
                            "Priority set to " + priority, performer));
                }
                report.setPriority(priority);
            }

            // Log due date change
            if (dueDate != null && !dueDate.isEmpty()) {
                LocalDateTime newDueDate = LocalDate.parse(dueDate).atStartOfDay();
                if (report.getDueDate() == null || !newDueDate.equals(report.getDueDate())) {
                    reportActivityRepository.save(new ReportActivity(report,
                            "Due date set to " + dueDate, performer));
                }
                report.setDueDate(newDueDate);
            }

            // Assign multiple staff and auto-create Tasks for each
            if (assignedStaffIds != null && !assignedStaffIds.isEmpty()) {
                // Generate a group ID so sibling tasks can find each other
                String taskGroupId = UUID.randomUUID().toString();

                // Collect names for activity log
                List<String> assignedNames = new ArrayList<>();

                // Set first staff on report for backward-compatible display
                User firstStaff = userRepository.findById(assignedStaffIds.get(0)).orElse(null);
                if (firstStaff != null) {
                    report.setAssignedStaff(firstStaff);
                }

                for (Long staffId : assignedStaffIds) {
                    User staff = userRepository.findById(staffId).orElse(null);
                    if (staff != null) {
                        // Check if this staff already has a task for this report
                        boolean alreadyAssigned = taskRepository.findByProblemReport(report).stream()
                                .anyMatch(t -> t.getAssignedStaff() != null && t.getAssignedStaff().getUserId().equals(staffId));
                        if (!alreadyAssigned) {
                            Task task = new Task();
                            task.setDescription(report.getDescription());
                            task.setStatus("PENDING");
                            task.setAssignedStaff(staff);
                            task.setProblemReport(report);
                            task.setTaskGroupId(taskGroupId);
                            if (dueDate != null && !dueDate.isEmpty()) {
                                task.setDueDate(LocalDate.parse(dueDate).atStartOfDay());
                            }
                            taskRepository.save(task);
                            assignedNames.add(staff.getUsername());
                        }
                    }
                }

                if (!assignedNames.isEmpty()) {
                    reportActivityRepository.save(new ReportActivity(report,
                            "Staff assigned: " + String.join(", ", assignedNames), performer));
                }
            }

            problemReportRepository.save(report);

            if ("FIXED".equals(status) && report.getField() != null) {
                boolean hasUnresolvedIssues = problemReportRepository
                        .existsByField_FieldIdAndStatusNot(report.getField().getFieldId(), "FIXED");

                if (!hasUnresolvedIssues) {
                    Field field = report.getField();
                    if ("Maintenance".equalsIgnoreCase(field.getStatus())) {
                        field.setStatus("Available");
                        fieldRepository.save(field);
                    }
                }
            }
        }
        return "redirect:/manager/reports?updated=true";
    }

    @org.springframework.transaction.annotation.Transactional
    @GetMapping("/report/delete/{id}")
    public String deleteReport(@PathVariable Long id, HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";
        // Delete related tasks first
        ProblemReport report = problemReportRepository.findById(id).orElse(null);
        if (report != null) {
            List<Task> relatedTasks = taskRepository.findByProblemReport(report);
            for (Task task : relatedTasks) {
                task.setAssignedStaff(null);
                task.setProblemReport(null);
                taskRepository.save(task);
                taskRepository.delete(task);
            }
            // Delete related activities
            reportActivityRepository.deleteByReport_ReportId(id);
            // Now delete the report
            problemReportRepository.deleteById(id);
        }
        return "redirect:/manager/reports?deleted=true";
    }

    // =========================
    // MAINTENANCE
    // =========================
    @GetMapping("/maintenance")
    public String showMaintenancePage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long branchId,
            HttpSession session, Model model) {
        if (!isManager(session))
            return "redirect:/login";

        List<ProblemReport> reports = problemReportRepository.findAll();

        // Filter by search query (ID or description)
        if (search != null && !search.trim().isEmpty()) {
            String q = search.trim().toLowerCase();
            reports = reports.stream()
                    .filter(r -> {
                        String idStr = "M" + r.getReportId();
                        return idStr.toLowerCase().contains(q)
                                || (r.getDescription() != null && r.getDescription().toLowerCase().contains(q));
                    })
                    .collect(Collectors.toList());
        }

        // Filter by branch
        if (branchId != null) {
            reports = reports.stream()
                    .filter(r -> r.getBranch() != null && r.getBranch().getBranchId().equals(branchId))
                    .collect(Collectors.toList());
        }

        // Sort by newest first
        reports.sort((r1, r2) -> {
            if (r1.getTimestamp() == null && r2.getTimestamp() == null) return 0;
            if (r1.getTimestamp() == null) return 1;
            if (r2.getTimestamp() == null) return -1;
            return r2.getTimestamp().compareTo(r1.getTimestamp());
        });

        model.addAttribute("reports", reports);
        model.addAttribute("branches", branchRepository.findAll());

        // Maintenance team staff for the "Add Maintenance" modal
        List<User> maintenanceStaff = userRepository.findAll().stream()
                .filter(u -> "STAFF".equalsIgnoreCase(u.getRole()) &&
                        "MAINTENANCE TEAM".equalsIgnoreCase(u.getPosition()))
                .collect(Collectors.toList());
        model.addAttribute("maintenanceStaff", maintenanceStaff);

        // Branch -> staff map for the View & Select Staff popup
        Map<Long, List<User>> branchMaintStaffMap = new HashMap<>();
        for (User u : maintenanceStaff) {
            if (u.getBranch() != null) {
                branchMaintStaffMap.computeIfAbsent(u.getBranch().getBranchId(), k -> new ArrayList<>()).add(u);
            }
        }
        model.addAttribute("branchMaintStaffMap", branchMaintStaffMap);

        // Branch -> fields map for the field dropdown (simple id+name only, avoids entity serialization issues)
        List<com.footballsystem.model.Field> allFields = fieldRepository.findAll();
        model.addAttribute("fields", allFields);
        Map<Long, List<Map<String, Object>>> fieldsByBranch = new HashMap<>();
        for (com.footballsystem.model.Field f : allFields) {
            if (f.getBranch() != null) {
                Map<String, Object> fieldData = new HashMap<>();
                fieldData.put("id", f.getFieldId());
                fieldData.put("name", f.getName());
                fieldsByBranch.computeIfAbsent(f.getBranch().getBranchId(), k -> new ArrayList<>()).add(fieldData);
            }
        }
        model.addAttribute("fieldsByBranch", fieldsByBranch);

        return "maintenance";
    }

    @PostMapping("/add-maintenance-task")
    public String addMaintenanceTask(
            @RequestParam(value = "maintenanceTypes", required = false) List<String> maintenanceTypes,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long fieldId,
            @RequestParam(required = false) String dueDate,
            @RequestParam(value = "assignedStaffIds", required = false) List<Long> assignedStaffIds,
            HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";

        // Build description from selected types
        String description = (maintenanceTypes != null && !maintenanceTypes.isEmpty())
                ? String.join(", ", maintenanceTypes)
                : "General Maintenance";

        if (assignedStaffIds == null || assignedStaffIds.isEmpty()) {
            return "redirect:/manager/maintenance?added=true";
        }


        String taskGroupId = java.util.UUID.randomUUID().toString();

        for (Long staffId : assignedStaffIds) {
            User staff = userRepository.findById(staffId).orElse(null);
            if (staff != null) {
                Task task = new Task();
                task.setDescription(description);
                task.setStatus("PENDING");
                task.setAssignedStaff(staff);
                task.setTaskGroupId(taskGroupId);
                task.setCreationDate(java.time.LocalDateTime.now());
                if (dueDate != null && !dueDate.isEmpty()) {
                    task.setDueDate(java.time.LocalDate.parse(dueDate).atStartOfDay());
                }
                taskRepository.save(task);
            }
        }

        return "redirect:/manager/maintenance?added=true";
    }

    @GetMapping("/maintenance/delete/{id}")
    public String deleteMaintenance(@PathVariable Long id, HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";
        problemReportRepository.deleteById(id);
        return "redirect:/manager/maintenance?deleted=true";
    }

    @PostMapping("/add-task")
    public String addTask(@RequestParam String description, @RequestParam Long assignedStaffId,
            @RequestParam String status, HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";

        User staff = userRepository.findById(assignedStaffId).orElse(null);
        Task task = new Task();
        task.setDescription(description);
        task.setAssignedStaff(staff);
        task.setStatus(status);
        task.setCreationDate(LocalDateTime.now());

        taskRepository.save(task);
        return "redirect:/manager/maintenance";
    }

    @PostMapping("/assign-task")
    public String assignTask(@RequestParam Long staffId,
            @RequestParam String description,
            @RequestParam("dueDate") LocalDateTime dueDate,
            @RequestParam(value = "taskImage", required = false) MultipartFile taskImage,
            HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";

        User staff = userRepository.findById(staffId).orElse(null);
        if (staff != null) {
            Task task = new Task();
            task.setDescription(description);
            task.setAssignedStaff(staff);
            task.setStatus("PENDING");
            task.setDueDate(dueDate);
            task.setCreationDate(LocalDateTime.now());

            if (taskImage != null && !taskImage.isEmpty()) {
                String imageUrl = saveImage(taskImage, "task");
                if (imageUrl != null) {
                    task.setTaskImageUrl(imageUrl);
                }
            }

            taskRepository.save(task);
        }
        return "redirect:/manager/staff";
    }

    @PostMapping("/update-task-status")
    public String updateTaskStatus(@RequestParam Long taskId, @RequestParam String status, HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task != null) {
            task.setStatus(status);
            taskRepository.save(task);
        }
        return "redirect:/manager/maintenance";
    }

    @GetMapping("/delete-task/{id}")
    public String deleteTask(@PathVariable Long id, HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";
        taskRepository.deleteById(id);
        return "redirect:/manager/staff?deleted=true";
    }

    // =========================
    // TRANSACTIONS
    // =========================
    @GetMapping("/transactions")
    public String showTransactionsPage(@RequestParam(required = false) String search,
            @RequestParam(required = false) String filter,
            HttpSession session, Model model) {
        if (!isManager(session))
            return "redirect:/login";

        List<Booking> allBookings = bookingRepository.findAll();
        Stream<Booking> stream = allBookings.stream();

        if (search != null && !search.isEmpty()) {
            stream = stream.filter(b -> String.valueOf(b.getBookingId()).contains(search));
        }

        if (filter != null && !filter.isEmpty()) {
            LocalDate today = LocalDate.now();
            if ("DAY".equalsIgnoreCase(filter)) {
                stream = stream.filter(b -> b.getCreatedAt() != null && b.getCreatedAt().toLocalDate().equals(today));
            } else if ("WEEK".equalsIgnoreCase(filter)) {
                LocalDate weekAgo = today.minusDays(7);
                stream = stream
                        .filter(b -> b.getCreatedAt() != null && !b.getCreatedAt().toLocalDate().isBefore(weekAgo));
            } else if ("MONTH".equalsIgnoreCase(filter)) {
                LocalDate monthAgo = today.minusDays(30);
                stream = stream
                        .filter(b -> b.getCreatedAt() != null && !b.getCreatedAt().toLocalDate().isBefore(monthAgo));
            }
        }

        List<Booking> transactions = stream
                .sorted(Comparator.comparing(Booking::getDate).reversed()) // Keep sorting by booking date or switch to
                                                                           // created at? Usually created at is better
                                                                           // for transactions. I'll stick to booking
                                                                           // date for sorting if not specified, but
                                                                           // usually transactions are sorted by
                                                                           // occurrence. The user only asked for
                                                                           // filter. I'll keep sorting as is for now
                                                                           // unless asked, but logically transactions
                                                                           // should be sorted by created_at. Let's
                                                                           // stick to user request strictly "make the
                                                                           // filter the time of paymen created at".
                .collect(Collectors.toList());

        model.addAttribute("transactions", transactions);
        model.addAttribute("search", search);
        model.addAttribute("filter", filter);
        return "transactions";
    }

    @GetMapping("/delete-transaction/{id}")
    public String deleteTransaction(@PathVariable Long id, HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";
        bookingRepository.deleteById(id);
        return "redirect:/manager/transactions?deleted=true";
    }

    // =========================
    // PERFORMANCE DASHBOARD
    // =========================
    @GetMapping("/performance")
    public String showPerformance(Model model,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";

        // Defaults
        int currentYear = LocalDate.now().getYear();
        int currentMonth = LocalDate.now().getMonthValue();
        if (year == null)
            year = currentYear;
        if (month == null)
            month = currentMonth;

        List<Branch> branches = branchRepository.findAll();
        model.addAttribute("branches", branches);
        model.addAttribute("selectedYear", year);
        model.addAttribute("selectedMonth", month);
        model.addAttribute("selectedBranchId", branchId);

        // Fetch Bookings
        List<Booking> bookings;
        if (branchId != null) {
            bookings = bookingRepository.findByField_Branch_BranchId(branchId);
            Branch selectedBranch = branchRepository.findById(branchId).orElse(null);
            model.addAttribute("selectedBranchName",
                    selectedBranch != null ? selectedBranch.getName() : "Unknown Branch");
        } else {
            bookings = bookingRepository.findAll();
            model.addAttribute("selectedBranchName", "All Branches");
        }

        // Filter by Date (Month/Year)
        // Only include non-rejected/cancelled bookings for stats?
        // User request: "total revenue, total bookings"
        // Total bookings usually includes all? Or only valid ones?
        // Revenue definitely only APPROVED/COMPLETED.

        int filterYear = year;
        int filterMonth = month;
        final List<Booking> monthBookings = bookings.stream()
                .filter(b -> {
                    LocalDate d = b.getDate();
                    return d != null && d.getYear() == filterYear && d.getMonthValue() == filterMonth;
                })
                .sorted(Comparator.comparing(Booking::getDate))
                .collect(Collectors.toList());

        // Calculate KPIs
        long totalCount = monthBookings.size();
        double totalRevenue = monthBookings.stream()
                .filter(b -> "APPROVED".equalsIgnoreCase(b.getStatus()) || "COMPLETED".equalsIgnoreCase(b.getStatus()))
                .mapToDouble(b -> b.getPrice() != null ? b.getPrice() : 0.0)
                .sum();

        // Detailed Report: Weekly Breakdown
        // We will group by Week Number of the month
        Map<Integer, Map<String, Object>> weeklyStats = new LinkedHashMap<>();

        for (Booking b : monthBookings) {
            int weekNum = b.getDate().get(WeekFields.of(Locale.getDefault()).weekOfMonth());
            weeklyStats.putIfAbsent(weekNum, new HashMap<>());
            Map<String, Object> weekData = weeklyStats.get(weekNum);

            weekData.putIfAbsent("week", "Week " + weekNum);
            weekData.put("count", (long) weekData.getOrDefault("count", 0L) + 1);

            weekData.putIfAbsent("bookings", new ArrayList<Booking>());
            ((List<Booking>) weekData.get("bookings")).add(b);

            if ("APPROVED".equalsIgnoreCase(b.getStatus()) || "COMPLETED".equalsIgnoreCase(b.getStatus())) {
                weekData.put("revenue", (double) weekData.getOrDefault("revenue", 0.0) + (b.getPrice() != null ? b.getPrice() : 0.0));
            } else {
                weekData.putIfAbsent("revenue", 0.0);
            }

            // Collect Dates in this week (min/max) for display
            LocalDate d = b.getDate();
            if (!weekData.containsKey("startDate") || d.isBefore((LocalDate) weekData.get("startDate"))) {
                weekData.put("startDate", d);
            }
            if (!weekData.containsKey("endDate") || d.isAfter((LocalDate) weekData.get("endDate"))) {
                weekData.put("endDate", d);
            }
        }

        model.addAttribute("totalBookings", totalCount);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("weeklyReport", weeklyStats.values());

        // === BRANCH COMPARISON CHART DATA ===
        // For each branch, compute revenue per month (Jan-Dec) for the selected year
        List<Branch> allBranches = branchRepository.findAll();
        List<Booking> allBookingsForYear = bookingRepository.findAll().stream()
                .filter(b -> b.getDate() != null && b.getDate().getYear() == filterYear)
                .filter(b -> b.getField() != null && b.getField().getBranch() != null)
                .filter(b -> "APPROVED".equalsIgnoreCase(b.getStatus()) || "Full Payment".equals(b.getPaymentStatus())
                        || "Pay Deposit".equals(b.getPaymentStatus()))
                .collect(Collectors.toList());

        // Build JSON: { branchId: { name: "...", data: [jan,feb,...,dec] }, ... }
        StringBuilder chartJson = new StringBuilder("{");
        boolean firstBranch = true;
        for (Branch br : allBranches) {
            double[] monthly = new double[12];
            for (Booking b : allBookingsForYear) {
                if (br.getBranchId().equals(b.getField().getBranch().getBranchId()) && b.getPrice() != null) {
                    monthly[b.getDate().getMonthValue() - 1] += b.getPrice();
                }
            }
            if (!firstBranch)
                chartJson.append(",");
            firstBranch = false;
            chartJson.append("\"").append(br.getBranchId()).append("\":{")
                    .append("\"name\":\"").append(br.getName().replace("\"", "\\\"")).append("\",")
                    .append("\"data\":[");
            for (int m = 0; m < 12; m++) {
                if (m > 0)
                    chartJson.append(",");
                chartJson.append(String.format("%.2f", monthly[m]));
            }
            chartJson.append("]}");
        }
        chartJson.append("}");
        model.addAttribute("branchChartJson", chartJson.toString());
        model.addAttribute("chartYear", filterYear);

        return "performance";
    }

    @GetMapping("/deposit-settings")
    public String showDepositSettings(@RequestParam(required = false) String search,
                                      @RequestParam(required = false) Long branchId,
                                      HttpSession session, Model model) {
        if (!isManager(session))
            return "redirect:/login";

        String depositAmount = systemSettingRepository.findById("deposit_amount")
                .map(SystemSetting::getSettingValue).orElse("200.0");
        String halfPriceEnabled = systemSettingRepository.findById("deposit_half_price_enabled")
                .map(SystemSetting::getSettingValue).orElse("false");
        String geminiApiKey = systemSettingRepository.findById("gemini_api_key")
                .map(SystemSetting::getSettingValue).orElse("");

        model.addAttribute("depositAmount", depositAmount);
        model.addAttribute("halfPriceEnabled", halfPriceEnabled);
        model.addAttribute("geminiApiKey", geminiApiKey);

        List<Booking> allBookings = bookingRepository.findAll();
        java.util.stream.Stream<Booking> stream = allBookings.stream()
                .filter(b -> b.getField() != null);

        if (search != null && !search.isEmpty()) {
            stream = stream.filter(b -> String.valueOf(b.getBookingId()).contains(search));
        }

        if (branchId != null) {
            stream = stream.filter(b -> b.getField().getBranch() != null &&
                    b.getField().getBranch().getBranchId().equals(branchId));
        }

        List<Booking> fieldBookings = stream.collect(Collectors.toList());

        model.addAttribute("bookings", fieldBookings);
        model.addAttribute("branches", branchRepository.findAll());
        model.addAttribute("selectedBranchId", branchId);
        return "manager-deposit-settings";
    }

    @PostMapping("/deposit-settings/save")
    public String saveDepositSettings(@RequestParam String depositAmount,
                                      @RequestParam(required = false) String halfPriceEnabled,
                                      HttpSession session, RedirectAttributes redirectAttributes) {
        if (!isManager(session))
            return "redirect:/login";

        try {
            double amount = Double.parseDouble(depositAmount);
            if (amount < 0) {
                redirectAttributes.addFlashAttribute("error", "Deposit amount cannot be negative.");
                return "redirect:/manager/deposit-settings";
            }
        } catch (NumberFormatException e) {
            redirectAttributes.addFlashAttribute("error", "Invalid deposit amount format.");
            return "redirect:/manager/deposit-settings";
        }

        SystemSetting amountSetting = new SystemSetting("deposit_amount", depositAmount);
        SystemSetting toggleSetting = new SystemSetting("deposit_half_price_enabled", 
                (halfPriceEnabled != null && halfPriceEnabled.equals("true")) ? "true" : "false");

        systemSettingRepository.save(amountSetting);
        systemSettingRepository.save(toggleSetting);

        redirectAttributes.addFlashAttribute("success", "Deposit settings updated successfully!");
        return "redirect:/manager/deposit-settings";
    }

    @PostMapping("/deposit-settings/save-gemini-key")
    public String saveGeminiKey(@RequestParam String geminiApiKey,
                                HttpSession session, RedirectAttributes redirectAttributes) {
        if (!isManager(session))
            return "redirect:/login";

        SystemSetting apiSetting = new SystemSetting("gemini_api_key", geminiApiKey.trim());
        systemSettingRepository.save(apiSetting);

        redirectAttributes.addFlashAttribute("success", "Gemini API key updated successfully!");
        return "redirect:/manager/deposit-settings";
    }
}

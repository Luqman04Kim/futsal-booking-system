package com.footballsystem.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.footballsystem.repository.BookingRepository;
import com.footballsystem.repository.UserRepository;
import com.footballsystem.repository.FieldRepository;
import com.footballsystem.repository.BranchRepository;
import com.footballsystem.repository.PriceMatrixRepository;
import com.footballsystem.repository.TaskRepository;
import com.footballsystem.repository.ProblemReportRepository;
import com.footballsystem.model.Booking;
import com.footballsystem.model.User;
import com.footballsystem.model.Branch;
import com.footballsystem.model.Field;
import com.footballsystem.model.PriceMatrix;
import com.footballsystem.model.Task;
import com.footballsystem.model.ProblemReport;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private com.footballsystem.repository.SystemSettingRepository systemSettingRepository;

    private double getRequiredDeposit(double totalPrice) {
        double configDeposit = Double.parseDouble(systemSettingRepository.findById("deposit_amount")
                .map(com.footballsystem.model.SystemSetting::getSettingValue).orElse("200.0"));
        boolean halfPriceEnabled = "true".equals(systemSettingRepository.findById("deposit_half_price_enabled")
                .map(com.footballsystem.model.SystemSetting::getSettingValue).orElse("false"));
        
        if (totalPrice < configDeposit) {
            if (halfPriceEnabled) {
                return totalPrice / 2.0;
            } else {
                return totalPrice;
            }
        }
        return configDeposit;
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FieldRepository fieldRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private PriceMatrixRepository priceMatrixRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProblemReportRepository problemReportRepository;

    // API Key
    private static final String API_KEY = "AIzaSyATamWFVHrr-hWmjrcboPPbLh0Sr7RcUSo";

    // Using 'gemini-1.5-flash' (stable model)
    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key="
            + API_KEY;

    @PostMapping("/ask")
    @SuppressWarnings("unchecked")
    public Map<String, String> askGemini(@RequestBody Map<String, String> payload, HttpSession session) {
        String userQuestion = payload.get("question");
        String botResponse = "I'm having trouble connecting to the AI service right now.";

        System.out.println("Received question: " + userQuestion);

        try {
            // 1. Retrieve History from Session
            List<Map<String, Object>> history = (List<Map<String, Object>>) session.getAttribute("chatHistory");
            if (history == null) {
                history = new ArrayList<>();
            }

            // 2. Construct User Message (for History - clean)
            Map<String, Object> userContent = new HashMap<>();
            userContent.put("role", "user");
            List<Map<String, String>> userParts = new ArrayList<>();
            Map<String, String> userPart = new HashMap<>();
            userPart.put("text", userQuestion);
            userParts.add(userPart);
            userContent.put("parts", userParts);

            // 3. Fetch System Data (FULL CONTEXT)
            StringBuilder dataBuilder = new StringBuilder();

            // --- Current Logged In User & Bookings Context ---
            User loggedUser = (User) session.getAttribute("loggedUser");
            if (loggedUser != null) {
                // Refresh user entity to get latest state from DB
                loggedUser = userRepository.findById(loggedUser.getUserId()).orElse(loggedUser);
                dataBuilder.append("Current Logged-in User:\n");
                dataBuilder.append("- Username: ").append(loggedUser.getUsername()).append("\n");
                dataBuilder.append("- Email: ").append(loggedUser.getEmail()).append("\n");
                dataBuilder.append("- Role: ").append(loggedUser.getRole()).append("\n");

                List<Booking> userBookings = bookingRepository.findByUser(loggedUser);
                dataBuilder.append("- Bookings:\n");
                
                boolean hasPending = false;
                boolean hasOverdue = false;
                double totalDueAmount = 0.0;
                LocalDateTime nowTime = LocalDateTime.now(ZoneId.of("Asia/Kuala_Lumpur"));
                
                if (userBookings.isEmpty()) {
                    dataBuilder.append("  (No bookings found for this user)\n");
                } else {
                    for (Booking b : userBookings) {
                        String fieldName = b.getField() != null ? b.getField().getName() : "Unknown Field";
                        String branchName = (b.getField() != null && b.getField().getBranch() != null) ? b.getField().getBranch().getName() : "Unknown Branch";
                        double price = b.getPrice() != null ? b.getPrice() : 0.0;
                        
                        dataBuilder.append(String.format("  * Booking ID: %d, Field: %s (%s), Date: %s, Time: %s-%s, Status: %s, Payment Status: %s, Price: RM %.2f\n",
                                b.getBookingId(), fieldName, branchName, b.getDate(), b.getStartTime(), b.getEndTime(), b.getStatus(), b.getPaymentStatus(), price));
                        
                        if ("PENDING".equals(b.getStatus())) {
                            hasPending = true;
                        }
                        
                        if ("Pay Deposit".equals(b.getPaymentStatus()) && "APPROVED".equals(b.getStatus())) {
                            if (b.getDate() != null && b.getEndTime() != null) {
                                LocalDateTime matchEnd = LocalDateTime.of(b.getDate(), b.getEndTime());
                                double depositAmount = getRequiredDeposit(price);
                                if (nowTime.isAfter(matchEnd)) {
                                    hasOverdue = true;
                                    double outstanding = Math.max(0, price - depositAmount);
                                    totalDueAmount += outstanding;
                                    dataBuilder.append(String.format("    [OVERDUE PAYMENT] Match ended on %s but remaining balance of RM %.2f is unpaid!\n", matchEnd.toString(), outstanding));
                                } else {
                                    double outstanding = Math.max(0, price - depositAmount);
                                    totalDueAmount += outstanding;
                                    dataBuilder.append(String.format("    [UPCOMING MATCH BALANCE DUE] Deposit of RM %.2f paid. Remaining balance of RM %.2f due after match.\n", depositAmount, outstanding));
                                }
                            }
                        }
                    }
                }
                
                dataBuilder.append("- Has Pending Booking (blocking new bookings): ").append(hasPending ? "Yes" : "No").append("\n");
                dataBuilder.append("- Has Overdue Payment (blocking new bookings): ").append(hasOverdue ? "Yes" : "No").append("\n");
                if (totalDueAmount > 0) {
                    dataBuilder.append("- Total Outstanding Balance: RM ").append(String.format("%.2f", totalDueAmount)).append("\n");
                }
                dataBuilder.append("\n");
            } else {
                dataBuilder.append("Current Logged-in User: None (Anonymous guest user)\n\n");
            }

            // --- Global Stats ---
            long totalUsers = userRepository.count();
            long totalBookings = bookingRepository.count();
            List<Booking> allBookings = bookingRepository.findAll();
            double totalRevenue = allBookings.stream()
                    .filter(b -> "APPROVED".equalsIgnoreCase(b.getStatus()) || "COMPLETED".equalsIgnoreCase(b.getStatus()))
                    .mapToDouble(b -> b.getPrice() != null ? b.getPrice() : 0.0)
                    .sum();

            dataBuilder.append("Global Statistics:\n");
            dataBuilder.append("- Total Users: ").append(totalUsers).append("\n");
            dataBuilder.append("- Total Bookings: ").append(totalBookings).append("\n");
            dataBuilder.append("- Total Revenue: RM ").append(String.format("%.2f", totalRevenue)).append("\n\n");

            // Branches
            List<Branch> branches = branchRepository.findAll();
            dataBuilder.append("Branches:\n");
            for (Branch b : branches) {
                dataBuilder.append(String.format("- %s (Location: %s, Contact: %s, Manager: %s)\n",
                        b.getName(), b.getLocation(), b.getContactNumber(),
                        b.getManager() != null ? b.getManager().getUsername() : "None"));
            }
            dataBuilder.append("\n");

            // Fields & Prices
            List<Field> fields = fieldRepository.findAll();
            dataBuilder.append("Fields & Standard Prices:\n");
            for (Field f : fields) {
                String branchName = f.getBranch() != null ? f.getBranch().getName() : "No Branch";
                dataBuilder.append(String.format("- %s (Type: %s, Status: %s, Branch: %s)\n", f.getName(), f.getType(),
                        f.getStatus(), branchName));
                dataBuilder.append(String.format("  Price/Hour: $%.2f, Weekend Price: $%.2f\n", f.getPricePerHour(),
                        f.getWeekendPrice()));
            }
            dataBuilder.append("\n");

            // Special Pricing (Price Matrix)
            List<PriceMatrix> prices = priceMatrixRepository.findAll();
            if (!prices.isEmpty()) {
                dataBuilder.append("Special Pricing Rules:\n");
                for (PriceMatrix pm : prices) {
                    String fieldName = pm.getField() != null ? pm.getField().getName() : "Unknown Field";
                    dataBuilder.append(String.format("- %s on %s at %s: $%.2f\n", fieldName, pm.getDayOfWeek(),
                            pm.getStartTime(), pm.getPrice()));
                }
                dataBuilder.append("\n");
            }

            // Staff
            List<User> allUsersList = userRepository.findAll();
            List<User> staffMembers = allUsersList.stream()
                    .filter(u -> "STAFF".equalsIgnoreCase(u.getRole()) || "MANAGER".equalsIgnoreCase(u.getRole()))
                    .collect(Collectors.toList());

            dataBuilder.append("Staff & Managers:\n");
            for (User u : staffMembers) {
                dataBuilder.append(String.format("- %s (Role: %s, Position: %s, Phone: %s)\n",
                        u.getUsername(), u.getRole(), u.getPosition(), u.getPhoneNumber()));
            }
            dataBuilder.append("\n");

            // Maintenance Tasks
            List<Task> tasks = taskRepository.findAll();
            dataBuilder.append("Maintenance Tasks:\n");
            tasks.stream()
                    .sorted(Comparator.comparing(Task::getCreationDate).reversed())
                    .limit(10)
                    .forEach(t -> {
                        String assignee = t.getAssignedStaff() != null ? t.getAssignedStaff().getUsername()
                                : "Unassigned";
                        dataBuilder.append(String.format("- [Task %d] %s (Status: %s, Assigned: %s)\n",
                                t.getTaskId(), t.getDescription(), t.getStatus(), assignee));
                    });
            dataBuilder.append("\n");

            // Problem Reports
            List<ProblemReport> reports = problemReportRepository.findAll();
            dataBuilder.append("Recent Problem Reports:\n");
            reports.stream()
                    .sorted(Comparator.comparing(ProblemReport::getTimestamp).reversed())
                    .limit(10)
                    .forEach(r -> {
                        String reporter = r.getReporter() != null ? r.getReporter().getUsername() : "Anonymous";
                        String loc = r.getBranch() != null ? r.getBranch().getName() : "General";
                        if (r.getField() != null)
                            loc += " - " + r.getField().getName();
                        dataBuilder.append(String.format("- [Report %d] %s at %s (Status: %s, Reported By: %s)\n",
                                r.getReportId(), r.getDescription(), loc, r.getStatus(), reporter));
                    });
            dataBuilder.append("\n");

            // Bookings (Recent 20 - only include complete records)
            dataBuilder.append("Recent Bookings:\n");
            allBookings.stream()
                    .filter(b -> b.getField() != null && b.getUser() != null && b.getDate() != null)
                    .sorted((b1, b2) -> b2.getDate().compareTo(b1.getDate()))
                    .limit(20)
                    .forEach(b -> {
                        dataBuilder.append(String.format("- %s by %s on %s %s-%s (%s)\n",
                                b.getField().getName(),
                                b.getUser().getUsername(),
                                b.getDate(),
                                b.getStartTime() != null ? b.getStartTime() : "N/A",
                                b.getEndTime() != null ? b.getEndTime() : "N/A",
                                b.getStatus() != null ? b.getStatus() : "N/A"));
                    });

            // --- Current Date/Time ---
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kuala_Lumpur"));
            String currentDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String currentDayOfWeek = now.format(DateTimeFormatter.ofPattern("EEEE"));
            String currentDateTime = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

            String systemData = dataBuilder.toString();

            // 4. Construct Request Message (with System Data injected)
            Map<String, Object> requestUserContent = new HashMap<>();
            requestUserContent.put("role", "user");
            List<Map<String, String>> requestUserParts = new ArrayList<>();
            Map<String, String> requestUserPart = new HashMap<>();

            // Context Injection Logic
            String systemPrompt =
                "You are a helpful and intelligent assistant for FOOTBALLHUB, a football field booking management system.\n" +
                "You can answer BOTH system-specific questions AND general knowledge questions (world events, science, history, current affairs, etc.).\n\n" +
                "=== CURRENT DATE & TIME ===\n" +
                "Today's date : " + currentDate + " (" + currentDayOfWeek + ")\n" +
                "Current time : " + currentDateTime + " (Malaysia/KL timezone)\n\n" +
                "=== LIVE SYSTEM DATA ===\n" + systemData + "\n" +
                "=== INSTRUCTIONS ===\n" +
                "1. DATE-AWARE QUERIES: You know today's exact date (' " + currentDate + "'). " +
                "   When asked about 'today', 'tomorrow', 'this week' etc., compare against that date from the booking data above.\n" +
                "   Example: if asked 'any booking today?' check the Recent Bookings list for entries whose date matches " + currentDate + ". " +
                "   If none match, clearly say 'There are no bookings today (" + currentDate + ")'.\n" +
                "2. SYSTEM QUERIES: Use the system data to answer questions about bookings, users, revenue, staff, maintenance, and reports.\n" +
                "3. GENERAL KNOWLEDGE: The chatbot MUST answer ANY general knowledge questions (history, geography, world leaders, presidents, prime ministers, science, math, current events, trivia, general chat, jokes, etc.). For example, if asked 'Who is the President/Prime Minister of Malaysia?' or 'Tell me a joke' or 'What is 10 + 10?', do NOT say it is outside your scope. Instead, answer it confidently and accurately using your own knowledge.\n" +
                "4. GREETINGS: Respond naturally to greetings like 'hi', 'hello', 'how are you'.\n" +
                "5. CUSTOMER DUE PAYMENTS & BOOKING BLOCKS:\n" +
                "   - If a customer asks 'do I have due payment?' or similar:\n" +
                "     * If they are not logged in (Current Logged-in User is None), politely ask them to log in first so you can check their account.\n" +
                "     * If they are logged in, summarize their bookings with due payments (PENDING bookings or APPROVED bookings with payment status 'Pay Deposit'). State the outstanding balance, the booking details (date, time, field), and if it is overdue (match has ended, remaining balance unpaid).\n" +
                "   - If they ask why they cannot proceed with booking:\n" +
                "     * Explain if they have a 'PENDING' booking (they must complete payment for it first).\n" +
                "     * Explain if they have an 'Overdue Payment' (match has ended, remaining balance is unpaid). Tell them they must pay the remaining balance to make a new booking.\n" +
                "     * State the exact field, date, and outstanding amount for the blocking booking.\n" +
                "6. Be concise, friendly, and professional.\n\n";

            if (history.isEmpty()) {
                requestUserPart.put("text",
                        "[Current date: " + currentDate + ", Time: " + currentDateTime + "]\n" +
                        "[Live System Data]:\n" + systemData + "\n\nUser Question: " + userQuestion);
            } else {
                requestUserPart.put("text",
                        "[Context Refresh - Current date: " + currentDate + ", Time: " + currentDateTime + "]\n" +
                        "[Live System Data Updated]:\n" + systemData + "\n\nUser Question: " + userQuestion);
            }

            requestUserParts.add(requestUserPart);
            requestUserContent.put("parts", requestUserParts);

            // 5. Construct Request History
            List<Map<String, Object>> requestHistory = new ArrayList<>(history);
            requestHistory.add(requestUserContent);

            // 6. Construct Request Body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", requestHistory);

            // Configure System Instruction so Gemini applies it on ALL turns of the session
            Map<String, Object> systemInstructionMap = new HashMap<>();
            List<Map<String, String>> systemInstructionParts = new ArrayList<>();
            Map<String, String> systemInstructionPart = new HashMap<>();
            systemInstructionPart.put("text", systemPrompt);
            systemInstructionParts.add(systemInstructionPart);
            systemInstructionMap.put("parts", systemInstructionParts);
            requestBody.put("systemInstruction", systemInstructionMap);

            // 7. Send Request
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            System.out.println("Sending request to Gemini");

            ResponseEntity<String> responseEntity = restTemplate.postForEntity(GEMINI_URL, entity, String.class);
            String rawResponse = responseEntity.getBody();

            // 8. Parse Response
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> responseMap = mapper.readValue(rawResponse, Map.class);

            if (responseMap != null && responseMap.containsKey("candidates")) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseMap.get("candidates");
                if (!candidates.isEmpty()) {
                    Map<String, Object> firstCandidate = candidates.get(0);
                    Map<String, Object> candidateContent = (Map<String, Object>) firstCandidate.get("content");
                    List<Map<String, String>> candidateParts = (List<Map<String, String>>) candidateContent
                            .get("parts");

                    if (!candidateParts.isEmpty()) {
                        botResponse = candidateParts.get(0).get("text");

                        // 9. Update History on Success
                        history.add(userContent);

                        Map<String, Object> botContent = new HashMap<>();
                        botContent.put("role", "model");
                        List<Map<String, String>> botParts = new ArrayList<>();
                        Map<String, String> botPart = new HashMap<>();
                        botPart.put("text", botResponse);
                        botParts.add(botPart);
                        botContent.put("parts", botParts);

                        history.add(botContent);
                        session.setAttribute("chatHistory", history);
                    }
                }
            }

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            System.err.println("Gemini API Error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
            String statusCode = e.getStatusCode().toString();
            if (statusCode.contains("429") || e.getResponseBodyAsString().contains("RESOURCE_EXHAUSTED")) {
                botResponse = "The AI chatbot has reached its daily usage limit. Please try again tomorrow or contact the system administrator.";
            } else if (statusCode.contains("404")) {
                botResponse = "The AI service is temporarily unavailable. Please try again later.";
            } else {
                botResponse = "I encountered an error connecting to the AI service. Please try again shortly.";
            }
        } catch (Exception e) {
            System.err.println("Gemini Internal Error: " + e.getMessage());
            e.printStackTrace();
            botResponse = "I'm sorry, I'm currently unable to process your request. Please try again later.";
        }

        Map<String, String> result = new HashMap<>();
        result.put("answer", botResponse);
        return result;
    }
}

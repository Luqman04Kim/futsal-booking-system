package com.footballsystem.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import com.footballsystem.model.Booking;

import java.time.format.DateTimeFormatter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class EmailService {

    @Value("${resend.api.key:re_4i4LAHST_GVcHnzXr8DdUyugNxdTbqntg}")
    private String resendApiKey;

    @Value("${resend.from:onboarding@resend.dev}")
    private String fromEmail;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Core method to send simple text emails
    public void sendSimpleEmail(String toEmail, String subject, String body) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("from", "FOOTBALLHUB <" + fromEmail + ">");
            payload.put("to", List.of(toEmail));
            payload.put("subject", subject);
            payload.put("text", body);

            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("Email sent successfully to " + toEmail + ". Response: " + response.body());
            } else {
                System.err.println("Failed to send email to " + toEmail + ". Status: " + response.statusCode() + ", Response: " + response.body());
            }
        } catch (Exception e) {
            System.err.println("Error sending email: " + e.getMessage());
        }
    }

    // 1. Booking Confirmation Email
    public void sendBookingConfirmation(String toEmail, String userName, String fieldName, String date, String time,
            double price) {
        String subject = "Booking Confirmed - FOOTBALLHUB";
        String body = "Dear " + userName + ",\n\n" +
                "Your booking has been successfully confirmed.\n\n" +
                "   Field: " + fieldName + "\n" +
                "   Date: " + date + "\n" +
                "   Time: " + time + "\n" +
                "   Total Paid: RM " + price + "\n\n" +
                "Please show this email at the counter upon arrival.\n\n" +
                "Regards,\n" +
                "FOOTBALLHUB Manager Team";

        sendSimpleEmail(toEmail, subject, body);
    }

    // 1b. Booking Confirmation Email WITH PDF Receipt Attachment
    public void sendBookingConfirmationWithReceipt(String toEmail, String userName, Booking booking, byte[] pdfBytes) {
        try {
            String fieldName = booking.getField() != null ? booking.getField().getName() : "N/A";
            DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("hh:mm a");

            String dateStr = booking.getDate() != null ? booking.getDate().toString() : "N/A";
            String timeStr = "N/A";
            if (booking.getStartTime() != null && booking.getEndTime() != null) {
                timeStr = booking.getStartTime().format(timeFormat) + " - " + booking.getEndTime().format(timeFormat);
            }

            String body = "Dear " + userName + ",\n\n" +
                    "Your booking has been successfully confirmed.\n\n" +
                    "   Booking ID: #" + booking.getBookingId() + "\n" +
                    "   Field: " + fieldName + "\n" +
                    "   Date: " + dateStr + "\n" +
                    "   Time: " + timeStr + "\n" +
                    "   Total Paid: RM " + String.format("%.2f", booking.getPrice()) + "\n\n" +
                    "Your receipt is attached to this email.\n" +
                    "Please present the receipt at the counter upon arrival.\n\n" +
                    "Regards,\n" +
                    "FOOTBALLHUB Manager Team";

            String filename = "Receipt_" + booking.getBookingId() + ".pdf";
            String base64Content = Base64.getEncoder().encodeToString(pdfBytes);

            Map<String, Object> attachment = new HashMap<>();
            attachment.put("filename", filename);
            attachment.put("content", base64Content);

            Map<String, Object> payload = new HashMap<>();
            payload.put("from", "FOOTBALLHUB <" + fromEmail + ">");
            payload.put("to", List.of(toEmail));
            payload.put("subject", "Booking Confirmed - FOOTBALLHUB (Receipt Attached)");
            payload.put("text", body);
            payload.put("attachments", List.of(attachment));

            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("Email with receipt sent successfully to " + toEmail + ". Response: " + response.body());
            } else {
                System.err.println("Failed to send email with receipt to " + toEmail + ". Status: " + response.statusCode() + ", Response: " + response.body());
                throw new RuntimeException("Failed to send receipt email: Status " + response.statusCode() + ", Response " + response.body());
            }
        } catch (Exception e) {
            System.err.println("Error sending email with receipt: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to send receipt email: " + e.getMessage(), e);
        }
    }

    // 2. Welcome Email (Registration)
    public void sendWelcomeEmail(String toEmail, String userName) {
        String subject = "Welcome to FOOTBALLHUB!";
        String body = "Hi " + userName + ",\n\n" +
                "Welcome to the FOOTBALLHUB community! Your account has been successfully created.\n" +
                "You can now log in and start booking your favorite football pitches.\n\n" +
                "Regards,\n" +
                "FOOTBALLHUB Manager";

        sendSimpleEmail(toEmail, subject, body);
    }

    // 3. Password Reset Email (UPDATED)
    public void sendPasswordReset(String toEmail, String token) {
        String subject = "Password Reset Request - FOOTBALLHUB";
        String resetLink = "https://s72510.up.railway.app/reset-password?token=" + token;

        String body = "Hello,\n\n" +
                "We received a request to reset your password.\n" +
                "Click the link below to reset your password:\n\n" +
                resetLink + "\n\n" +
                "This link will expire in 15 minutes.\n\n" +
                "If you did not request this, please ignore this email.\n\n" +
                "Regards,\n" +
                "FOOTBALLHUB Security Team";

        sendSimpleEmail(toEmail, subject, body);
    }
}

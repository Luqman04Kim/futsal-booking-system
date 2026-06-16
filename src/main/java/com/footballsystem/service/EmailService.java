package com.footballsystem.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;

import com.footballsystem.model.Booking;
import jakarta.mail.internet.MimeMessage;

import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    // Core method to send simple text emails
    public void sendSimpleEmail(String toEmail, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            // Sets the sender. Note: Gmail SMTP will usually overwrite this with the
            // authenticated account email.
            message.setFrom("FOOTBALLHUB Manager <" + senderEmail + ">");
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);
            System.out.println("Email sent successfully to " + toEmail);
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
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("FOOTBALLHUB Manager <" + senderEmail + ">");
            helper.setTo(toEmail);
            helper.setSubject("Booking Confirmed - FOOTBALLHUB (Receipt Attached)");

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

            helper.setText(body);

            // Attach PDF
            String filename = "Receipt_" + booking.getBookingId() + ".pdf";
            helper.addAttachment(filename, new ByteArrayResource(pdfBytes));

            mailSender.send(message);
            System.out.println("Email with receipt sent successfully to " + toEmail);
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
        String resetLink = "http://localhost:8082/reset-password?token=" + token;

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

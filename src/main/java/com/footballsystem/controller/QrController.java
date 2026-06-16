package com.footballsystem.controller;

import com.footballsystem.model.Booking;
import com.footballsystem.repository.BookingRepository;
import com.google.zxing.BarcodeFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
public class QrController {

    @Autowired
    private BookingRepository bookingRepository;

    /**
     * GET /qr/{bookingId}
     * Returns a QR code PNG image for the given booking.
     * The QR encodes the booking's unique qrToken so staff can verify it.
     */
    @GetMapping("/qr/{bookingId}")
    @ResponseBody
    public ResponseEntity<byte[]> generateQr(@PathVariable Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null) {
            return ResponseEntity.notFound().build();
        }

        // If already checked in, serve a "QR Used" image instead of the real token
        if (booking.isCheckedIn()) {
            return buildUsedQrImage();
        }

        // Ensure legacy bookings (no qrToken yet) get a token assigned
        if (booking.getQrToken() == null || booking.getQrToken().isEmpty()) {
            booking.setQrToken(UUID.randomUUID().toString());
            bookingRepository.save(booking);
        }

        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.MARGIN, 2);

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(booking.getQrToken(), BarcodeFormat.QR_CODE, 250, 250, hints);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);
        } catch (WriterException | java.io.IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Generates a small "QR USED" placeholder image for already-checked-in bookings
     */
    private ResponseEntity<byte[]> buildUsedQrImage() {
        try {
            // Encode a literal "USED" string — results in a scannable but unrecognised
            // token
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.MARGIN, 2);
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode("QR_EXPIRED_ALREADY_USED", BarcodeFormat.QR_CODE, 250, 250, hints);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            // Add a custom header so the customer page can detect it
            headers.add("X-QR-Status", "USED");
            return new ResponseEntity<>(baos.toByteArray(), headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * POST /staff/verify-qr
     * Staff submits a scanned QR token. If valid, marks the booking as checked-in.
     */
    @PostMapping("/staff/verify-qr")
    @ResponseBody
    public Map<String, Object> verifyQr(@RequestBody Map<String, String> body, HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        String role = (String) session.getAttribute("role");
        if (role == null || !role.equalsIgnoreCase("STAFF")) {
            response.put("success", false);
            response.put("message", "Not authorized");
            return response;
        }

        String token = body.get("token");
        if (token == null || token.isBlank()) {
            response.put("success", false);
            response.put("message", "Invalid QR code");
            return response;
        }

        String trimmedToken = token.trim();
        Booking booking = bookingRepository.findAll().stream()
                .filter(b -> trimmedToken.equals(b.getQrToken()) || (b.getQrToken() != null && b.getQrToken().equals("USED_" + trimmedToken)))
                .findFirst().orElse(null);

        if (booking == null) {
            response.put("success", false);
            response.put("message", "QR Code was invalid");
            return response;
        }

        // Check if has already been checked in
        if (booking.isCheckedIn()) {
            response.put("success", false);
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a, dd MMM yyyy");
            String formattedTime = booking.getCheckedInAt() != null ? booking.getCheckedInAt().format(formatter) : "unknown time";
            response.put("message", "❌ Code already scanned before on " + formattedTime);
            return response;
        }

        // Check if the booking session has ended (expired QR)
        if (booking.getDate() != null && booking.getEndTime() != null) {
            LocalDate today = LocalDate.now();
            LocalTime now = LocalTime.now();
            if (booking.getDate().isBefore(today) ||
                    (booking.getDate().isEqual(today) && booking.getEndTime().isBefore(now))) {
                response.put("success", false);
                response.put("message", "QR Code was invalid");
                return response;
            }
        }

        // Mark as checked in AND burn the token so it can never be reused
        booking.setCheckedIn(true);
        booking.setCheckedInAt(LocalDateTime.now());
        booking.setQrToken("USED_" + trimmedToken); // invalidate old token
        bookingRepository.save(booking);

        response.put("success", true);
        response.put("message", "✅ Check-in successful!");
        response.put("bookingId", booking.getBookingId());
        response.put("customer", booking.getUser() != null ? booking.getUser().getUsername() : "Unknown");
        response.put("field", booking.getField() != null ? booking.getField().getName() : "Unknown");
        response.put("date", booking.getDate() != null ? booking.getDate().toString() : "");
        response.put("time", (booking.getStartTime() != null ? booking.getStartTime().toString() : "") + " – " +
                (booking.getEndTime() != null ? booking.getEndTime().toString() : ""));
        response.put("status", booking.getStatus());
        response.put("branch", booking.getField() != null && booking.getField().getBranch() != null
                ? booking.getField().getBranch().getName()
                : "");
        return response;
    }

    /**
     * GET /staff/scan-qr
     * Shows the camera-based QR scan page for staff.
     */
    @GetMapping("/staff/scan-qr")
    public String showScanPage(HttpSession session, Model model) {
        String role = (String) session.getAttribute("role");
        if (role == null || !role.equalsIgnoreCase("STAFF"))
            return "redirect:/login";
        return "staff-scan-qr";
    }
}

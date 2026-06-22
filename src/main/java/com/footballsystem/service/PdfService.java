package com.footballsystem.service;

import com.footballsystem.model.Booking;
import com.footballsystem.model.InventoryItem;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import javax.imageio.ImageIO;

@Service
public class PdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("hh:mm a");

    public byte[] generateReceipt(Booking booking) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            Document document = new Document(PageSize.A4);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            document.open();

            // Define fonts
            Font titleFont = new Font(Font.HELVETICA, 24, Font.BOLD, new Color(0, 53, 128));
            Font headerFont = new Font(Font.HELVETICA, 14, Font.BOLD, Color.BLACK);
            Font normalFont = new Font(Font.HELVETICA, 12, Font.NORMAL);
            Font boldFont = new Font(Font.HELVETICA, 12, Font.BOLD);
            Font smallFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.GRAY);

            // LOGO
            try {
                Image logo = Image.getInstance("LOGO WEB.png");
                logo.setAlignment(Element.ALIGN_CENTER);
                float width = logo.getWidth();
                float height = logo.getHeight();
                float targetHeight = 60f;
                float targetWidth = (width / height) * targetHeight;
                logo.scaleAbsolute(targetWidth, targetHeight);
                document.add(logo);
                
                Paragraph space = new Paragraph(" ", new Font(Font.HELVETICA, 8));
                space.setAlignment(Element.ALIGN_CENTER);
                document.add(space);
            } catch (Exception logoEx) {
                System.err.println("Could not load receipt logo: " + logoEx.getMessage());
            }

            // HEADER
            PdfPTable headerTable = new PdfPTable(1);
            headerTable.setWidthPercentage(100);

            PdfPCell titleCell = new PdfPCell();
            titleCell.setBorder(Rectangle.NO_BORDER);
            titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            titleCell.setPaddingBottom(20);

            Paragraph title = new Paragraph("FOOTBALLHUB", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            titleCell.addElement(title);

            Paragraph subtitle = new Paragraph("Official Booking Receipt",
                    new Font(Font.HELVETICA, 12, Font.ITALIC, Color.GRAY));
            subtitle.setAlignment(Element.ALIGN_CENTER);
            titleCell.addElement(subtitle);

            headerTable.addCell(titleCell);
            document.add(headerTable);

            // RECEIPT ID & DATE
            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);
            infoTable.setSpacingBefore(10);
            infoTable.setSpacingAfter(20);

            PdfPCell receiptIdCell = new PdfPCell(new Phrase("Receipt #" + booking.getBookingId(), headerFont));
            receiptIdCell.setBorder(Rectangle.NO_BORDER);
            receiptIdCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            infoTable.addCell(receiptIdCell);

            String dateStr = booking.getCreatedAt() != null
                    ? booking.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    : "N/A";
            PdfPCell dateCell = new PdfPCell(new Phrase("Date: " + dateStr, smallFont));
            dateCell.setBorder(Rectangle.NO_BORDER);
            dateCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            infoTable.addCell(dateCell);

            document.add(infoTable);

            // DIVIDER
            PdfPTable divider = new PdfPTable(1);
            divider.setWidthPercentage(100);
            PdfPCell dividerCell = new PdfPCell();
            dividerCell.setBorderWidthTop(2);
            dividerCell.setBorderWidthBottom(0);
            dividerCell.setBorderWidthLeft(0);
            dividerCell.setBorderWidthRight(0);
            dividerCell.setBorderColorTop(new Color(0, 53, 128));
            dividerCell.setFixedHeight(10);
            divider.addCell(dividerCell);
            document.add(divider);

            // CUSTOMER INFO
            document.add(new Paragraph("Customer Information", headerFont));
            document.add(new Paragraph(" ", smallFont));

            PdfPTable customerTable = new PdfPTable(2);
            customerTable.setWidthPercentage(100);
            customerTable.setWidths(new float[] { 1, 2 });

            addTableRow(customerTable, "Name:", booking.getUser().getUsername(), normalFont, boldFont);
            addTableRow(customerTable, "Email:", booking.getUser().getEmail(), normalFont, boldFont);

            // Declare userPlan once for reuse below
            com.footballsystem.model.MembershipPlan userPlan = booking.getUser().getMembershipPlan();

            if (booking.getUser().isVip()) {
                // Show the plan name if available, otherwise generic "VIP Member"
                String memberLabel = userPlan != null ? userPlan.getName().toUpperCase() + " MEMBER" : "VIP MEMBER";
                addTableRow(customerTable, "Membership:", memberLabel, normalFont, boldFont);
            }

            document.add(customerTable);
            document.add(new Paragraph(" ", smallFont));

            // MEMBERSHIP PLAN BENEFITS (shown only if user has a plan)
            if (userPlan != null) {
                document.add(new Paragraph("Membership Plan: " + userPlan.getName().toUpperCase() + " MEMBER", headerFont));
                document.add(new Paragraph(" ", smallFont));

                PdfPTable planTable = new PdfPTable(2);
                planTable.setWidthPercentage(100);
                planTable.setWidths(new float[]{ 2, 1 });

                if (userPlan.getDiscountPercentage() > 0) {
                    addTableRow(planTable, "\u2713 Field Discount:",
                        userPlan.getDiscountPercentage() + "% off", normalFont, boldFont);
                }

                if (userPlan.isFreeAddonsIncluded()) {
                    addTableRow(planTable, "\u2713 All Add-on Services:", "FREE", normalFont, boldFont);
                } else if (userPlan.getFreeServices() != null && !userPlan.getFreeServices().isEmpty()) {
                    StringBuilder freeList = new StringBuilder();
                    for (int i = 0; i < userPlan.getFreeServices().size(); i++) {
                        freeList.append(userPlan.getFreeServices().get(i).getName());
                        if (i < userPlan.getFreeServices().size() - 1) freeList.append(", ");
                    }
                    addTableRow(planTable, "\u2713 Free Services:", freeList.toString(), normalFont, boldFont);
                }

                document.add(planTable);
                document.add(new Paragraph(" ", smallFont));
            }

            // BOOKING DETAILS
            document.add(new Paragraph("Booking Details", headerFont));
            document.add(new Paragraph(" ", smallFont));

            PdfPTable bookingTable = new PdfPTable(2);
            bookingTable.setWidthPercentage(100);
            bookingTable.setWidths(new float[] { 1, 2 });

            String fieldName = booking.getField() != null ? booking.getField().getName() : "Membership Transaction";
            String branchName = (booking.getField() != null && booking.getField().getBranch() != null)
                    ? booking.getField().getBranch().getName()
                    : "All Branches";

            addTableRow(bookingTable, "Item:", fieldName, normalFont, boldFont);
            addTableRow(bookingTable, "Branch:", branchName, normalFont, boldFont);
            addTableRow(bookingTable, "Date:", booking.getDate() != null ? booking.getDate().format(DATE_FORMAT) : java.time.LocalDate.now().format(DATE_FORMAT), normalFont, boldFont);
            addTableRow(bookingTable, "Time:",
                    booking.getStartTime() != null ? (booking.getStartTime().format(TIME_FORMAT) + " - " + booking.getEndTime().format(TIME_FORMAT)) : "N/A",
                    normalFont, boldFont);
            addTableRow(bookingTable, "Payment Status:",
                    booking.getPaymentStatus() != null ? booking.getPaymentStatus() : "N/A", normalFont, boldFont);

            document.add(bookingTable);
            document.add(new Paragraph(" ", smallFont));

            // PAYMENT SUMMARY
            document.add(new Paragraph("Payment Summary", headerFont));
            document.add(new Paragraph(" ", smallFont));

            PdfPTable priceTable = new PdfPTable(2);
            priceTable.setWidthPercentage(100);
            priceTable.setWidths(new float[] { 3, 1 });

            // Field Price - Calculate from booking info
            double fieldBasePrice = 0.0;
            if (booking.getField() != null && booking.getField().getPricePerHour() != null) {
                fieldBasePrice = booking.getField().getPricePerHour();
            }
            // If we have add-ons, reverse-calculate field price from total
            // Calculate base field price (original price before manager discount)
            double originalFieldPrice = 0.0;
            if (booking.getField() != null && booking.getField().getPricePerHour() != null) {
                originalFieldPrice = booking.getField().getPricePerHour();

                // If using matrix price, we need to fetch it or estimate it (Simplest is to use
                // current logic if not stored)
                // For now, let's assume standard price or recalculate based on logic if needed.
                // But wait, we have price per hour. Let's use logic similar to controller if
                // possible or rely on stored values if we had them.
                // Since we don't store original price in booking, we must check if field has
                // active discount.
            }

            double fieldBasePriceToDisplay = originalFieldPrice;
            boolean hasManagerDiscount = booking.getField() != null && booking.getField().hasActiveDiscount();
            double managerDiscountAmount = 0.0;

            if (hasManagerDiscount) {
                // If there's an active discount, the stored booking price includes it.
                // We want to show Original Price.
                // The 'originalFieldPrice' variable holds the base rate.
            } else {
                if (booking.getField() != null && booking.getField().getPricePerHour() != null) {
                    fieldBasePriceToDisplay = booking.getField().getPricePerHour();
                }
            }

            // Recalculate context based on available info
            if (booking.getField() != null) {
                if (booking.getField().hasActiveDiscount()) {
                    // Current Price in system is discounted.
                    // To get original, we reverse calc: discounted = original * (1 - pct/100)
                    // original = discounted / (1 - pct/100)
                    // However, we have getDiscountedPrice logic.

                    // Let's use the standard PricePerHour as "Original" reference for display
                    // IF it matches the matrix.
                    // Actually, simpler:
                    // 1. Get accurate original price (PriceMatrix or Standard)
                    // Since we can't easily access repo here without autowiring (it is a service,
                    // so we could, but let's avoid complex query if possible).
                    // Alternative: Re-use the logic from Controller? No, we are in Service.
                    // OK, let's just use the Field's pricePerHour and standard Discount logic.

                    if (booking.getField().getPricePerHour() != null) {
                        fieldBasePriceToDisplay = booking.getField().getPricePerHour();
                        double discounted = booking.getField().getDiscountedPrice(fieldBasePriceToDisplay);
                        managerDiscountAmount = fieldBasePriceToDisplay - discounted;
                    }
                } else {
                    if (booking.getField().getPricePerHour() != null) {
                        fieldBasePriceToDisplay = booking.getField().getPricePerHour();
                    }
                }
            }

            if (booking.getField() != null) {
                addPriceRow(priceTable, fieldName + " (Field)", String.format("RM %.2f", fieldBasePriceToDisplay),
                        normalFont);

                if (hasManagerDiscount && managerDiscountAmount > 0) {
                    addPriceRow(priceTable, "Discount (" + booking.getField().getDiscountPercentage() + "%)",
                            String.format("- RM %.2f", managerDiscountAmount), normalFont);
                }
            } else {
                String desc = booking.getPaymentStatus() != null ? booking.getPaymentStatus() : "Membership Plan Subscription";
                addPriceRow(priceTable, desc, String.format("RM %.2f", booking.getPrice() != null ? booking.getPrice() : 0.0), normalFont);
            }

            boolean hasFreeAddons = userPlan != null && userPlan.isFreeAddonsIncluded();
            double planDiscountPercentage = userPlan != null ? userPlan.getDiscountPercentage() : 0;
            String planName = userPlan != null ? userPlan.getName() : "VIP";

            // Build set of free service IDs from the plan's specific freeServices list
            Set<Long> freeServiceIds = new HashSet<>();
            if (userPlan != null && userPlan.getFreeServices() != null) {
                for (com.footballsystem.model.InventoryItem si : userPlan.getFreeServices()) {
                    freeServiceIds.add(si.getId());
                }
            }

            double addonsTotal = 0.0;

            // Dynamic Inventory Addons — check per-item if it is free via the plan
            if (booking.getInventoryItems() != null) {
                for (InventoryItem item : booking.getInventoryItems()) {
                    boolean itemIsFree = hasFreeAddons || freeServiceIds.contains(item.getId());
                    if (itemIsFree) {
                        // Show original price struck through with (FREE) label
                        addPriceRow(priceTable,
                            item.getName() + " (" + planName + " Member - FREE)",
                            "RM 0.00", normalFont);
                    } else {
                        addPriceRow(priceTable, item.getName(),
                            String.format("RM %.2f", item.getEffectivePrice()), normalFont);
                        addonsTotal += item.getEffectivePrice();
                    }
                }
            }

            // Plan Discount row if applicable
            if (booking.getUser().isVip() || (booking.getUser().getMembershipPlan() != null && planDiscountPercentage > 0)) {
                double pct = planDiscountPercentage > 0 ? planDiscountPercentage : (booking.getUser().isVip() ? 10.0 : 0.0);
                double subtotal = fieldBasePriceToDisplay - managerDiscountAmount + addonsTotal;
                double discount = subtotal * (pct / 100.0);
                addPriceRow(priceTable, planName + " Discount (" + pct + "%)", String.format("- RM %.2f", discount), normalFont);
            }

            // Total
            PdfPCell totalLabelCell = new PdfPCell(new Phrase("Total Paid", new Font(Font.HELVETICA, 14, Font.BOLD)));
            totalLabelCell.setBorderWidthTop(1);
            totalLabelCell.setBorderWidthBottom(0);
            totalLabelCell.setBorderWidthLeft(0);
            totalLabelCell.setBorderWidthRight(0);
            totalLabelCell.setPaddingTop(10);
            priceTable.addCell(totalLabelCell);

            String totalPrice = booking.getPrice() != null ? String.format("RM %.2f", booking.getPrice()) : "RM 0.00";
            PdfPCell totalValueCell = new PdfPCell(
                    new Phrase(totalPrice, new Font(Font.HELVETICA, 14, Font.BOLD, new Color(0, 128, 0))));
            totalValueCell.setBorderWidthTop(1);
            totalValueCell.setBorderWidthBottom(0);
            totalValueCell.setBorderWidthLeft(0);
            totalValueCell.setBorderWidthRight(0);
            totalValueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalValueCell.setPaddingTop(10);
            priceTable.addCell(totalValueCell);

            document.add(priceTable);

            // QR CODE SECTION
            document.add(new Paragraph(" ", normalFont));

            // Ensure qrToken exists (for legacy bookings)
            String token = booking.getQrToken();
            if (token == null || token.isEmpty()) {
                token = UUID.randomUUID().toString();
                booking.setQrToken(token);
                // Note: we can't save here without the repo, but the token is still valid for
                // display
            }

            try {
                Map<EncodeHintType, Object> qrHints = new HashMap<>();
                qrHints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
                qrHints.put(EncodeHintType.MARGIN, 1);

                QRCodeWriter qrWriter = new QRCodeWriter();
                BitMatrix bitMatrix = qrWriter.encode(token, BarcodeFormat.QR_CODE, 150, 150, qrHints);

                // Convert BitMatrix → BufferedImage → byte[] PNG
                BufferedImage qrBuffered = MatrixToImageWriter.toBufferedImage(bitMatrix);
                ByteArrayOutputStream qrBaos = new ByteArrayOutputStream();
                ImageIO.write(qrBuffered, "PNG", qrBaos);

                // Wrap in lowagie Image and center it
                Image qrImage = Image.getInstance(qrBaos.toByteArray());
                qrImage.setAlignment(Element.ALIGN_CENTER);
                qrImage.scaleAbsolute(130, 130);

                // Section header
                Paragraph qrHeader = new Paragraph("Attendance QR Code", headerFont);
                qrHeader.setAlignment(Element.ALIGN_CENTER);
                document.add(qrHeader);

                Paragraph qrNote = new Paragraph(
                        "Show this QR code to the staff upon arrival to verify your attendance.", smallFont);
                qrNote.setAlignment(Element.ALIGN_CENTER);
                document.add(qrNote);
                document.add(new Paragraph(" ", smallFont));

                document.add(qrImage);

                Paragraph qrCaution = new Paragraph(
                        "\u26a0 This QR code is valid for ONE scan only. Do not share.",
                        new Font(Font.HELVETICA, 9, Font.ITALIC, new Color(165, 0, 68)));
                qrCaution.setAlignment(Element.ALIGN_CENTER);
                document.add(qrCaution);

            } catch (Exception qrEx) {
                // If QR fails, just show the token text as fallback
                Paragraph qrFallback = new Paragraph("QR Token: " + token, smallFont);
                qrFallback.setAlignment(Element.ALIGN_CENTER);
                document.add(qrFallback);
            }

            // FOOTER
            document.add(new Paragraph(" ", normalFont));
            document.add(new Paragraph(" ", normalFont));

            Paragraph footer = new Paragraph("Thank you for choosing FOOTBALLHUB!",
                    new Font(Font.HELVETICA, 12, Font.ITALIC, Color.GRAY));
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            Paragraph footerNote = new Paragraph("Scan the QR code above upon arrival. One-time use only.", smallFont);
            footerNote.setAlignment(Element.ALIGN_CENTER);
            document.add(footerNote);

            document.close();
            writer.close();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error generating PDF receipt: " + e.getMessage());
        }

        return baos.toByteArray();
    }

    private void addTableRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPaddingBottom(5);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPaddingBottom(5);
        table.addCell(valueCell);
    }

    private void addPriceRow(PdfPTable table, String item, String price, Font font) {
        PdfPCell itemCell = new PdfPCell(new Phrase(item, font));
        itemCell.setBorder(Rectangle.NO_BORDER);
        itemCell.setPaddingBottom(5);
        table.addCell(itemCell);

        PdfPCell priceCell = new PdfPCell(new Phrase(price, font));
        priceCell.setBorder(Rectangle.NO_BORDER);
        priceCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        priceCell.setPaddingBottom(5);
        table.addCell(priceCell);
    }
}

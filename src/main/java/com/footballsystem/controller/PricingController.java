package com.footballsystem.controller;

import com.footballsystem.model.Field;
import com.footballsystem.model.PriceMatrix;
import com.footballsystem.repository.FieldRepository;
import com.footballsystem.repository.PriceMatrixRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/pricing")
public class PricingController {

    @Autowired
    private FieldRepository fieldRepository;
    @Autowired
    private PriceMatrixRepository priceMatrixRepository;

    // Fixed session start times (08:00 to 00:00, assuming 2-hour slots)
    private static final int[] START_HOURS = { 8, 10, 12, 14, 16, 18, 20, 22, 0 };

    private boolean isManager(HttpSession session) {
        String role = (String) session.getAttribute("role");
        return role != null && role.equals("MANAGER");
    }

    // 1. SHOW PRICE MATRIX FORM (Redirects to dedicated view or handled in modal)
    // This endpoint prepares the data needed to render the price grid.
    @GetMapping("/matrix/{fieldId}")
    public String showPriceMatrix(@PathVariable Long fieldId,
                                  @RequestParam(required = false) String saved,
                                  HttpSession session, Model model) {
        if (!isManager(session))
            return "redirect:/login";

        Field field = fieldRepository.findById(fieldId).orElse(null);
        if (field == null)
            return "redirect:/manager/fields";

        List<PriceMatrix> existingPrices = priceMatrixRepository.findByField(field);

        Map<String, Map<Integer, Double>> priceMap = new HashMap<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            priceMap.put(day.name(), new HashMap<>());
        }
        for (PriceMatrix pm : existingPrices) {
            int hour = pm.getStartTime().getHour();
            priceMap.get(pm.getDayOfWeek().name()).put(hour, pm.getPrice());
        }

        model.addAttribute("field", field);
        model.addAttribute("priceMap", priceMap);
        model.addAttribute("startHours", START_HOURS);
        model.addAttribute("days", DayOfWeek.values());
        if ("1".equals(saved)) {
            model.addAttribute("success", "Price matrix saved successfully!");
        }

        return "price-matrix";
    }

    // 2. SAVE PRICE MATRIX
    // Processes the form submission where inputs are named like "price_MONDAY_8"
    @PostMapping("/matrix/save")
    public String savePriceMatrix(@RequestParam Long fieldId,
            @RequestParam Map<String, String> allParams,
            HttpSession session) {
        if (!isManager(session))
            return "redirect:/login";

        Field field = fieldRepository.findById(fieldId).orElse(null);
        if (field == null)
            return "redirect:/manager/fields";

        // Iterate through all form parameters
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            // Check if parameter key starts with "price_"
            if (entry.getKey().startsWith("price_") && !entry.getValue().isEmpty()) {
                try {
                    // Parse key: "price_MONDAY_8" -> ["price", "MONDAY", "8"]
                    String[] parts = entry.getKey().split("_");
                    if (parts.length == 3) {
                        DayOfWeek day = DayOfWeek.valueOf(parts[1]);
                        int hour = Integer.parseInt(parts[2]);
                        double price = Double.parseDouble(entry.getValue());

                        LocalTime startTime = LocalTime.of(hour, 0);

                        // Check if a price entry already exists for this slot
                        Optional<PriceMatrix> existing = priceMatrixRepository
                                .findByFieldAndDayOfWeekAndStartTime(field, day, startTime);

                        PriceMatrix pm = existing.orElse(new PriceMatrix());
                        pm.setField(field);
                        pm.setDayOfWeek(day);
                        pm.setStartTime(startTime);
                        pm.setPrice(price);

                        priceMatrixRepository.save(pm);
                    }
                } catch (Exception e) {
                    // Log error or skip invalid entries (e.g. empty strings or parse errors)
                    System.err.println("Error saving price for " + entry.getKey() + ": " + e.getMessage());
                }
            }
        }

        return "redirect:/pricing/matrix/" + fieldId + "?saved=1";
    }

    // 3. SAVE DISCOUNT FOR FIELD
    @PostMapping("/discount/save")
    public String saveDiscount(@RequestParam Long fieldId,
            @RequestParam Integer discountPercentage,
            HttpSession session,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        if (!isManager(session))
            return "redirect:/login";

        Field field = fieldRepository.findById(fieldId).orElse(null);
        if (field == null) {
            redirect.addFlashAttribute("error", "Field not found");
            return "redirect:/manager/fields";
        }

        // Validate discount percentage
        if (discountPercentage < 1 || discountPercentage > 99) {
            redirect.addFlashAttribute("error", "Discount must be between 1% and 99%");
            return "redirect:/pricing/matrix/" + fieldId;
        }

        field.setDiscountPercentage(discountPercentage);
        field.setDiscountActive(true);
        fieldRepository.save(field);

        // Update all PriceMatrix entries for this field with the discounted price
        List<PriceMatrix> matrixEntries = priceMatrixRepository.findByField(field);
        double multiplier = 1.0 - (discountPercentage / 100.0);
        for (PriceMatrix pm : matrixEntries) {
            // If a basePrice was already stored (from a previous discount), use it as the source
            // Otherwise save the current price as basePrice first
            if (pm.getBasePrice() == null) {
                pm.setBasePrice(pm.getPrice());
            }
            // Apply discount to the stored basePrice so stacking is not an issue
            if (pm.getBasePrice() != null) {
                pm.setPrice(Math.round(pm.getBasePrice() * multiplier * 100.0) / 100.0);
            }
            priceMatrixRepository.save(pm);
        }

        redirect.addFlashAttribute("success", "Discount of " + discountPercentage + "% applied successfully! Price matrix updated.");
        return "redirect:/pricing/matrix/" + fieldId;
    }

    // 4. CLEAR/REMOVE DISCOUNT FROM FIELD
    @PostMapping("/discount/clear")
    public String clearDiscount(@RequestParam Long fieldId,
            HttpSession session,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        if (!isManager(session))
            return "redirect:/login";

        Field field = fieldRepository.findById(fieldId).orElse(null);
        if (field == null) {
            redirect.addFlashAttribute("error", "Field not found");
            return "redirect:/manager/fields";
        }

        field.setDiscountActive(false);
        field.setDiscountPercentage(null);
        fieldRepository.save(field);

        // Restore all PriceMatrix entries back to their original basePrice
        List<PriceMatrix> matrixEntries = priceMatrixRepository.findByField(field);
        for (PriceMatrix pm : matrixEntries) {
            if (pm.getBasePrice() != null) {
                pm.setPrice(pm.getBasePrice());
                pm.setBasePrice(null); // Clear stored original so next discount starts fresh
            }
            priceMatrixRepository.save(pm);
        }

        redirect.addFlashAttribute("success", "Discount removed. Price matrix restored to original prices.");
        return "redirect:/pricing/matrix/" + fieldId;
    }
}

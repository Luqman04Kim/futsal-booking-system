package com.footballsystem.controller;

import com.footballsystem.model.InventoryItem;
import com.footballsystem.repository.InventoryItemRepository;
import com.footballsystem.repository.BookingRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/manager/inventory")
public class InventoryController {

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private boolean isManager(HttpSession session) {
        Object role = session.getAttribute("role");
        return role != null && "MANAGER".equals(role.toString());
    }

    // ────────────────────────────────────────────────────────────────
    // LIST  GET /manager/inventory
    // ────────────────────────────────────────────────────────────────
    @GetMapping
    public String listInventory(HttpSession session, Model model) {
        if (!isManager(session)) return "redirect:/login";

        List<InventoryItem> items = inventoryItemRepository.findAll();
        items.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        model.addAttribute("items", items);
        model.addAttribute("totalItems", items.size());
        model.addAttribute("activeItems", items.stream().filter(InventoryItem::isActive).count());
        model.addAttribute("freeItems",   items.stream().filter(InventoryItem::isFree).count());
        model.addAttribute("discountedItems", items.stream().filter(InventoryItem::hasDiscount).count());
        return "inventory";
    }

    // ────────────────────────────────────────────────────────────────
    // ADD  POST /manager/inventory/add
    // ────────────────────────────────────────────────────────────────
    @PostMapping("/add")
    public String addItem(@RequestParam String name,
                          @RequestParam(required = false) String description,
                          @RequestParam Double basePrice,
                          @RequestParam(required = false) Double discountedPrice,
                          @RequestParam(defaultValue = "false") boolean free,
                          @RequestParam(required = false, defaultValue = "fas fa-box") String iconClass,
                          HttpSession session,
                          RedirectAttributes ra) {
        if (!isManager(session)) return "redirect:/login";

        InventoryItem item = new InventoryItem();
        item.setName(name.trim());
        item.setDescription(description != null ? description.trim() : null);
        item.setBasePrice(basePrice);
        item.setFree(free);
        item.setIconClass(iconClass != null && !iconClass.isBlank() ? iconClass : "fas fa-box");
        item.setActive(true);

        if (!free && discountedPrice != null && discountedPrice >= 0 && discountedPrice < basePrice) {
            item.setDiscountedPrice(discountedPrice);
        } else {
            item.setDiscountedPrice(null);
        }

        inventoryItemRepository.save(item);
        ra.addFlashAttribute("success", "\"" + item.getName() + "\" added successfully.");
        return "redirect:/manager/inventory";
    }

    // ────────────────────────────────────────────────────────────────
    // EDIT (GET modal data) – returns redirect because modal is inline
    // EDIT  POST /manager/inventory/edit/{id}
    // ────────────────────────────────────────────────────────────────
    @PostMapping("/edit/{id}")
    public String editItem(@PathVariable Long id,
                           @RequestParam String name,
                           @RequestParam(required = false) String description,
                           @RequestParam Double basePrice,
                           @RequestParam(required = false) Double discountedPrice,
                           @RequestParam(defaultValue = "false") boolean free,
                           @RequestParam(required = false) String iconClass,
                           HttpSession session,
                           RedirectAttributes ra) {
        if (!isManager(session)) return "redirect:/login";

        InventoryItem item = inventoryItemRepository.findById(id).orElse(null);
        if (item == null) {
            ra.addFlashAttribute("error", "Item not found.");
            return "redirect:/manager/inventory";
        }

        item.setName(name.trim());
        item.setDescription(description != null ? description.trim() : null);
        item.setBasePrice(basePrice);
        item.setFree(free);
        if (iconClass != null && !iconClass.isBlank()) item.setIconClass(iconClass);

        if (free) {
            item.setDiscountedPrice(null);
        } else if (discountedPrice != null && discountedPrice >= 0 && discountedPrice < basePrice) {
            item.setDiscountedPrice(discountedPrice);
        } else {
            item.setDiscountedPrice(null);
        }

        inventoryItemRepository.save(item);
        ra.addFlashAttribute("success", "\"" + item.getName() + "\" updated successfully.");
        return "redirect:/manager/inventory";
    }

    // ────────────────────────────────────────────────────────────────
    // TOGGLE ACTIVE  POST /manager/inventory/toggle/{id}
    // ────────────────────────────────────────────────────────────────
    @PostMapping("/toggle/{id}")
    public String toggleActive(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        if (!isManager(session)) return "redirect:/login";

        InventoryItem item = inventoryItemRepository.findById(id).orElse(null);
        if (item != null) {
            item.setActive(!item.isActive());
            inventoryItemRepository.save(item);
            ra.addFlashAttribute("success", "\"" + item.getName() + "\" is now " + (item.isActive() ? "active" : "hidden") + ".");
        }
        return "redirect:/manager/inventory";
    }

    // ────────────────────────────────────────────────────────────────
    // DELETE  POST /manager/inventory/delete/{id}
    // ────────────────────────────────────────────────────────────────
    @PostMapping("/delete/{id}")
    public String deleteItem(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        if (!isManager(session)) return "redirect:/login";

        inventoryItemRepository.findById(id).ifPresent(item -> {
            // Remove associations in join table first to prevent foreign key errors
            bookingRepository.deleteInventoryItemAssociations(id);

            ra.addFlashAttribute("success", "\"" + item.getName() + "\" deleted.");
            inventoryItemRepository.delete(item);
        });
        return "redirect:/manager/inventory";
    }

    // ────────────────────────────────────────────────────────────────
    // PUBLIC API – used by Customer Booking Page (no auth required)
    // GET /api/inventory  →  returns active items as JSON
    // ────────────────────────────────────────────────────────────────
    @GetMapping("/api/active")
    @ResponseBody
    public List<InventoryItem> getActiveItemsUnderManager() {
        return inventoryItemRepository.findByActiveTrueOrderByCreatedAtDesc();
    }
}

package com.footballsystem.config;

import com.footballsystem.model.InventoryItem;
import com.footballsystem.model.User;
import com.footballsystem.repository.InventoryItemRepository;
import com.footballsystem.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final InventoryItemRepository inventoryItemRepository;

    public DataInitializer(UserRepository userRepository,
                           InventoryItemRepository inventoryItemRepository) {
        this.userRepository = userRepository;
        this.inventoryItemRepository = inventoryItemRepository;
    }

    @Override
    public void run(String... args) throws Exception {

        // ── Default manager account ───────────────────────────────
        String managerEmail = "manager@field.com";
        if (userRepository.findByEmail(managerEmail).isEmpty()) {
            User manager = new User();
            manager.setUsername("manager");
            manager.setEmail(managerEmail);
            manager.setPassword("manager123");
            manager.setRole("MANAGER");
            manager.setPhoneNumber("0123456789");
            manager.setImageUrl("/img/undraw_profile.svg");
            userRepository.save(manager);
            System.out.println("✅ Created default manager user: " + managerEmail + " / manager123");
        } else {
            System.out.println("ℹ️  Manager user already exists: " + managerEmail);
        }

        // ── Default extra services (inventory) ────────────────────
        if (inventoryItemRepository.count() == 0) {

            // 1. Hire Photographer — RM 200
            InventoryItem photographer = new InventoryItem();
            photographer.setName("Hire Photographer");
            photographer.setDescription("Professional match coverage");
            photographer.setBasePrice(200.0);
            photographer.setFree(false);
            photographer.setIconClass("fas fa-camera");
            photographer.setActive(true);
            inventoryItemRepository.save(photographer);

            // 2. Rent Jerseys — RM 50
            InventoryItem jerseys = new InventoryItem();
            jerseys.setName("Rent Jerseys");
            jerseys.setDescription("Full set for both teams");
            jerseys.setBasePrice(50.0);
            jerseys.setFree(false);
            jerseys.setIconClass("fas fa-tshirt");
            jerseys.setActive(true);
            inventoryItemRepository.save(jerseys);

            // 3. Referee Included — Free
            InventoryItem referee = new InventoryItem();
            referee.setName("Referee Included");
            referee.setDescription("Free for 3 people");
            referee.setBasePrice(0.0);
            referee.setFree(true);
            referee.setIconClass("fas fa-user-shield");
            referee.setActive(true);
            inventoryItemRepository.save(referee);

            System.out.println("✅ Seeded 3 default extra services (Photographer, Jerseys, Referee).");
        } else {
            System.out.println("ℹ️  Inventory already has " + inventoryItemRepository.count() + " item(s), skipping seed.");
        }
    }
}

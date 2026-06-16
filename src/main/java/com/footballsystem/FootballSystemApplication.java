package com.footballsystem;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.footballsystem.model.MembershipPlan;
import com.footballsystem.model.User;
import com.footballsystem.repository.MembershipPlanRepository;
import com.footballsystem.repository.UserRepository;

import java.util.List;

@SpringBootApplication
@EnableScheduling
public class FootballSystemApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(FootballSystemApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(FootballSystemApplication.class, args);
    }

    @Bean
    public CommandLineRunner dataMigrationRunner(
            UserRepository userRepository,
            MembershipPlanRepository planRepository) {

        return args -> {

            List<User> legacyVips = userRepository.findAll().stream()
                    .filter(u -> u.isVip() && u.getMembershipPlan() == null)
                    .toList();

            if (!legacyVips.isEmpty()) {

                MembershipPlan defaultVipPlan = planRepository.findAll().stream()
                        .filter(p -> p.getName().equalsIgnoreCase("VIP"))
                        .findFirst()
                        .orElseGet(() -> {
                            MembershipPlan newPlan = new MembershipPlan(
                                    "VIP",
                                    "Legacy VIP Membership (10% Off Field Price)",
                                    1000.00,
                                    10,
                                    false,
                                    false);

                            return planRepository.save(newPlan);
                        });

                for (User user : legacyVips) {
                    user.setMembershipPlan(defaultVipPlan);
                    userRepository.save(user);
                }

                System.out.println("Migrated " + legacyVips.size()
                        + " legacy VIP users to the new Membership Plan system.");
            }
        };
    }
}
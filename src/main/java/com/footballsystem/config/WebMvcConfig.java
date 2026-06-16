package com.footballsystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${upload.dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        try {
            // --- 1. Serve NEW uploads from external directory (/uploads/**) ---
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            // Use Path.toUri() to get a properly formatted file URI (works on Windows + Linux)
            String uploadLocation = uploadPath.toUri().toString();
            if (!uploadLocation.endsWith("/")) {
                uploadLocation += "/";
            }
            registry.addResourceHandler("/uploads/**")
                    .addResourceLocations(uploadLocation);

            // --- 2. Also serve OLD /img/** images from src/main/resources/static/img ---
            // This keeps existing DB image URLs (/img/branch_xxx.png) working
            Path staticImgPath = Paths.get("src/main/resources/static/img").toAbsolutePath().normalize();
            if (Files.exists(staticImgPath)) {
                String staticImgLocation = staticImgPath.toUri().toString();
                if (!staticImgLocation.endsWith("/")) {
                    staticImgLocation += "/";
                }
                registry.addResourceHandler("/img/**")
                        .addResourceLocations(staticImgLocation, "classpath:/static/img/");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

package com.finallms.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String workDir = System.getProperty("user.dir");
        if (workDir == null || workDir.isBlank())
            workDir = ".";

        // Build absolute path to the uploads folder
        String uploadPath = Paths.get(workDir, "uploads").toAbsolutePath().toString();

        // On Windows, convert backslashes and ensure triple-slash prefix for file: URI
        String resourceLocation = "file:///" + uploadPath.replace("\\", "/") + "/";

        System.out.println("Serving uploads from: " + resourceLocation);

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(resourceLocation);
    }
}

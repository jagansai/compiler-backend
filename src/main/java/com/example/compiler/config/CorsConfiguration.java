package com.example.compiler.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Global CORS configuration driven by `compiler.cors-origins`.
 */
@Configuration
public class CorsConfiguration implements WebMvcConfigurer {

    private final CompilerConfig compilerConfig;

    @Autowired
    public CorsConfiguration(CompilerConfig compilerConfig) {
        this.compilerConfig = compilerConfig;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String origins = compilerConfig.getCorsOrigins();
        if (origins == null || origins.isBlank() || "*".equals(origins.trim())) {
            // allow all
            registry.addMapping("/api/**").allowedOrigins("*");
            return;
        }

        String[] allowed = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        registry.addMapping("/api/**").allowedOrigins(allowed);
    }
}

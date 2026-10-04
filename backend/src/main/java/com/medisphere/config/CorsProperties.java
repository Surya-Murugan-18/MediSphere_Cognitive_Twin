package com.medisphere.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "medisphere.cors")
public record CorsProperties(List<String> allowedOrigins) {
}

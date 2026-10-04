package com.medisphere.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "com.medisphere.repository")
// NOTE: @EnableMongoAuditing is declared here only — NOT in MediSphereApplication
// to avoid BeanDefinitionOverrideException from double-registration.
@org.springframework.data.mongodb.config.EnableMongoAuditing
public class MongoConfig {
    // Connection is fully driven by spring.data.mongodb.uri in application.yml.
    // Time-series collection setup will be added in Phase 5 (vitals streaming).
}

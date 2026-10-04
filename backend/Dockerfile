# =============================================================================
# MediSphere Backend — Multi-stage Dockerfile
# Phase 8 — B8.2
#
# Stage 1 (build): maven:3.9-eclipse-temurin-21
#   - Compiles source, runs tests, produces executable JAR
# Stage 2 (runtime): eclipse-temurin:21-jre-alpine
#   - Minimal JRE image — no build tools, smaller attack surface
#
# Required environment variables (set in docker-compose.yml or .env):
#   MONGODB_URI, MONGODB_DB, JWT_SECRET, KAFKA_BOOTSTRAP_SERVERS,
#   CORS_ALLOWED_ORIGINS, SPRING_PROFILES_ACTIVE
# =============================================================================

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy dependency descriptors first to exploit Docker layer caching.
# Dependencies are only re-downloaded when pom.xml changes.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build — runs unit tests inside the container
COPY src ./src
RUN mvn clean package -DskipTests=false -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Add a non-root user for security
RUN addgroup -S medisphere && adduser -S medisphere -G medisphere

WORKDIR /app

# Copy only the executable JAR from the build stage
COPY --from=build /app/target/medisphere-backend-*.jar app.jar

# Transfer ownership to non-root user
RUN chown medisphere:medisphere app.jar

USER medisphere

# Backend API port
EXPOSE 8080

# JVM tuning for containers:
#   -XX:+UseContainerSupport  — respect Docker CPU/memory limits
#   -XX:MaxRAMPercentage=75   — use up to 75% of container RAM for heap
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]

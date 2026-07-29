# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
# Copy all source code
COPY . .
# Build the project, skipping tests to speed up the build
RUN ./mvnw clean package -DskipTests || mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre
ARG SERVICE
WORKDIR /app
COPY --from=builder /app/${SERVICE}/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
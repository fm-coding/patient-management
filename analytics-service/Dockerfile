FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

COPY pom.xml .

# Download dependencies first to leverage Docker cache
RUN mvn dependency:go-offline -B

COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Use the same Java version for runtime
FROM eclipse-temurin:17-jre-jammy AS runner

WORKDIR /app

# Copy the built jar
COPY --from=builder /app/target/analytics-service-*.jar ./app.jar

EXPOSE 4002

ENTRYPOINT ["java", "-jar", "app.jar"]
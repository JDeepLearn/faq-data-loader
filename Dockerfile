# ===== Stage 1: Build =====
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN ./mvnw -B clean package -DskipTests

# ===== Stage 2: Runtime =====
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/faq-data-loader-*.jar app.jar

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

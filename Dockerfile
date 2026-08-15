# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -ntp clean package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S audit && adduser -S audit -G audit
WORKDIR /app
COPY --from=build /workspace/target/audit-log-service-*.jar app.jar
USER audit
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]

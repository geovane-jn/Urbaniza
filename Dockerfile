FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml ./
COPY src ./src
COPY *.html *.css *.js ./
COPY assets ./assets
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build --chown=10001:10001 /app/target/urbaniza-1.0.0.jar app.jar
RUN mkdir -p /app/.urbaniza && chown 10001:10001 /app/.urbaniza
USER 10001:10001

# Spring Boot usa PORT quando fornecida pelo Render (padrão: 8080).
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]

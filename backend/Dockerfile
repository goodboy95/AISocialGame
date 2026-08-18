# Build stage
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -DskipTests package

# Runtime stage
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --system --gid 10001 aisocial \
    && useradd --system --uid 10001 --gid aisocial --home-dir /nonexistent --shell /usr/sbin/nologin aisocial
COPY --chown=aisocial:aisocial --from=builder /app/target/ai-social-game-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 11031
USER aisocial:aisocial
ENTRYPOINT ["java","-jar","/app/app.jar"]

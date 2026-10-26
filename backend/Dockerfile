# Build stage: compile and package the Spring Boot jar
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

# Run stage: just the JRE and the jar
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/ticket-booking-system-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

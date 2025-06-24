# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /build

COPY . .

RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21

WORKDIR /server

COPY --from=build /build/target/server-1.0.jar .

# This needs to match the port of the server socket
EXPOSE 8081 12345

ENTRYPOINT ["java", "-jar", "server-1.0.jar"]

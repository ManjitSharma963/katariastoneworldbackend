# Multi-stage build for Railway (no pre-built JAR required).
FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /app

COPY pom.xml .
RUN mvn -B dependency:go-offline -DskipTests

COPY src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN addgroup -S kataria && adduser -S kataria -G kataria
USER kataria

COPY --from=build /app/target/katariastoneworld-apis-1.0.0.jar /app/app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

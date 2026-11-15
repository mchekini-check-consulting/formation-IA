# Application Nova Assistant — build multi-étapes
FROM maven:3.9-eclipse-temurin-21 AS construction
WORKDIR /construction
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=construction /construction/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

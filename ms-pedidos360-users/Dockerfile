FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -m -u 1001 backend
COPY --from=build /workspace/target/ms-pedidos360-users-0.0.1-SNAPSHOT.jar app.jar
USER backend
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
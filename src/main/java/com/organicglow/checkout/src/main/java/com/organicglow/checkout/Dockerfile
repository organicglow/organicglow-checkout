# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package

# ---- Run stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
# copy the built JAR (wildcard handles your exact version/name)
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENV PORT=8080
CMD ["java","-jar","/app/app.jar"]

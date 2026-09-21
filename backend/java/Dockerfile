# El contexto de build es la RAÍZ del repo (necesita database/migrations):
#   docker build -f backend/java/Dockerfile -t retail-store-api-java .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/java/pom.xml backend/java/pom.xml
RUN mvn -f backend/java/pom.xml -B -q dependency:go-offline
COPY database/migrations database/migrations
COPY backend/java/src backend/java/src
RUN mvn -f backend/java/pom.xml -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /workspace/backend/java/target/retail-store-api.jar app.jar
# JVM acotada para convivir con Next.js + Postgres en un VPS pequeño
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

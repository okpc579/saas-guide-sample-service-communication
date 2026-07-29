FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl eligibility-service -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
COPY --from=build /workspace/eligibility-service/target/eligibility-service-*.jar /app.jar
USER 10001
ENTRYPOINT ["java","-jar","/app.jar"]

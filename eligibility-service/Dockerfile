FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./mvnw -q -pl eligibility-service -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
COPY --from=build /workspace/eligibility-service/target/eligibility-service-*.jar /app.jar
USER 10001
ENTRYPOINT ["java","-jar","/app.jar"]

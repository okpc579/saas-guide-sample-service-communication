FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./mvnw -q -pl application-service -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
COPY --from=build /workspace/application-service/target/application-service-*.jar /app.jar
USER 10001
ENTRYPOINT ["java","-jar","/app.jar"]

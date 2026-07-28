FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
COPY target/application-service-*.jar /app.jar
USER 10001
ENTRYPOINT ["java","-jar","/app.jar"]

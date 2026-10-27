FROM eclipse-temurin:25-jdk-noble AS probe
WORKDIR /probe
COPY Healthcheck.java .
RUN javac --release 25 Healthcheck.java

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
COPY --from=probe /probe/Healthcheck.class /app/Healthcheck.class
COPY --chown=10001:10001 app.jar /app/app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

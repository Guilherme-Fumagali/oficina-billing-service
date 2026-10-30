FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn package -DskipTests -q

FROM newrelic/newrelic-java-init:9.4.0 AS newrelic

FROM eclipse-temurin:21.0.11_10-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=builder --chown=spring:spring /app/target/*.jar app.jar
COPY --from=newrelic --chown=spring:spring /newrelic-agent.jar /newrelic/newrelic.jar
COPY --chown=spring:spring newrelic.yml /newrelic/newrelic.yml
USER spring
EXPOSE 8080

ARG GIT_COMMIT=local
ENV GIT_COMMIT=${GIT_COMMIT}
ENV JAVA_TOOL_OPTIONS=""
ENV NEW_RELIC_APPLICATION_LOGGING_FORWARDING_ENABLED=false

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "\
if [ -n \"$NEW_RELIC_LICENSE_KEY\" ]; then \
  exec java -javaagent:/newrelic/newrelic.jar -jar app.jar; \
else \
  exec java -jar app.jar; \
fi"]

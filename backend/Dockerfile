# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

# The builder's Maven 3.9.11 version matches .mvn/wrapper/maven-wrapper.properties.
# Resolve build plugins and dependencies before copying sources so this layer is reusable.
COPY pom.xml pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B --no-transfer-progress dependency:go-offline

COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B --no-transfer-progress package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S sentinel && adduser -S -G sentinel -h /opt/sentinel sentinel
WORKDIR /opt/sentinel

# Preserve Quarkus fast-jar layers so dependency-only layers remain cacheable.
COPY --from=build --chown=sentinel:sentinel /workspace/target/quarkus-app/lib/ ./lib/
COPY --from=build --chown=sentinel:sentinel /workspace/target/quarkus-app/*.jar ./
COPY --from=build --chown=sentinel:sentinel /workspace/target/quarkus-app/app/ ./app/
COPY --from=build --chown=sentinel:sentinel /workspace/target/quarkus-app/quarkus/ ./quarkus/

USER sentinel
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]

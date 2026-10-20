# syntax=docker/dockerfile:1

# ---- build ----
FROM eclipse-temurin:25.0.4.1_1-jdk-noble AS build
# The Maven wrapper verifies the pinned .zip distribution; without unzip it would silently switch
# to the .tar.gz and fail the SHA-256 check.
RUN apt-get update \
 && apt-get install -y --no-install-recommends unzip \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /src

COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
COPY plugin-api/pom.xml plugin-api/
COPY app/pom.xml app/
COPY example-plugin/pom.xml example-plugin/
COPY plugin-api/src plugin-api/src
COPY app/src app/src

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -pl app -am package -DskipTests \
 && java -Djarmode=tools -jar app/target/kuratoon.jar extract --layers --launcher --destination /extracted

# ---- runtime ----
FROM eclipse-temurin:25.0.4.1_1-jre-noble

RUN apt-get update \
 && apt-get install -y --no-install-recommends libvips-tools curl \
 && rm -rf /var/lib/apt/lists/* \
 && vips --version

WORKDIR /opt/kuratoon
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./

# Non-root by default; compose overrides with PUID:PGID.
USER 1000:1000
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# umask 027: files 0640, dirs 0750; nginx reads them through the PGID group (docs/spec/08).
ENTRYPOINT ["/bin/sh", "-c", "umask 027 && exec java -XX:MaxRAMPercentage=75 org.springframework.boot.loader.launch.JarLauncher \"$@\"", "kuratoon"]

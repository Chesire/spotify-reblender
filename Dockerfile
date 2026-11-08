# Gradle comes preinstalled in the build image (same version as the wrapper), so the build doesn't download it.
FROM gradle:9.8.2-jdk21 AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts ./
RUN gradle --no-daemon dependencies > /dev/null
COPY src src
RUN gradle --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
# Run as an unprivileged user; only it can read the database (and the encrypted tokens) in /data.
# Fixed UID/GID 10001 so a key file mounted from the host can be chowned to it.
RUN groupadd --system --gid 10001 reblender \
    && useradd --system --uid 10001 --gid reblender --home-dir /app --shell /usr/sbin/nologin reblender \
    && mkdir -p /data \
    && chown reblender:reblender /data \
    && chmod 700 /data
WORKDIR /app
COPY --from=build --chown=reblender:reblender /workspace/build/libs/*.jar app.jar
USER reblender
VOLUME /data
ENV DATABASE_URL=jdbc:h2:file:/data/reblender
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

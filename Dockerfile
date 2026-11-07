FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
VOLUME /data
ENV DATABASE_URL=jdbc:h2:file:/data/reblender
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM gradle:8.5-jdk17-alpine AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY src ./src
# Tests are skipped until the test class is moved to package com.shop and uses a test DB
RUN gradle clean bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app \
    && mkdir -p /app/uploads/photos /app/uploads/home /app/sessions \
    && chown -R app:app /app
COPY --from=build --chown=app:app /app/build/libs/*.jar app.jar
USER app
# Mount a volume at /app/uploads (and /app/sessions) or files are lost on every redeploy
EXPOSE 8765
ENTRYPOINT ["java", "-jar", "app.jar"]
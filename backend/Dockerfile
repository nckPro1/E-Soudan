# syntax=docker/dockerfile:1
# Backend Spring Boot: build bằng Gradle ở tầng 1, chạy bằng JRE gọn ở tầng 2.
# Build: docker build -t soudan-backend backend

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# Chép riêng file cấu hình Gradle trước để tầng tải thư viện được cache khi chỉ đổi mã nguồn
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
 && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar' | head -n 1)" /app.jar

FROM eclipse-temurin:21-jre
# curl dùng cho HEALTHCHECK; chạy bằng người dùng thường, không chạy bằng root
RUN apt-get update && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --home-dir /app app \
 && mkdir -p /app /data/uploads && chown -R app:app /app /data
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# --- build ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# --- run ---
FROM eclipse-temurin:25-jre
WORKDIR /app
# run as an unprivileged user (uid 10001); /data holds uploads and downloads (mounted volumes)
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --home /app --shell /usr/sbin/nologin app \
    && mkdir -p /data/uploads /data/downloads /data/secrets && chown -R app:app /app /data
COPY --from=build --chown=app:app /src/target/*.jar app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-Xmx384m -Dfile.encoding=UTF-8"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

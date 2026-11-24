FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY .mvn ./.mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw \
    && chmod +x mvnw \
    && ./mvnw --batch-mode dependency:go-offline

COPY src ./src
RUN ./mvnw --batch-mode -DskipTests package

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

RUN (command -v curl >/dev/null 2>&1 || (apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*)) \
    && groupadd --system slotly \
    && useradd --system --gid slotly --home-dir /app slotly \
    && mkdir -p /app/logs \
    && chown -R slotly:slotly /app

COPY --from=build --chown=slotly:slotly /workspace/target/*.jar /app/app.jar

USER slotly
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# syntax=docker/dockerfile:1.7
# Builds either Spring Boot service: docker build --build-arg MODULE=herald-api .

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
# Resolve dependencies first so source changes do not invalidate the dependency layer.
COPY pom.xml .
COPY herald-core/pom.xml herald-core/
COPY herald-api/pom.xml herald-api/
COPY herald-worker/pom.xml herald-worker/
ARG MODULE
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl ${MODULE} -am dependency:go-offline -DexcludeGroupIds=dev.herald || true
COPY herald-core herald-core
COPY herald-api herald-api
COPY herald-worker herald-worker
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl ${MODULE} -am package -DskipTests \
 && java -Djarmode=tools -jar ${MODULE}/target/${MODULE}-*.jar extract --layers --launcher --destination /extracted

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S -g 10001 herald && adduser -S -u 10001 -G herald herald
WORKDIR /app
# Layers ordered from least to most frequently changing for better image caching.
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

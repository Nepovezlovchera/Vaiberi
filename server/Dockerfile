FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jdk
# JDK (не JRE), потому что runner компилирует пользовательский код через javac
WORKDIR /app
COPY --from=build /app/target/java-blueprints-server-*.jar app.jar
RUN groupadd --system blueprints && useradd --system --gid blueprints --home-dir /app --create-home blueprints \
    && mkdir -p /app/llm-cache /tmp \
    && chown -R blueprints:blueprints /app /tmp
USER blueprints
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

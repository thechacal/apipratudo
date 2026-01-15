# build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package

RUN PLAYWRIGHT_BROWSERS_PATH=/ms-playwright \
    mvn -q -DskipTests \
    -Dexec.mainClass=com.microsoft.playwright.CLI \
    -Dexec.args="install chromium" \
    -Dexec.classpathScope=test \
    org.codehaus.mojo:exec-maven-plugin:3.1.0:java

# run
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
COPY --from=build /ms-playwright /ms-playwright
COPY --from=build /app/target/*.jar app.jar
ENV PORT=8088
EXPOSE 8088
CMD ["java","-jar","app.jar"]

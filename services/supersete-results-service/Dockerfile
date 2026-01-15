# build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package

RUN PLAYWRIGHT_VERSION=$(mvn -q -DforceStdout help:evaluate -Dexpression=playwright.version) \
    && java -cp "/root/.m2/repository/com/microsoft/playwright/playwright/${PLAYWRIGHT_VERSION}/playwright-${PLAYWRIGHT_VERSION}.jar" \
       com.microsoft.playwright.CLI install chromium

# run
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
COPY --from=build /root/.cache/ms-playwright /ms-playwright
COPY --from=build /app/target/*.jar app.jar
ENV PORT=8092
EXPOSE 8092
CMD ["java","-jar","app.jar"]

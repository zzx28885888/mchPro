# 中文：多阶段构建：第一阶段用 Maven/JDK 编译，运行镜像只包含 JRE 和可执行 JAR。
# English: Multi-stage build: Maven/JDK compiles the app; the runtime image contains only a JRE and executable JAR.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
# 中文：容器镜像构建时打包应用并跳过测试执行；本地开发可单独运行 Maven 测试。
# English: Package the app while skipping test execution in the image build; run Maven tests separately during development.
RUN mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

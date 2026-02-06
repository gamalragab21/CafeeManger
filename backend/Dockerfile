FROM gradle:8.11-jdk17 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle buildFatJar --no-daemon

FROM openjdk:17-slim
EXPOSE 8080
RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/*-all.jar /app/cafeemanger-backend.jar
ENTRYPOINT ["java", "-jar", "/app/cafeemanger-backend.jar"]

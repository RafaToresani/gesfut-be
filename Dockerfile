FROM openjdk:21-jdk
ARG JAR_FILE=target/gesfut-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app_gesfut.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar" ,"/app_gesfut.jar"]
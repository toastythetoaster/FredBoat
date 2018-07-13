FROM openjdk:10-jdk-slim

ENV ENV docker

WORKDIR /opt/FredBoat

COPY fredboat.example.yaml /opt/FredBoat/fredboat.yaml
COPY FredBoat.jar /opt/FredBoat/FredBoat.jar

EXPOSE 1356

ENTRYPOINT ["java", "-Xmx128m", "-jar", "FredBoat.jar"]

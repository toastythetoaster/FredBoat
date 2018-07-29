FROM openjdk:10-jdk-slim

ENV ENV docker

WORKDIR /opt/FredBoat

COPY fredboat.yml.template /opt/FredBoat/fredboat.yml
COPY FredBoat.jar /opt/FredBoat/FredBoat.jar

EXPOSE 1356

ENTRYPOINT ["java", "-Xmx128m", "-jar", "FredBoat.jar"]

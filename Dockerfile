FROM ubuntu:latest

# Install Maven, Git and JDK (required by Maven even if app is native)
RUN apt-get update && \
    apt-get install -y maven git openjdk-21-jdk && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# The JAR name from release-artifacts/mvn-builder.jar
COPY release-artifacts/mvn-builder.jar /app/mvn-builder.jar

# Default workspace location
RUN mkdir /workspaces
ENV BASE_PATH=/workspaces

EXPOSE 3333

ENTRYPOINT ["java", "-jar", "/app/mvn-builder.jar"]

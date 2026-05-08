FROM ubuntu:latest

# Install Maven, Git and JDK (required by Maven even if app is native)
RUN apt-get update && \
    apt-get install -y maven git openjdk-21-jdk && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# The binary name from release-artifacts/mvn-builder-linux-amd64
COPY release-artifacts/mvn-builder-linux-amd64 /app/mvn-builder
RUN chmod +x /app/mvn-builder

# Default workspace location
RUN mkdir /workspaces
ENV BASE_PATH=/workspaces

EXPOSE 3333

ENTRYPOINT ["/app/mvn-builder"]

#!/bin/bash

# run.sh - Local development script for Maven Workspace Manager
set -e

echo "Starting application in development mode..."


# Run with Spring Boot Maven plugin
fuser -k 8080/tcp || true && mvn clean spring-boot:run

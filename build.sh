#!/bin/bash

# build.sh - Local build script for Maven Workspace Manager
set -e

echo "Starting local native build..."

# Clean and compile native image
mvn clean -Pnative native:compile

echo "Build complete!"
echo "You can find the binary at: ./target/mvn-builder"

#!/bin/bash

# Simple Flight Server - Server Mode
# This script runs the Flight server on port 8815

JAR_FILE="target/simple-flight-server-1.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found at $JAR_FILE"
    echo "Please run 'mvn clean package' first to build the JAR."
    exit 1
fi

echo "Starting Flight Server on port 8815..."
echo "Press Ctrl+C to stop the server"
echo ""

java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
     -jar "$JAR_FILE" -server

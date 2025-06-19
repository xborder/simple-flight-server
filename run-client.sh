#!/bin/bash

# Simple Flight Server - Client Mode
# This script runs the Flight client to connect to a server on localhost:8815

JAR_FILE="target/simple-flight-server-1.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found at $JAR_FILE"
    echo "Please run 'mvn clean package' first to build the JAR."
    exit 1
fi

echo "Connecting to Flight Server at localhost:8815..."
echo ""

java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
     -jar "$JAR_FILE"

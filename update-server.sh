#!/bin/bash

# Flight Server Update Script
# This script rebuilds and updates the Flight server on AWS

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Flight Server Update Script${NC}"
echo "=========================="

# Check if JAR exists
JAR_FILE="target/simple-flight-server-1.0-SNAPSHOT.jar"

echo -e "\n${YELLOW}Step 1: Building new server binary...${NC}"
mvn clean package

if [ $? -ne 0 ]; then
    echo -e "${RED}✗ Build failed${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Build successful${NC}"

echo -e "\n${YELLOW}Step 2: Updating AWS deployment...${NC}"
./deploy-to-aws.sh --update-server

echo -e "\n${GREEN}✅ Server update completed!${NC}"
echo ""
echo "Your Flight server on AWS has been updated with the latest code."
echo "The service has been restarted and should be ready to accept connections."

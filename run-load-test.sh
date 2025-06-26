#!/bin/bash

# Convenience script to run NLB load tests

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}🧪 AWS NLB Load Testing Suite${NC}"
echo "============================="
echo ""
echo "Available test options:"
echo "  1. Quick test (5 threads, 30 seconds)"
echo "  2. Standard test (10 threads, 5 minutes)"
echo "  3. Heavy test (20 threads, 5 minutes)"
echo "  4. Delay test (5 threads, 5 minutes, with 70s delays)"
echo "  5. Custom test (specify parameters)"
echo "  6. Shell-based quick test (30 seconds)"
echo ""

read -p "Select test type (1-6): " choice

case $choice in
    1)
        echo -e "${YELLOW}Running quick test...${NC}"
        java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
             -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
             org.example.NLBLoadTester --threads 5 --duration 30
        ;;
    2)
        echo -e "${YELLOW}Running standard test...${NC}"
        java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
             -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
             org.example.NLBLoadTester --threads 10 --duration 300
        ;;
    3)
        echo -e "${YELLOW}Running heavy test...${NC}"
        java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
             -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
             org.example.NLBLoadTester --threads 20 --duration 300
        ;;
    4)
        echo -e "${YELLOW}Running delay test...${NC}"
        echo "⚠️  This test uses 70-second delays per request"
        java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
             -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
             org.example.NLBLoadTester --threads 5 --duration 300 --delay
        ;;
    5)
        echo "Custom test configuration:"
        read -p "Number of threads: " threads
        read -p "Duration (seconds): " duration
        read -p "Use delay mode? (y/N): " use_delay
        
        delay_arg=""
        if [[ $use_delay =~ ^[Yy]$ ]]; then
            delay_arg="--delay"
        fi
        
        echo -e "${YELLOW}Running custom test...${NC}"
        java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
             -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
             org.example.NLBLoadTester --threads $threads --duration $duration $delay_arg
        ;;
    6)
        echo -e "${YELLOW}Running shell-based quick test...${NC}"
        ./test-nlb-quick.sh
        ;;
    *)
        echo "Invalid choice. Exiting."
        exit 1
        ;;
esac

echo ""
echo -e "${GREEN}✅ Test completed!${NC}"

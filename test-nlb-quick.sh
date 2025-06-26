#!/bin/bash

# Quick AWS NLB Load Test (30 seconds, 5 threads)
# For rapid testing of load balancer behavior

set -e

LOAD_BALANCER_HOST="FlightServer-NLB-cac72f6dd42cf041.elb.us-east-1.amazonaws.com"
TEST_DURATION=30  # 30 seconds
THREADS=5
RESULTS_DIR="nlb-quick-test-$(date +%Y%m%d-%H%M%S)"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}🚀 Quick AWS NLB Load Test${NC}"
echo "=========================="
echo ""
echo "Configuration:"
echo "  Load Balancer: $LOAD_BALANCER_HOST"
echo "  Duration: $TEST_DURATION seconds"
echo "  Threads: $THREADS"
echo ""

mkdir -p "$RESULTS_DIR"

# Function to run a client thread
run_quick_client() {
    local thread_id=$1
    local log_file="$RESULTS_DIR/thread-${thread_id}.log"
    
    echo "Thread $thread_id starting..." >> "$log_file"
    
    local start_time=$(date +%s)
    local end_time=$((start_time + TEST_DURATION))
    local count=0
    local success=0
    
    while [ $(date +%s) -lt $end_time ]; do
        if java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
           -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
           org.example.AWSFlightClient "$LOAD_BALANCER_HOST" \
           >> "$log_file" 2>&1; then
            success=$((success + 1))
        fi
        count=$((count + 1))
        sleep 0.5  # Brief pause between requests
    done
    
    echo "Thread $thread_id completed: $success/$count successful" >> "$log_file"
    echo "$thread_id,$count,$success" >> "$RESULTS_DIR/results.csv"
}

echo -e "${YELLOW}Starting $THREADS threads for $TEST_DURATION seconds...${NC}"

# Initialize results file
echo "thread_id,total_requests,successful_requests" > "$RESULTS_DIR/results.csv"

# Start threads
for ((i=1; i<=THREADS; i++)); do
    echo "Starting thread $i..."
    run_quick_client $i &
done

# Show countdown
for ((sec=TEST_DURATION; sec>0; sec--)); do
    echo -ne "\r⏰ Time remaining: ${sec}s "
    sleep 1
done
echo ""

echo -e "${YELLOW}Waiting for threads to complete...${NC}"
wait

# Generate quick summary
echo ""
echo -e "${GREEN}📊 Quick Results:${NC}"
echo "=================="

total_requests=0
total_success=0

while IFS=, read -r thread_id requests success; do
    if [ "$thread_id" != "thread_id" ]; then  # Skip header
        total_requests=$((total_requests + requests))
        total_success=$((total_success + success))
        echo "  Thread $thread_id: $success/$requests successful"
    fi
done < "$RESULTS_DIR/results.csv"

success_rate=0
if [ $total_requests -gt 0 ]; then
    success_rate=$(echo "scale=1; $total_success * 100 / $total_requests" | bc -l)
fi

echo ""
echo "  Total: $total_success/$total_requests successful (${success_rate}%)"
echo "  Rate: $(echo "scale=1; $total_requests / $TEST_DURATION" | bc -l) requests/sec"
echo ""
echo -e "${BLUE}📁 Detailed logs in: $RESULTS_DIR/${NC}"

if (( $(echo "$success_rate >= 90" | bc -l) )); then
    echo -e "${GREEN}✅ NLB performing well under concurrent load${NC}"
else
    echo -e "${YELLOW}⚠️  Some performance degradation detected${NC}"
fi

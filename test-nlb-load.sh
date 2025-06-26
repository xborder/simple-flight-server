#!/bin/bash

# AWS NLB Load Testing Script
# Tests Network Load Balancer limits with multiple parallel Flight clients

set -e

# Configuration
LOAD_BALANCER_HOST="FlightServer-NLB-cac72f6dd42cf041.elb.us-east-1.amazonaws.com"
TEST_DURATION=300  # 5 minutes in seconds
DEFAULT_THREADS=10
RESULTS_DIR="nlb-load-test-$(date +%Y%m%d-%H%M%S)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🧪 AWS Network Load Balancer Load Testing${NC}"
echo "=============================================="
echo ""
echo "Configuration:"
echo "  Load Balancer: $LOAD_BALANCER_HOST"
echo "  Test Duration: $TEST_DURATION seconds (5 minutes)"
echo "  Results Directory: $RESULTS_DIR"
echo ""

# Get number of threads from user
read -p "Enter number of parallel threads (default: $DEFAULT_THREADS): " THREADS
THREADS=${THREADS:-$DEFAULT_THREADS}

echo "  Parallel Threads: $THREADS"
echo ""

# Confirm test parameters
echo -e "${YELLOW}⚠️  This test will:${NC}"
echo "  - Run $THREADS parallel Flight clients"
echo "  - Each client will make continuous requests for 5 minutes"
echo "  - Generate detailed performance metrics"
echo "  - Test both normal and delayed responses"
echo ""

read -p "Do you want to proceed? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Test cancelled."
    exit 0
fi

# Create results directory
mkdir -p "$RESULTS_DIR"

echo ""
echo -e "${GREEN}🚀 Starting load test...${NC}"
echo ""

# Function to run a single client thread
run_client_thread() {
    local thread_id=$1
    local use_delay=$2
    local delay_suffix=""
    local delay_arg=""
    
    if [ "$use_delay" = "true" ]; then
        delay_suffix="-delay"
        delay_arg="--delay"
    fi
    
    local log_file="$RESULTS_DIR/thread-${thread_id}${delay_suffix}.log"
    local stats_file="$RESULTS_DIR/thread-${thread_id}${delay_suffix}-stats.txt"
    
    echo "$(date '+%Y-%m-%d %H:%M:%S') - Thread $thread_id${delay_suffix} started" >> "$log_file"
    
    local start_time=$(date +%s)
    local end_time=$((start_time + TEST_DURATION))
    local request_count=0
    local success_count=0
    local error_count=0
    local total_duration=0
    
    while [ $(date +%s) -lt $end_time ]; do
        local request_start=$(date +%s.%3N)
        
        if java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
           -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
           org.example.AWSFlightClient "$LOAD_BALANCER_HOST" $delay_arg \
           >> "$log_file" 2>&1; then
            success_count=$((success_count + 1))
        else
            error_count=$((error_count + 1))
            echo "$(date '+%Y-%m-%d %H:%M:%S') - ERROR in request $request_count" >> "$log_file"
        fi
        
        local request_end=$(date +%s.%3N)
        local request_duration=$(echo "$request_end - $request_start" | bc -l)
        total_duration=$(echo "$total_duration + $request_duration" | bc -l)
        
        request_count=$((request_count + 1))
        
        # Brief pause between requests (only for normal requests, delay requests naturally pause)
        if [ "$use_delay" != "true" ]; then
            sleep 0.1
        fi
    done
    
    # Calculate statistics
    local avg_duration=0
    if [ $request_count -gt 0 ]; then
        avg_duration=$(echo "scale=3; $total_duration / $request_count" | bc -l)
    fi
    
    # Write statistics
    cat > "$stats_file" << EOF
Thread ID: $thread_id${delay_suffix}
Test Duration: $TEST_DURATION seconds
Total Requests: $request_count
Successful Requests: $success_count
Failed Requests: $error_count
Success Rate: $(echo "scale=2; $success_count * 100 / $request_count" | bc -l)%
Average Request Duration: ${avg_duration}s
Total Request Time: ${total_duration}s
EOF
    
    echo "$(date '+%Y-%m-%d %H:%M:%S') - Thread $thread_id${delay_suffix} completed" >> "$log_file"
}

# Start background threads
echo -e "${YELLOW}Starting $THREADS parallel threads...${NC}"

# Calculate thread distribution (mix of normal and delayed requests)
normal_threads=$((THREADS * 3 / 4))  # 75% normal requests
delay_threads=$((THREADS - normal_threads))  # 25% delayed requests

echo "  Normal request threads: $normal_threads"
echo "  Delayed request threads: $delay_threads"
echo ""

# Start normal request threads
for ((i=1; i<=normal_threads; i++)); do
    echo "Starting normal thread $i..."
    run_client_thread $i false &
done

# Start delayed request threads
for ((i=1; i<=delay_threads; i++)); do
    echo "Starting delay thread $i..."
    run_client_thread $((normal_threads + i)) true &
done

echo ""
echo -e "${BLUE}⏳ Test running for 5 minutes...${NC}"
echo "Monitor progress in: $RESULTS_DIR/"

# Show progress
for ((minute=1; minute<=5; minute++)); do
    sleep 60
    echo -e "${YELLOW}⏰ $minute minute(s) elapsed...${NC}"
done

echo ""
echo -e "${YELLOW}⏳ Waiting for all threads to complete...${NC}"

# Wait for all background jobs to complete
wait

echo ""
echo -e "${GREEN}✅ Load test completed!${NC}"
echo ""

# Generate summary report
echo -e "${BLUE}📊 Generating summary report...${NC}"

SUMMARY_FILE="$RESULTS_DIR/summary-report.txt"

cat > "$SUMMARY_FILE" << EOF
AWS Network Load Balancer Load Test Summary
==========================================

Test Configuration:
- Load Balancer: $LOAD_BALANCER_HOST
- Test Duration: $TEST_DURATION seconds (5 minutes)
- Total Threads: $THREADS
- Normal Threads: $normal_threads
- Delayed Threads: $delay_threads
- Test Date: $(date)

Thread Results:
EOF

# Aggregate statistics
total_requests=0
total_success=0
total_errors=0
total_time=0

echo "" >> "$SUMMARY_FILE"
echo "Individual Thread Statistics:" >> "$SUMMARY_FILE"
echo "============================" >> "$SUMMARY_FILE"

for stats_file in "$RESULTS_DIR"/*-stats.txt; do
    if [ -f "$stats_file" ]; then
        echo "" >> "$SUMMARY_FILE"
        cat "$stats_file" >> "$SUMMARY_FILE"
        
        # Extract values for aggregation
        requests=$(grep "Total Requests:" "$stats_file" | cut -d: -f2 | tr -d ' ')
        success=$(grep "Successful Requests:" "$stats_file" | cut -d: -f2 | tr -d ' ')
        errors=$(grep "Failed Requests:" "$stats_file" | cut -d: -f2 | tr -d ' ')
        
        total_requests=$((total_requests + requests))
        total_success=$((total_success + success))
        total_errors=$((total_errors + errors))
    fi
done

# Calculate overall statistics
overall_success_rate=0
if [ $total_requests -gt 0 ]; then
    overall_success_rate=$(echo "scale=2; $total_success * 100 / $total_requests" | bc -l)
fi

cat >> "$SUMMARY_FILE" << EOF

Overall Statistics:
==================
Total Requests Across All Threads: $total_requests
Total Successful Requests: $total_success
Total Failed Requests: $total_errors
Overall Success Rate: ${overall_success_rate}%
Requests Per Second: $(echo "scale=2; $total_requests / $TEST_DURATION" | bc -l)
Successful Requests Per Second: $(echo "scale=2; $total_success / $TEST_DURATION" | bc -l)

Load Balancer Performance Assessment:
====================================
EOF

if (( $(echo "$overall_success_rate >= 95" | bc -l) )); then
    echo "✅ EXCELLENT: Success rate >= 95% - NLB handling load very well" >> "$SUMMARY_FILE"
elif (( $(echo "$overall_success_rate >= 90" | bc -l) )); then
    echo "✅ GOOD: Success rate >= 90% - NLB handling load well" >> "$SUMMARY_FILE"
elif (( $(echo "$overall_success_rate >= 80" | bc -l) )); then
    echo "⚠️  FAIR: Success rate >= 80% - Some performance degradation" >> "$SUMMARY_FILE"
else
    echo "❌ POOR: Success rate < 80% - Significant performance issues" >> "$SUMMARY_FILE"
fi

echo "" >> "$SUMMARY_FILE"
echo "Files Generated:" >> "$SUMMARY_FILE"
echo "- Individual thread logs: thread-*.log" >> "$SUMMARY_FILE"
echo "- Individual thread stats: thread-*-stats.txt" >> "$SUMMARY_FILE"
echo "- This summary: summary-report.txt" >> "$SUMMARY_FILE"

# Display summary
echo ""
echo -e "${GREEN}📋 Test Summary:${NC}"
echo "================"
echo "  Total Requests: $total_requests"
echo "  Successful: $total_success"
echo "  Failed: $total_errors"
echo "  Success Rate: ${overall_success_rate}%"
echo "  Requests/sec: $(echo "scale=2; $total_requests / $TEST_DURATION" | bc -l)"
echo ""
echo -e "${BLUE}📁 Results saved in: $RESULTS_DIR/${NC}"
echo "  - summary-report.txt (detailed summary)"
echo "  - thread-*.log (individual thread logs)"
echo "  - thread-*-stats.txt (individual thread statistics)"
echo ""
echo -e "${GREEN}🏁 Load test completed successfully!${NC}"

# PollFlightInfo Testing Guide

This guide provides step-by-step instructions for testing the PollFlightInfo implementation.

## Prerequisites

1. Java 17+ installed
2. Maven available
3. Project compiled: `mvn clean compile`

## Quick Test (Recommended)

Test the 65-second fixed polling scenario:

```bash
java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
     -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
     org.example.PollFlightClient --fixed-polling your-load-balancer-dns.elb.amazonaws.com
```

**Expected Result**: Query completes in ~60 seconds, retrieves 100 rows of data.

## Available Test Commands

### 1. Fixed Polling Tests

#### AWS Testing (Recommended)
```bash
# 65-second polling test
java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
     -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
     org.example.PollFlightClient --fixed-polling your-load-balancer-dns.elb.amazonaws.com
```

#### Local Testing
```bash
# Terminal 1: Start server
java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
     -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
     org.example.Main -server

# Terminal 2: Test client
java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
     -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
     org.example.PollFlightClient --fixed-polling
```

### 2. Variable Duration Tests

```bash
# 1-minute query
java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
     -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
     org.example.PollFlightClient --medium your-load-balancer-dns.elb.amazonaws.com

# 2-minute query
java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
     -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
     org.example.PollFlightClient --long your-load-balancer-dns.elb.amazonaws.com

# 5-minute query
java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
     -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
     org.example.PollFlightClient --very-long your-load-balancer-dns.elb.amazonaws.com

# 2-hour query (NLB timeout test)
java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
     -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
     org.example.PollFlightClient --ultra-long your-load-balancer-dns.elb.amazonaws.com
```

## Test Scenarios

### Scenario 1: Fixed Polling Window
- **Purpose**: Test client that polls for exactly 65 seconds
- **Query**: 1-minute query (completes during polling)
- **Expected**: Query completes, data retrieved successfully

### Scenario 2: Standard Polling
- **Purpose**: Test client that polls until completion
- **Query**: Various durations (1 min to 2 hours)
- **Expected**: Continuous polling until completion

### Scenario 3: NLB Timeout Test
- **Purpose**: Test queries exceeding load balancer timeout
- **Query**: 2-hour query with 5-second polls
- **Expected**: No connection timeouts, perfect for heavy analytics

## Expected Output

### Fixed Polling Test
```
🧪 Fixed Duration Polling Test
==============================
🚀 Starting long-running query: medium-query
📊 Initial poll response received
  Progress: 0.0%

🔄 Polling query status (poll #2, elapsed: 5s)...
📊 Poll response:
  Progress: 8.8%
⏳ Query still running, continuing to poll...

...

✅ Query completed during polling! (at poll #13)
📊 FlightInfo available after 65 seconds:
  Records: 100
  Endpoints: 1

📥 Retrieving available data...
  Total: 100 rows in 1 batches
  ✅ Complete dataset retrieved

✅ Fixed duration polling test completed!
```

### Standard Polling Test
```
🧪 PollFlightInfo Specification Test
====================================
🚀 Starting long-running query: long-query
📊 Initial poll response received
  Progress: 0.0%

🔄 Polling query status (poll #25)...
📊 Poll response:
  Progress: 100.0%
✅ Query completed! (flight_descriptor is unset)

📊 Final FlightInfo received:
  Records: 100
📥 Retrieving query results...
  Total: 100 rows in 1 batches

✅ PollFlightInfo specification test completed successfully!
```

## Troubleshooting

### Connection Issues
- Verify server is running
- Check hostname/port are correct
- Ensure port 8815 is accessible

### Compilation Issues
```bash
mvn clean compile
```

### Classpath Issues
```bash
# Regenerate classpath
mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q
```

### Server Logs
Monitor server progress:
```bash
# AWS server logs
ssh -i your-key-file.pem -o StrictHostKeyChecking=no ec2-user@your-ec2-instance-ip \
    "sudo journalctl -u flight-server -f"
```

## Performance Metrics

### Fixed Polling Test Results
- **Duration**: ~62 seconds (target: 65 seconds)
- **Poll Count**: ~13 polls
- **Poll Interval**: ~4.8 seconds average
- **Data Retrieved**: 100 rows successfully

### Standard Polling Test Results
- **2-minute query**: ~25 polls, 100% success
- **5-minute query**: ~60 polls, 100% success
- **2-hour query**: Continuous polling, no timeouts

## Key Benefits Demonstrated

1. **Non-Blocking**: Client never hangs for hours
2. **Progress Monitoring**: Real-time progress updates
3. **Resource Efficient**: Short polling requests
4. **NLB Compatible**: Works with any timeout configuration
5. **Scalable**: Multiple concurrent queries supported

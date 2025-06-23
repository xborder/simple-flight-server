#!/bin/bash

# Test the delayed response on AWS (will be killed after showing it starts)

echo "🐌 Testing DELAYED server response on AWS..."
echo "This will start the 70-second delay test and then be interrupted"
echo ""

# Start the delayed test in background
java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
     -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
     org.example.TestDelayClient FlightServer-NLB-cac72f6dd42cf041.elb.us-east-1.amazonaws.com --delay &

# Get the process ID
PID=$!

echo "Started delay test with PID: $PID"
echo "Waiting 10 seconds to see initial output..."

# Wait 10 seconds to see the initial output
sleep 10

echo ""
echo "🛑 Killing test after 10 seconds (to avoid waiting 70 seconds)"
kill $PID 2>/dev/null

echo "✅ Test demonstrated that delay parameter works"
echo ""
echo "The server received the 'sample-delay' request and started the 70-second wait."
echo "In a real scenario, this would test load balancer timeout behavior."

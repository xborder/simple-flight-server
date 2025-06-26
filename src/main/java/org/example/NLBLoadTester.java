package org.example;

import org.apache.arrow.flight.*;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VectorSchemaRoot;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-threaded load tester for AWS Network Load Balancer
 */
public class NLBLoadTester {
    
    private static final String DEFAULT_HOST = "your-load-balancer-dns.elb.amazonaws.com";
    private static final int PORT = 8815;
    private static final int DEFAULT_THREADS = 10;
    private static final int DEFAULT_DURATION = 300; // 5 minutes
    
    private final String serverHost;
    private final int numThreads;
    private final int durationSeconds;
    private final boolean useDelay;
    private final int staggerSeconds;
    
    // Shared counters
    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger successfulRequests = new AtomicInteger(0);
    private final AtomicInteger failedRequests = new AtomicInteger(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    
    public NLBLoadTester(String serverHost, int numThreads, int durationSeconds, boolean useDelay, int staggerSeconds) {
        this.serverHost = serverHost;
        this.numThreads = numThreads;
        this.durationSeconds = durationSeconds;
        this.useDelay = useDelay;
        this.staggerSeconds = staggerSeconds;
    }
    
    public void runLoadTest() throws InterruptedException {
        System.out.println("🧪 AWS Network Load Balancer Load Test");
        System.out.println("======================================");
        System.out.println("Host: " + serverHost);
        System.out.println("Threads: " + numThreads);
        System.out.println("Duration: " + durationSeconds + " seconds");
        System.out.println("Mode: " + (useDelay ? "🐌 DELAYED (70s)" : "⚡ NORMAL"));
        if (staggerSeconds > 0) {
            System.out.println("Stagger: " + staggerSeconds + " seconds (threads start gradually)");
        }
        System.out.println();
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch endLatch = new CountDownLatch(numThreads);

        long testStartTime = System.currentTimeMillis();
        long testEndTime = testStartTime + (durationSeconds * 1000L);

        if (staggerSeconds > 0) {
            // Staggered thread startup
            System.out.println("🚀 Starting " + numThreads + " worker threads over " + staggerSeconds + " seconds...");
            double staggerInterval = (double) staggerSeconds * 1000 / numThreads; // milliseconds between thread starts

            for (int i = 0; i < numThreads; i++) {
                final int threadId = i + 1;
                final long threadStartDelay = (long) (i * staggerInterval);

                executor.submit(() -> {
                    try {
                        // Wait for this thread's start time
                        Thread.sleep(threadStartDelay);
                        System.out.println("🔄 Starting thread " + threadId + " (+" + (threadStartDelay/1000.0) + "s)");
                        runWorkerThread(threadId, testEndTime);
                    } catch (Exception e) {
                        System.err.println("Thread " + threadId + " error: " + e.getMessage());
                    } finally {
                        endLatch.countDown();
                    }
                });
            }
        } else {
            // Simultaneous thread startup
            CountDownLatch startLatch = new CountDownLatch(1);

            for (int i = 0; i < numThreads; i++) {
                final int threadId = i + 1;
                executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for all threads to be ready
                        runWorkerThread(threadId, testEndTime);
                    } catch (Exception e) {
                        System.err.println("Thread " + threadId + " error: " + e.getMessage());
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            System.out.println("🚀 Starting " + numThreads + " worker threads simultaneously...");
            startLatch.countDown(); // Start all threads simultaneously
        }
        
        // Progress monitoring
        for (int i = 1; i <= durationSeconds; i++) {
            Thread.sleep(1000);
            if (i % 30 == 0 || i <= 10) { // Show progress every 30 seconds, or first 10 seconds
                int current = totalRequests.get();
                int success = successfulRequests.get();
                double rate = (double) current / i;
                System.out.printf("⏰ %ds elapsed - Requests: %d, Success: %d, Rate: %.1f req/s%n", 
                    i, current, success, rate);
            }
        }
        
        System.out.println("⏳ Waiting for threads to complete...");
        endLatch.await();
        executor.shutdown();
        
        // Generate report
        generateReport();
    }
    
    private void runWorkerThread(int threadId, long endTime) {
        try (BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE)) {
            Location location = Location.forGrpcInsecure(serverHost, PORT);

            // Keep making requests until test ends
            while (System.currentTimeMillis() < endTime) {
                long requestStart = System.currentTimeMillis();

                // Check if we still have time for another request
                if (requestStart >= endTime) {
                    break;
                }

                boolean success = false;

                try (FlightClient client = FlightClient.builder(allocator, location).build()) {
                    // Choose flight based on delay setting
                    String flightName = useDelay ? "sample-delay" : "sample";
                    FlightDescriptor descriptor = FlightDescriptor.path(flightName);
                    FlightInfo info = client.getInfo(descriptor);

                    // Get data stream
                    Ticket ticket = info.getEndpoints().get(0).getTicket();
                    try (FlightStream stream = client.getStream(ticket)) {
                        while (stream.next()) {
                            // Process data (just count rows)
                            VectorSchemaRoot root = stream.getRoot();
                            // Data processed successfully
                        }
                    }

                    success = true;
                    successfulRequests.incrementAndGet();

                } catch (Exception e) {
                    failedRequests.incrementAndGet();
                    if (threadId <= 3) { // Only log errors from first 3 threads to avoid spam
                        System.err.println("Thread " + threadId + " request error: " + e.getMessage());
                    }
                }

                long requestEnd = System.currentTimeMillis();
                long responseTime = requestEnd - requestStart;
                totalResponseTime.addAndGet(responseTime);
                totalRequests.incrementAndGet();

                // Brief pause between requests (except for delay mode which naturally pauses)
                if (!useDelay && success) {
                    try {
                        Thread.sleep(100); // 100ms pause for normal requests
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                // For delay mode, check if we have time for another 70+ second request
                if (useDelay && (System.currentTimeMillis() + 75000) > endTime) {
                    // Not enough time for another delay request, exit gracefully
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("Thread " + threadId + " fatal error: " + e.getMessage());
        }
    }
    
    private void generateReport() {
        int total = totalRequests.get();
        int success = successfulRequests.get();
        int failed = failedRequests.get();
        long totalTime = totalResponseTime.get();
        
        double successRate = total > 0 ? (double) success * 100 / total : 0;
        double avgResponseTime = total > 0 ? (double) totalTime / total : 0;
        double requestsPerSecond = (double) total / durationSeconds;
        double successPerSecond = (double) success / durationSeconds;
        
        System.out.println();
        System.out.println("📊 Load Test Results");
        System.out.println("===================");
        System.out.println("Test Duration: " + durationSeconds + " seconds");
        System.out.println("Concurrent Threads: " + numThreads);
        System.out.println("Request Mode: " + (useDelay ? "DELAYED (70s)" : "NORMAL"));
        System.out.println();
        System.out.println("Request Statistics:");
        System.out.println("  Total Requests: " + total);
        System.out.println("  Successful: " + success);
        System.out.println("  Failed: " + failed);
        System.out.println("  Success Rate: " + String.format("%.1f%%", successRate));
        System.out.println();
        System.out.println("Performance Metrics:");
        System.out.println("  Requests/second: " + String.format("%.2f", requestsPerSecond));
        System.out.println("  Successful requests/second: " + String.format("%.2f", successPerSecond));
        System.out.println("  Average response time: " + String.format("%.0f ms", avgResponseTime));
        System.out.println();
        
        // Performance assessment
        System.out.println("Load Balancer Assessment:");
        if (successRate >= 95) {
            System.out.println("  ✅ EXCELLENT: Success rate >= 95% - NLB handling load very well");
        } else if (successRate >= 90) {
            System.out.println("  ✅ GOOD: Success rate >= 90% - NLB handling load well");
        } else if (successRate >= 80) {
            System.out.println("  ⚠️  FAIR: Success rate >= 80% - Some performance degradation");
        } else {
            System.out.println("  ❌ POOR: Success rate < 80% - Significant performance issues");
        }
        
        if (!useDelay) {
            if (avgResponseTime < 500) {
                System.out.println("  ✅ FAST: Average response time < 500ms");
            } else if (avgResponseTime < 1000) {
                System.out.println("  ⚠️  SLOW: Average response time < 1s");
            } else {
                System.out.println("  ❌ VERY SLOW: Average response time >= 1s");
            }
        }
        
        System.out.println();
        System.out.println("🏁 Load test completed!");
    }
    
    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int threads = DEFAULT_THREADS;
        int duration = DEFAULT_DURATION;
        boolean useDelay = false;
        int staggerSeconds = 0;
        
        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host":
                    if (i + 1 < args.length) host = args[++i];
                    break;
                case "--threads":
                    if (i + 1 < args.length) threads = Integer.parseInt(args[++i]);
                    break;
                case "--duration":
                    if (i + 1 < args.length) duration = Integer.parseInt(args[++i]);
                    break;
                case "--delay":
                    useDelay = true;
                    break;
                case "--stagger":
                    if (i + 1 < args.length) staggerSeconds = Integer.parseInt(args[++i]);
                    break;
                case "--help":
                    System.out.println("Usage: java NLBLoadTester [options]");
                    System.out.println("Options:");
                    System.out.println("  --host <hostname>     Server hostname (default: " + DEFAULT_HOST + ")");
                    System.out.println("  --threads <number>    Number of threads (default: " + DEFAULT_THREADS + ")");
                    System.out.println("  --duration <seconds>  Test duration (default: " + DEFAULT_DURATION + ")");
                    System.out.println("  --delay               Use delayed requests (70s each)");
                    System.out.println("  --stagger <seconds>   Stagger thread startup over N seconds (default: 0)");
                    System.out.println("  --help                Show this help");
                    System.out.println();
                    System.out.println("Examples:");
                    System.out.println("  java NLBLoadTester --threads 15 --stagger 60");
                    System.out.println("  java NLBLoadTester --threads 10 --duration 300 --delay");
                    return;
            }
        }
        
        try {
            NLBLoadTester tester = new NLBLoadTester(host, threads, duration, useDelay, staggerSeconds);
            tester.runLoadTest();
        } catch (Exception e) {
            System.err.println("Load test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

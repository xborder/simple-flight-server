package org.example;

import org.apache.arrow.flight.*;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Schema;

import java.util.Iterator;

/**
 * Client that demonstrates PollFlightInfo pattern using actions for long-running queries
 */
public class PollFlightClient {
  
  public static void main(String[] args) throws Exception {
    String serverHost = "localhost";
    int port = 8815;
    String queryType = "long-query"; // Default to 2-minute query
    boolean fixedPollingTest = false;

    // Parse command line arguments
    if (args.length > 0) {
      if (args[0].startsWith("--")) {
        // Query type specified
        switch (args[0]) {
          case "--long":
            queryType = "long-query"; // 2 minutes
            break;
          case "--very-long":
            queryType = "very-long-query"; // 5 minutes
            break;
          case "--ultra-long":
            queryType = "ultra-long-query"; // 2 hours
            break;
          case "--fixed-polling":
            fixedPollingTest = true;
            queryType = "medium-query"; // Use 1-minute query for 65-second test (completes during polling)
            break;
          default:
            System.err.println("Unknown query type: " + args[0]);
            System.err.println("Valid options: --long, --very-long, --ultra-long, --fixed-polling");
            System.exit(1);
        }

        // Check for hostname
        if (args.length > 1) {
          serverHost = args[1];
        }
      } else {
        // Hostname specified
        serverHost = args[0];
        if (args.length > 1) {
          switch (args[1]) {
            case "--long":
              queryType = "long-query";
              break;
            case "--very-long":
              queryType = "very-long-query";
              break;
            case "--ultra-long":
              queryType = "ultra-long-query";
              break;
            case "--fixed-polling":
              fixedPollingTest = true;
              queryType = "medium-query";
              break;
          }
        }
      }
    }

    if (fixedPollingTest) {
      runFixedPollingTest(serverHost, port, queryType);
    } else {
      runStandardPollingTest(serverHost, port, queryType);
    }
  }

  private static void runFixedPollingTest(String serverHost, int port, String queryType) throws Exception {
    String queryDescription = "1 minute (polling for 65 seconds - query completes during polling)";

    System.out.println("🧪 Fixed Duration Polling Test");
    System.out.println("==============================");
    System.out.println("Server: " + serverHost + ":" + port);
    System.out.println("Query: " + queryType + " (" + queryDescription + ")");
    System.out.println("💡 Will poll for exactly 65 seconds, then retrieve available data");
    System.out.println();

    try (BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE)) {
      Location location = Location.forGrpcInsecure(serverHost, port);

      try (FlightClient client = FlightClient.builder(allocator, location).build()) {
        System.out.println("✅ Connected to Flight server at " + location);

        // Start the long-running query using PollFlightInfo
        System.out.println("\n🚀 Starting long-running query: " + queryType);
        System.out.println("💡 Will poll for exactly 65 seconds regardless of completion status...");

        long startTime = System.currentTimeMillis();
        long pollingEndTime = startTime + 65000; // Poll for exactly 65 seconds

        // Step 1: Start the query with initial PollFlightInfo call
        FlightDescriptor queryDescriptor = FlightDescriptor.path(queryType);
        PollInfo pollInfo = client.pollInfo(queryDescriptor);

        System.out.println("📊 Initial poll response received");
        System.out.println("  Progress: " + (pollInfo.getProgress().isPresent() ?
            String.format("%.1f%%", pollInfo.getProgress().get() * 100) : "unknown"));
        System.out.println("  Has FlightInfo: " + (pollInfo.getFlightInfo() != null));
        System.out.println("  Has poll descriptor: " + pollInfo.getFlightDescriptor().isPresent());

        // Step 2: Poll for exactly 65 seconds
        FlightDescriptor pollDescriptor = pollInfo.getFlightDescriptor().orElse(null);
        int pollCount = 1;
        boolean queryCompleted = false;

        while (System.currentTimeMillis() < pollingEndTime) {
          if (pollDescriptor == null) {
            queryCompleted = true;
            System.out.println("✅ Query completed before 65 seconds! (at poll #" + pollCount + ")");
            break;
          }

          System.out.println("\n⏰ Waiting 5 seconds before next poll...");
          Thread.sleep(5000);

          // Check if we still have time for another poll
          if (System.currentTimeMillis() + 1000 > pollingEndTime) {
            System.out.println("⏱️ Approaching 65-second limit, making final poll...");
          }

          pollCount++;
          long currentTime = System.currentTimeMillis();
          long elapsedSeconds = (currentTime - startTime) / 1000;

          System.out.println("🔄 Polling query status (poll #" + pollCount + ", elapsed: " + elapsedSeconds + "s)...");

          pollInfo = client.pollInfo(pollDescriptor);

          System.out.println("📊 Poll response:");
          System.out.println("  Progress: " + (pollInfo.getProgress().isPresent() ?
              String.format("%.1f%%", pollInfo.getProgress().get() * 100) : "unknown"));
          System.out.println("  Has FlightInfo: " + (pollInfo.getFlightInfo() != null));

          pollDescriptor = pollInfo.getFlightDescriptor().orElse(null);

          if (pollDescriptor == null) {
            queryCompleted = true;
            System.out.println("✅ Query completed during polling! (at poll #" + pollCount + ")");
            break;
          } else {
            System.out.println("⏳ Query still running, continuing to poll...");
          }
        }

        long actualPollingDuration = System.currentTimeMillis() - startTime;

        System.out.println("\n📈 Fixed Polling Statistics:");
        System.out.println("  Polling Duration: " + (actualPollingDuration / 1000.0) + " seconds");
        System.out.println("  Target Duration: 65 seconds");
        System.out.println("  Poll Count: " + pollCount);
        System.out.println("  Average Poll Interval: " + (actualPollingDuration / pollCount / 1000.0) + " seconds");
        System.out.println("  Query Completed: " + (queryCompleted ? "Yes" : "No"));

        // Step 3: Get available data (partial or complete)
        FlightInfo flightInfo = pollInfo.getFlightInfo();
        if (flightInfo != null) {
          System.out.println("\n📊 FlightInfo available after 65 seconds:");
          System.out.println("  Schema: " + flightInfo.getSchema());
          System.out.println("  Records: " + flightInfo.getRecords());
          System.out.println("  Endpoints: " + flightInfo.getEndpoints().size());

          if (!flightInfo.getEndpoints().isEmpty()) {
            System.out.println("\n📥 Retrieving available data...");
            Ticket ticket = flightInfo.getEndpoints().get(0).getTicket();

            try (FlightStream stream = client.getStream(ticket)) {
              Schema schema = stream.getSchema();
              System.out.println("  Result schema: " + schema);

              int batchCount = 0;
              int totalRows = 0;

              while (stream.next()) {
                VectorSchemaRoot root = stream.getRoot();
                batchCount++;
                totalRows += root.getRowCount();

                System.out.println("  Batch " + batchCount + ": " + root.getRowCount() + " rows");

                // Show first few rows
                if (batchCount == 1) {
                  IntVector valueVector = (IntVector) root.getVector("value");
                  int rowsToShow = Math.min(5, root.getRowCount());
                  System.out.println("    First " + rowsToShow + " rows:");
                  for (int i = 0; i < rowsToShow; i++) {
                    System.out.println("      Row " + i + ": " + valueVector.get(i));
                  }
                  if (root.getRowCount() > 5) {
                    System.out.println("      ... (" + (root.getRowCount() - 5) + " more rows)");
                  }
                }
              }

              System.out.println("  Total: " + totalRows + " rows in " + batchCount + " batches");

              if (queryCompleted) {
                System.out.println("  ✅ Complete dataset retrieved");
              } else {
                System.out.println("  ⚠️ Partial dataset retrieved (query may still be running)");
              }
            }
          } else {
            System.out.println("  ⚠️ No endpoints available yet (query still in progress)");
          }
        } else {
          System.out.println("⚠️ No FlightInfo available after 65 seconds of polling");
        }

        System.out.println("\n✅ Fixed duration polling test completed!");

        if (!queryCompleted) {
          System.out.println("💡 Note: Query may still be running on the server.");
          System.out.println("    In a real application, you could continue polling or cancel the query.");
        }

      } catch (Exception e) {
        System.err.println("❌ Error during fixed polling test:");
        System.err.println("  " + e.getMessage());
        throw e;
      }
    }
  }

  private static void runStandardPollingTest(String serverHost, int port, String queryType) throws Exception {
    
    String queryDescription = getQueryDescription(queryType);
    System.out.println("🧪 PollFlightInfo Specification Test");
    System.out.println("====================================");
    System.out.println("Server: " + serverHost + ":" + port);
    System.out.println("Query: " + queryType + " (" + queryDescription + ")");
    System.out.println("💡 Using proper PollFlightInfo RPC according to Flight specification");
    System.out.println();

    try (BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE)) {
      Location location = Location.forGrpcInsecure(serverHost, port);

      try (FlightClient client = FlightClient.builder(allocator, location).build()) {
        System.out.println("✅ Connected to Flight server at " + location);

        // Start the long-running query using PollFlightInfo
        System.out.println("\n🚀 Starting long-running query: " + queryType);
        System.out.println("💡 Using PollFlightInfo RPC to avoid blocking...");

        long startTime = System.currentTimeMillis();

        // Step 1: Start the query with initial PollFlightInfo call
        FlightDescriptor queryDescriptor = FlightDescriptor.path(queryType);
        PollInfo pollInfo = client.pollInfo(queryDescriptor);

        System.out.println("📊 Initial poll response received");
        System.out.println("  Progress: " + (pollInfo.getProgress().isPresent() ?
            String.format("%.1f%%", pollInfo.getProgress().get() * 100) : "unknown"));
        System.out.println("  Expiration: " + pollInfo.getExpirationTime().orElse(null));
        System.out.println("  Has FlightInfo: " + (pollInfo.getFlightInfo() != null));
        System.out.println("  Has poll descriptor: " + pollInfo.getFlightDescriptor().isPresent());

        // Step 2: Poll until query completes
        FlightDescriptor pollDescriptor = pollInfo.getFlightDescriptor().orElse(null);
        int pollCount = 1;

        while (pollDescriptor != null) {
          System.out.println("\n⏰ Waiting 10 seconds before next poll...");
          Thread.sleep(10000);

          pollCount++;
          System.out.println("🔄 Polling query status (poll #" + pollCount + ")...");

          pollInfo = client.pollInfo(pollDescriptor);

          System.out.println("📊 Poll response:");
          System.out.println("  Progress: " + (pollInfo.getProgress().isPresent() ?
              String.format("%.1f%%", pollInfo.getProgress().get() * 100) : "unknown"));
          System.out.println("  Expiration: " + pollInfo.getExpirationTime().orElse(null));
          System.out.println("  Has FlightInfo: " + (pollInfo.getFlightInfo() != null));

          pollDescriptor = pollInfo.getFlightDescriptor().orElse(null);

          if (pollDescriptor == null) {
            System.out.println("✅ Query completed! (flight_descriptor is unset)");
            break;
          } else {
            System.out.println("⏳ Query still running, will continue polling...");
          }
        }

        long endTime = System.currentTimeMillis();
        long totalDuration = endTime - startTime;

        System.out.println("\n📈 Polling Statistics:");
        System.out.println("  Total Duration: " + (totalDuration / 1000.0) + " seconds");
        System.out.println("  Duration (minutes): " + (totalDuration / 60000.0) + " minutes");
        System.out.println("  Poll Count: " + pollCount);
        System.out.println("  Average Poll Interval: " + (totalDuration / pollCount / 1000.0) + " seconds");

        if (totalDuration > 6000000) { // More than 100 minutes (NLB timeout)
          System.out.println("🎉 SUCCESS: Query survived longer than NLB timeout!");
        } else if (totalDuration > 60000) { // More than 1 minute
          System.out.println("✅ GOOD: Query completed successfully with significant duration");
        } else {
          System.out.println("⚠️  Query completed quickly - may not test timeout limits");
        }

        // Step 3: Get the final result from FlightInfo
        FlightInfo flightInfo = pollInfo.getFlightInfo();
        if (flightInfo != null) {
          System.out.println("\n📊 Final FlightInfo received:");
          System.out.println("  Schema: " + flightInfo.getSchema());
          System.out.println("  Records: " + flightInfo.getRecords());
          System.out.println("  Endpoints: " + flightInfo.getEndpoints().size());

          if (!flightInfo.getEndpoints().isEmpty()) {
            System.out.println("\n📥 Retrieving query results...");
            Ticket ticket = flightInfo.getEndpoints().get(0).getTicket();

            try (FlightStream stream = client.getStream(ticket)) {
              Schema schema = stream.getSchema();
              System.out.println("  Result schema: " + schema);

              int batchCount = 0;
              int totalRows = 0;

              while (stream.next()) {
                VectorSchemaRoot root = stream.getRoot();
                batchCount++;
                totalRows += root.getRowCount();

                System.out.println("  Batch " + batchCount + ": " + root.getRowCount() + " rows");

                // Show first few rows
                if (batchCount == 1) {
                  IntVector valueVector = (IntVector) root.getVector("value");
                  int rowsToShow = Math.min(5, root.getRowCount());
                  System.out.println("    First " + rowsToShow + " rows:");
                  for (int i = 0; i < rowsToShow; i++) {
                    System.out.println("      Row " + i + ": " + valueVector.get(i));
                  }
                  if (root.getRowCount() > 5) {
                    System.out.println("      ... (" + (root.getRowCount() - 5) + " more rows)");
                  }
                }
              }

              System.out.println("  Total: " + totalRows + " rows in " + batchCount + " batches");
            }
          }
        } else {
          System.out.println("⚠️  No FlightInfo received in final poll response");
        }

        System.out.println("\n✅ PollFlightInfo specification test completed successfully!");
        
      } catch (Exception e) {
        System.err.println("❌ Error during PollFlightInfo specification test:");
        System.err.println("  " + e.getMessage());
        throw e;
      }
    }
  }
  
  private static String getQueryDescription(String queryType) {
    switch (queryType) {
      case "long-query":
        return "2 minutes";
      case "very-long-query":
        return "5 minutes";
      case "ultra-long-query":
        return "2 hours";
      case "medium-query":
        return "1 minute";
      default:
        return "unknown duration";
    }
  }
}

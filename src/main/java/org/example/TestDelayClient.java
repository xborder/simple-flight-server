package org.example;

import org.apache.arrow.flight.*;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Schema;

/**
 * Test client to demonstrate normal vs delayed server responses
 */
public class TestDelayClient {
  
  public static void main(String[] args) throws Exception {
    String serverHost = "localhost";
    int port = 8815;
    boolean testDelay = false;
    
    // Parse command line arguments
    if (args.length > 0) {
      if ("--delay".equals(args[0])) {
        testDelay = true;
        System.out.println("🐌 Testing DELAYED server response (70 seconds)");
      } else if ("--normal".equals(args[0])) {
        testDelay = false;
        System.out.println("⚡ Testing NORMAL server response (immediate)");
      } else {
        serverHost = args[0];
        if (args.length > 1 && "--delay".equals(args[1])) {
          testDelay = true;
          System.out.println("🐌 Testing DELAYED server response (70 seconds) on " + serverHost);
        } else {
          System.out.println("⚡ Testing NORMAL server response (immediate) on " + serverHost);
        }
      }
    } else {
      System.out.println("⚡ Testing NORMAL server response (immediate) on localhost");
      System.out.println();
      System.out.println("Usage:");
      System.out.println("  java TestDelayClient                    # Normal response on localhost");
      System.out.println("  java TestDelayClient --normal           # Normal response on localhost");
      System.out.println("  java TestDelayClient --delay            # Delayed response on localhost");
      System.out.println("  java TestDelayClient <hostname>         # Normal response on hostname");
      System.out.println("  java TestDelayClient <hostname> --delay # Delayed response on hostname");
      System.out.println();
    }
    
    try (BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE)) {
      Location location = Location.forGrpcInsecure(serverHost, port);

      try (FlightClient client = FlightClient.builder(allocator, location).build()) {
        System.out.println("✅ Connected to Flight server at " + location);

        // List available flights
        System.out.println("\n📋 Listing available flights:");
        client.listFlights(new Criteria(new byte[0])).forEach(info -> {
          System.out.println("  📄 Flight: " + info.getDescriptor().getPath().get(0));
          System.out.println("     Schema: " + info.getSchema());
          System.out.println("     Records: " + info.getRecords());
        });

        // Choose which flight to request
        String flightName = testDelay ? "sample-delay" : "sample";
        System.out.println("\n🎯 Requesting flight: " + flightName);
        
        // Get flight info
        FlightDescriptor descriptor = FlightDescriptor.path(flightName);
        FlightInfo info = client.getInfo(descriptor);
        System.out.println("📊 Flight info retrieved for: " + info.getDescriptor().getPath().get(0));

        // Get data from the server
        System.out.println("\n📥 Getting data stream...");
        if (testDelay) {
          System.out.println("⏰ Server will wait 70 seconds before responding...");
          System.out.println("💡 This tests the load balancer idle timeout behavior");
        }
        
        long startTime = System.currentTimeMillis();
        
        Ticket ticket = info.getEndpoints().get(0).getTicket();
        try (FlightStream stream = client.getStream(ticket)) {
          Schema schema = stream.getSchema();
          System.out.println("📋 Stream schema: " + schema);

          while (stream.next()) {
            VectorSchemaRoot root = stream.getRoot();
            IntVector valueVector = (IntVector) root.getVector("value");

            System.out.println("📦 Received batch with " + root.getRowCount() + " rows:");
            for (int i = 0; i < root.getRowCount(); i++) {
              if (valueVector.isNull(i)) {
                System.out.println("    Row " + i + ": null");
              } else {
                System.out.println("    Row " + i + ": " + valueVector.get(i));
              }
            }
          }
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.println("\n⏱️  Total request duration: " + duration + " ms (" + (duration / 1000.0) + " seconds)");
        
        if (testDelay && duration < 60000) {
          System.out.println("⚠️  Warning: Expected delay of 70 seconds, but completed in " + (duration / 1000.0) + " seconds");
        } else if (testDelay && duration >= 60000) {
          System.out.println("✅ Delay test completed successfully - server waited as expected");
        }

        System.out.println("\n✅ Test completed successfully!");
        
      } catch (Exception e) {
        System.err.println("❌ Error during Flight operations:");
        System.err.println("  " + e.getMessage());
        
        if (testDelay) {
          System.err.println("\n💡 This might be expected behavior if testing load balancer timeouts");
        }
        throw e;
      }
    }
  }
}

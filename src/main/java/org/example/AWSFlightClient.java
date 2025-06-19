package org.example;

import org.apache.arrow.flight.*;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Schema;

/**
 * Flight client specifically configured for AWS deployment
 * This client connects to the Flight server deployed on AWS behind a Network Load Balancer
 */
public class AWSFlightClient {
  
  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.err.println("Usage: java AWSFlightClient <load-balancer-dns-name>");
      System.err.println("Example: java AWSFlightClient flight-server-nlb-1234567890.elb.us-east-1.amazonaws.com");
      System.exit(1);
    }
    
    String serverHost = args[0];
    int port = 8815;
    
    System.out.println("Connecting to AWS Flight Server...");
    System.out.println("Host: " + serverHost);
    System.out.println("Port: " + port);
    System.out.println();
    
    try (BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE)) {
      Location location = Location.forGrpcInsecure(serverHost, port);

      try (FlightClient client = FlightClient.builder(allocator, location).build()) {
        System.out.println("✓ Connected to AWS Flight server at " + location);

        // List available flights
        System.out.println("\n📋 Listing flights:");
        client.listFlights(new Criteria(new byte[0])).forEach(info -> {
          System.out.println("  Found flight: " + info.getDescriptor());
          System.out.println("  Schema: " + info.getSchema());
        });

        // Get flight info
        FlightDescriptor descriptor = FlightDescriptor.path("sample");
        FlightInfo info = client.getInfo(descriptor);
        System.out.println("\n📊 Got flight info for: " + info.getDescriptor());
        System.out.println("  Endpoints: " + info.getEndpoints().size());
        System.out.println("  Records: " + info.getRecords());

        // Get data from the server
        System.out.println("\n📥 Getting data stream:");
        Ticket ticket = info.getEndpoints().get(0).getTicket();
        try (FlightStream stream = client.getStream(ticket)) {
          Schema schema = stream.getSchema();
          System.out.println("  Stream schema: " + schema);

          while (stream.next()) {
            VectorSchemaRoot root = stream.getRoot();
            IntVector valueVector = (IntVector) root.getVector("value");

            System.out.println("  Received batch with " + root.getRowCount() + " rows:");
            for (int i = 0; i < root.getRowCount(); i++) {
              if (valueVector.isNull(i)) {
                System.out.println("    Row " + i + ": null");
              } else {
                System.out.println("    Row " + i + ": " + valueVector.get(i));
              }
            }
          }
        }

        // Do an action
        System.out.println("\n🔄 Performing action:");
        byte[] actionBody = "Hello from AWS client!".getBytes();
        Result result = client.doAction(new Action("echo", actionBody)).next();
        System.out.println("  Action result: " + new String(result.getBody()));
        
        System.out.println("\n✅ AWS Flight client test completed successfully!");
        
      } catch (Exception e) {
        System.err.println("❌ Error connecting to Flight server:");
        System.err.println("  " + e.getMessage());
        System.err.println();
        System.err.println("Troubleshooting tips:");
        System.err.println("1. Verify the server hostname is correct");
        System.err.println("2. Check that the server is running and healthy");
        System.err.println("3. Ensure port 8815 is accessible");
        System.err.println("4. Wait a few minutes for load balancer health checks");
        throw e;
      }
    }
  }
}

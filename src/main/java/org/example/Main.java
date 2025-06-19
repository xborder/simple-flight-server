package org.example;

import org.apache.arrow.flight.*;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.ArrowType;

import java.util.Arrays;
import java.util.Collections;

public class Main {
  public static void main(String[] args) throws Exception {
    boolean isServer = args.length > 0 && "-server".equals(args[0]);

    if (isServer) {
      runServer();
    } else {
      runClient();
    }
  }

  private static void runServer() throws Exception {
    int port = 8815;

    try (BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE)) {
      Location location = Location.forGrpcInsecure("0.0.0.0", port);

      try (FlightServer server = FlightServer.builder(allocator, location, new SampleFlightProducer(allocator)).build()) {
        server.start();
        System.out.println("Flight server started on port " + server.getPort());
        System.out.println("Press Ctrl+C to stop the server");
        server.awaitTermination();
      }
    }
  }

  private static void runClient() throws Exception {
    int port = 8815;

    try (BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE)) {
      Location location = Location.forGrpcInsecure("localhost", port);

      try (FlightClient client = FlightClient.builder(allocator, location).build()) {
        System.out.println("Connected to server at " + location);

        // List available flights
        System.out.println("Listing flights:");
        client.listFlights(new Criteria(new byte[0])).forEach(info -> {
          System.out.println("Found flight: " + info.getDescriptor());
          System.out.println("Schema: " + info.getSchema());
        });

        // Get flight info
        FlightDescriptor descriptor = FlightDescriptor.path("sample");
        FlightInfo info = client.getInfo(descriptor);
        System.out.println("Got flight info for: " + info.getDescriptor());

        // Get data from the server
        System.out.println("Getting data stream:");
        Ticket ticket = info.getEndpoints().get(0).getTicket();
        try (FlightStream stream = client.getStream(ticket)) {
          Schema schema = stream.getSchema();
          System.out.println("Stream schema: " + schema);

          while (stream.next()) {
            VectorSchemaRoot root = stream.getRoot();
            IntVector valueVector = (IntVector) root.getVector("value");

            System.out.println("Received batch with " + root.getRowCount() + " rows:");
            for (int i = 0; i < root.getRowCount(); i++) {
              if (valueVector.isNull(i)) {
                System.out.println("  Row " + i + ": null");
              } else {
                System.out.println("  Row " + i + ": " + valueVector.get(i));
              }
            }
          }
        }

        // Do an action
        System.out.println("Performing action:");
        byte[] actionBody = "Hello, Flight!".getBytes();
        Result result = client.doAction(new Action("echo", actionBody)).next();
        System.out.println("Action result: " + new String(result.getBody()));
      }
    }
  }

  /**
   * Sample Flight Producer implementation that provides sample data
   */
  static class SampleFlightProducer extends NoOpFlightProducer {
    private final BufferAllocator allocator;
    private final Schema schema;

    public SampleFlightProducer(BufferAllocator allocator) {
      this.allocator = allocator;
      // Create a simple schema with one integer field
      this.schema = new Schema(Arrays.asList(
          new Field("value", FieldType.nullable(new ArrowType.Int(32, true)), null)
      ));
    }

    @Override
    public void listFlights(CallContext context, Criteria criteria, StreamListener<FlightInfo> listener) {
      // Create flight info for our sample data
      FlightDescriptor descriptor = FlightDescriptor.path("sample");
      FlightEndpoint endpoint = new FlightEndpoint(
          new Ticket("sample".getBytes()),
          Location.forGrpcInsecure("localhost", 8815)
      );

      FlightInfo flightInfo = new FlightInfo(
          schema,
          descriptor,
          Collections.singletonList(endpoint),
          -1, // Unknown number of bytes
          10  // 10 rows
      );

      listener.onNext(flightInfo);
      listener.onCompleted();
    }

    @Override
    public FlightInfo getFlightInfo(CallContext context, FlightDescriptor descriptor) {
      if (descriptor.getPath().size() == 1 && "sample".equals(descriptor.getPath().get(0))) {
        FlightEndpoint endpoint = new FlightEndpoint(
            new Ticket("sample".getBytes()),
            Location.forGrpcInsecure("localhost", 8815)
        );

        return new FlightInfo(
            schema,
            descriptor,
            Collections.singletonList(endpoint),
            -1, // Unknown number of bytes
            10  // 10 rows
        );
      }
      throw CallStatus.NOT_FOUND.withDescription("Flight not found: " + descriptor).toRuntimeException();
    }

    @Override
    public void getStream(CallContext context, Ticket ticket, ServerStreamListener listener) {
      String ticketString = new String(ticket.getBytes());

      if ("sample".equals(ticketString)) {
        System.out.println("🔄 getStream called for ticket: " + ticketString);
        System.out.println("⏰ Waiting 70 seconds before sending data...");

        try {
          // Wait for 70 seconds (exceeds 60s NLB idle timeout)
          Thread.sleep(70000);

          // Check if listener was cancelled during the wait
          boolean isCancelled = listener.isCancelled();
          boolean isReady = listener.isReady();
          System.out.println("📊 After 70 seconds - Listener cancelled: " + isCancelled);
          System.out.println("📊 After 70 seconds - Listener isReady: " + isReady);

          if (isCancelled) {
            System.out.println("❌ Request was cancelled, not sending data");
            return;
          }

          System.out.println("✅ Proceeding to send data...");

          // Create sample data
          try (VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator)) {
            IntVector valueVector = (IntVector) root.getVector("value");

            // Generate 10 rows of sample data
            valueVector.allocateNew(10);

            for (int i = 0; i < 10; i++) {
              valueVector.set(i, i * 10); // Values: 0, 10, 20, 30, ..., 90
            }

            valueVector.setValueCount(10);
            root.setRowCount(10);

            System.out.println("📤 Starting data stream...");
            listener.start(root);

            System.out.println("📤 Sending data batch...");
            listener.putNext();

            System.out.println("✅ Data stream completed");
            listener.completed();
          }
        } catch (InterruptedException e) {
          System.out.println("⚠️ Thread was interrupted during wait");
          Thread.currentThread().interrupt();
          listener.error(new RuntimeException("Stream interrupted", e));
        } catch (Exception e) {
          System.out.println("❌ Error in getStream: " + e.getMessage());
          listener.error(e);
        }
      } else {
        System.out.println("❌ Unknown ticket: " + ticketString);
        listener.error(CallStatus.NOT_FOUND.withDescription("Ticket not found: " + ticketString).toRuntimeException());
      }
    }

    @Override
    public void doAction(CallContext context, Action action, StreamListener<Result> listener) {
      if ("echo".equals(action.getType())) {
        // Echo back the action body
        Result result = new Result(action.getBody());
        listener.onNext(result);
        listener.onCompleted();
      } else {
        listener.onError(CallStatus.UNIMPLEMENTED.withDescription("Unknown action: " + action.getType()).toRuntimeException());
      }
    }
  }
}
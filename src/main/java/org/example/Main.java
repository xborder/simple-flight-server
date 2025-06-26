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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Instant;

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

    // Query state management for polling
    private final ConcurrentHashMap<String, QueryState> runningQueries = new ConcurrentHashMap<>();
    private final AtomicLong queryIdCounter = new AtomicLong(0);

    static class QueryState {
      final String queryId;
      final long startTime;
      final long estimatedDuration; // in milliseconds
      final String originalPath;
      volatile double progress;
      volatile boolean completed;
      volatile FlightInfo result;
      final long expirationTime;

      QueryState(String queryId, String originalPath, long estimatedDuration) {
        this.queryId = queryId;
        this.originalPath = originalPath;
        this.startTime = System.currentTimeMillis();
        this.estimatedDuration = estimatedDuration;
        this.progress = 0.0;
        this.completed = false;
        this.expirationTime = startTime + (estimatedDuration * 2); // Expire after 2x estimated duration
      }

      void updateProgress() {
        if (!completed) {
          long elapsed = System.currentTimeMillis() - startTime;
          progress = Math.min(1.0, (double) elapsed / estimatedDuration);
          if (progress >= 1.0) {
            completed = true;
          }
        }
      }

      boolean isExpired() {
        return System.currentTimeMillis() > expirationTime;
      }
    }



    public SampleFlightProducer(BufferAllocator allocator) {
      this.allocator = allocator;
      // Create a simple schema with one integer field
      this.schema = new Schema(Arrays.asList(
          new Field("value", FieldType.nullable(new ArrowType.Int(32, true)), null)
      ));
    }

    @Override
    public void listFlights(CallContext context, Criteria criteria, StreamListener<FlightInfo> listener) {
      // Create flight info for normal sample data
      FlightDescriptor descriptor1 = FlightDescriptor.path("sample");
      FlightEndpoint endpoint1 = new FlightEndpoint(
          new Ticket("sample".getBytes()),
          Location.forGrpcInsecure("localhost", 8815)
      );

      FlightInfo flightInfo1 = new FlightInfo(
          schema,
          descriptor1,
          Collections.singletonList(endpoint1),
          -1, // Unknown number of bytes
          10  // 10 rows
      );

      // Create flight info for delayed sample data
      FlightDescriptor descriptor2 = FlightDescriptor.path("sample-delay");
      FlightEndpoint endpoint2 = new FlightEndpoint(
          new Ticket("sample-delay".getBytes()),
          Location.forGrpcInsecure("localhost", 8815)
      );

      FlightInfo flightInfo2 = new FlightInfo(
          schema,
          descriptor2,
          Collections.singletonList(endpoint2),
          -1, // Unknown number of bytes
          10  // 10 rows
      );

      // Add long-running query options (for PollFlightInfo)
      FlightDescriptor descriptor3 = FlightDescriptor.path("long-query");
      FlightInfo flightInfo3 = new FlightInfo(
          schema,
          descriptor3,
          Collections.emptyList(), // No endpoints - use PollFlightInfo
          -1, // Unknown number of bytes
          100  // 100 rows when completed
      );

      FlightDescriptor descriptor4 = FlightDescriptor.path("very-long-query");
      FlightInfo flightInfo4 = new FlightInfo(
          schema,
          descriptor4,
          Collections.emptyList(), // No endpoints - use PollFlightInfo
          -1, // Unknown number of bytes
          100  // 100 rows when completed
      );

      FlightDescriptor descriptor5 = FlightDescriptor.path("ultra-long-query");
      FlightInfo flightInfo5 = new FlightInfo(
          schema,
          descriptor5,
          Collections.emptyList(), // No endpoints - use PollFlightInfo
          -1, // Unknown number of bytes
          100  // 100 rows when completed
      );

      FlightDescriptor descriptor6 = FlightDescriptor.path("medium-query");
      FlightInfo flightInfo6 = new FlightInfo(
          schema,
          descriptor6,
          Collections.emptyList(), // No endpoints - use PollFlightInfo
          -1, // Unknown number of bytes
          100  // 100 rows when completed
      );

      listener.onNext(flightInfo1);
      listener.onNext(flightInfo2);
      listener.onNext(flightInfo3);
      listener.onNext(flightInfo4);
      listener.onNext(flightInfo5);
      listener.onNext(flightInfo6);
      listener.onCompleted();
    }



    @Override
    public FlightInfo getFlightInfo(CallContext context, FlightDescriptor descriptor) {
      if (descriptor.getPath().size() == 1) {
        String flightPath = descriptor.getPath().get(0);

        // Handle regular flights
        if ("sample".equals(flightPath) || "sample-delay".equals(flightPath)) {
          FlightEndpoint endpoint = new FlightEndpoint(
              new Ticket(flightPath.getBytes()),
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

        // Handle long-running queries - simulate the delay in getFlightInfo
        if ("long-query".equals(flightPath)) {
          return handleLongRunningQuery(flightPath, 120); // 2 minutes
        } else if ("very-long-query".equals(flightPath)) {
          return handleLongRunningQuery(flightPath, 300); // 5 minutes
        } else if ("ultra-long-query".equals(flightPath)) {
          return handleLongRunningQuery(flightPath, 7200); // 2 hours (exceeds NLB timeout)
        }
      }
      throw CallStatus.NOT_FOUND.withDescription("Flight not found: " + descriptor).toRuntimeException();
    }

    private FlightInfo handleLongRunningQuery(String queryType, int durationSeconds) {
      System.out.println("🚀 Starting long-running query: " + queryType + " (duration: " + durationSeconds + "s)");
      System.out.println("⏰ This will simulate a heavy query that takes " + (durationSeconds / 60.0) + " minutes...");

      long startTime = System.currentTimeMillis();
      long endTime = startTime + (durationSeconds * 1000L);

      // Simulate query execution with progress updates
      int progressUpdates = Math.min(10, durationSeconds / 10); // Update every 10% or 10 seconds
      long updateInterval = (durationSeconds * 1000L) / progressUpdates;

      try {
        for (int i = 1; i <= progressUpdates; i++) {
          Thread.sleep(updateInterval);
          double progress = (double) i / progressUpdates * 100;
          System.out.println("📊 Query progress: " + String.format("%.1f%%", progress) +
                           " (elapsed: " + (i * updateInterval / 1000) + "s)");
        }

        System.out.println("✅ Long-running query completed!");

        // Return FlightInfo for the completed query
        FlightEndpoint endpoint = new FlightEndpoint(
            new Ticket((queryType + "-result").getBytes()),
            Location.forGrpcInsecure("localhost", 8815)
        );

        return new FlightInfo(
            schema,
            FlightDescriptor.path(queryType),
            Collections.singletonList(endpoint),
            -1, // Unknown number of bytes
            100 // More rows for long query result
        );

      } catch (InterruptedException e) {
        System.out.println("⚠️ Long-running query was interrupted");
        Thread.currentThread().interrupt();
        throw CallStatus.CANCELLED.withDescription("Query was interrupted").toRuntimeException();
      }
    }

    @Override
    public void getStream(CallContext context, Ticket ticket, ServerStreamListener listener) {
      String ticketString = new String(ticket.getBytes());

      if ("sample".equals(ticketString) || "sample-delay".equals(ticketString)) {
        System.out.println("🔄 getStream called for ticket: " + ticketString);

        // Check if delay is requested
        boolean shouldDelay = "sample-delay".equals(ticketString);

        try {
          if (shouldDelay) {
            System.out.println("⏰ Delay requested - Waiting 70 seconds before sending data...");
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
          } else {
            System.out.println("⚡ No delay requested - Sending data immediately");

            // Check listener status without delay
            boolean isCancelled = listener.isCancelled();
            boolean isReady = listener.isReady();
            System.out.println("📊 Listener cancelled: " + isCancelled);
            System.out.println("📊 Listener isReady: " + isReady);
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
      } else if (ticketString.endsWith("-result")) {
        // Handle query result tickets
        System.out.println("📊 getStream called for query result: " + ticketString);

        try {
          // Send larger dataset for query results (100 rows)
          try (VectorSchemaRoot root = VectorSchemaRoot.create(schema, allocator)) {
            IntVector valueVector = (IntVector) root.getVector("value");

            valueVector.allocateNew(100);

            for (int i = 0; i < 100; i++) {
              valueVector.set(i, i * 5); // Values: 0, 5, 10, 15, ..., 495
            }

            valueVector.setValueCount(100);
            root.setRowCount(100);

            System.out.println("📤 Starting query result stream (100 rows)...");
            listener.start(root);

            System.out.println("📤 Sending query result batch...");
            listener.putNext();

            System.out.println("✅ Query result stream completed");
            listener.completed();
          }
        } catch (Exception e) {
          System.out.println("❌ Error in query result stream: " + e.getMessage());
          listener.error(e);
        }
      } else {
        System.out.println("❌ Unknown ticket: " + ticketString);
        listener.error(CallStatus.NOT_FOUND.withDescription("Ticket not found: " + ticketString).toRuntimeException());
      }
    }

    @Override
    public PollInfo pollFlightInfo(CallContext context, FlightDescriptor descriptor) {
      try {
        System.out.println("🔄 pollFlightInfo called for: " + descriptor.getPath());

        if (descriptor.getPath().size() == 1) {
          String path = descriptor.getPath().get(0);

          // Check if this is a query ID (starts with "poll-query-")
          if (path.startsWith("poll-query-")) {
            return handleExistingPollQuery(path);
          }

          // Handle new long-running query requests
          if ("long-query".equals(path)) {
            return startPollQuery(path, 120000); // 2 minutes
          } else if ("very-long-query".equals(path)) {
            return startPollQuery(path, 300000); // 5 minutes
          } else if ("ultra-long-query".equals(path)) {
            return startPollQuery(path, 7200000); // 2 hours (exceeds NLB timeout)
          } else if ("medium-query".equals(path)) {
          return startPollQuery(path, 60000); // 1 minute (completes before 65s polling)
        }
        }

        throw CallStatus.NOT_FOUND.withDescription("Unknown poll descriptor: " + descriptor).toRuntimeException();
      } catch (Exception e) {
        System.err.println("❌ Error in pollFlightInfo: " + e.getMessage());
        e.printStackTrace();
        throw CallStatus.INTERNAL.withDescription("Internal error: " + e.getMessage()).toRuntimeException();
      }
    }

    private PollInfo startPollQuery(String originalPath, long durationMs) {
      try {
        String queryId = "poll-query-" + queryIdCounter.incrementAndGet();
        QueryState queryState = new QueryState(queryId, originalPath, durationMs);
        runningQueries.put(queryId, queryState);

        System.out.println("🚀 Started polling query: " + queryId + " (duration: " + (durationMs/1000) + "s)");

        // Start background thread to simulate query execution
        Thread queryThread = new Thread(() -> {
          try {
            int progressUpdates = Math.min(10, (int)(durationMs / 10000)); // Update every 10 seconds or 10%
            long updateInterval = durationMs / progressUpdates;

            for (int i = 1; i <= progressUpdates; i++) {
              Thread.sleep(updateInterval);
              queryState.updateProgress();
              System.out.println("📊 Query " + queryId + " progress: " + String.format("%.1f%%", queryState.progress * 100));
            }

            queryState.completed = true;
            System.out.println("✅ Polling query " + queryId + " completed!");

            // Create result FlightInfo
            FlightEndpoint endpoint = new FlightEndpoint(
                new Ticket((queryId + "-result").getBytes()),
                Location.forGrpcInsecure("localhost", 8815)
            );

            queryState.result = new FlightInfo(
                schema,
                FlightDescriptor.path(queryState.originalPath),
                Collections.singletonList(endpoint),
                -1, // Unknown number of bytes
                100 // More rows for long query result
            );

          } catch (InterruptedException e) {
            System.out.println("⚠️ Query " + queryId + " was interrupted");
            Thread.currentThread().interrupt();
          }
        });

        queryThread.start();

        // Return initial PollInfo with partial FlightInfo (as per spec)
        FlightDescriptor pollDescriptor = FlightDescriptor.path(queryId);

        System.out.println("📋 Creating initial PollInfo for query: " + queryId);
        System.out.println("  pollDescriptor: " + pollDescriptor);

        // Create initial FlightInfo with empty endpoints (query not complete yet)
        FlightInfo initialFlightInfo = new FlightInfo(
            schema,
            FlightDescriptor.path(queryState.originalPath),
            Collections.emptyList(), // No endpoints yet - query still running
            -1, // Unknown number of bytes
            -1  // Unknown number of records
        );

        PollInfo result = new PollInfo(
            initialFlightInfo, // Partial FlightInfo (empty endpoints)
            pollDescriptor, // Client should poll with this descriptor
            Double.valueOf(0.0), // Initial progress
            null // No expiration time initially
        );

        System.out.println("📋 Successfully created PollInfo for query: " + queryId);
        return result;
      } catch (Exception e) {
        System.err.println("❌ Error in startPollQuery: " + e.getMessage());
        e.printStackTrace();
        throw new RuntimeException("Failed to start poll query", e);
      }
    }

    private PollInfo handleExistingPollQuery(String queryId) {
      QueryState queryState = runningQueries.get(queryId);

      if (queryState == null) {
        throw CallStatus.NOT_FOUND.withDescription("Query not found: " + queryId).toRuntimeException();
      }

      if (queryState.isExpired()) {
        runningQueries.remove(queryId);
        throw CallStatus.CANCELLED.withDescription("Query expired: " + queryId).toRuntimeException();
      }

      queryState.updateProgress();

      System.out.println("📊 Query " + queryId + " progress: " + String.format("%.1f%%", queryState.progress * 100));

      if (queryState.completed) {
        // Query completed, return final FlightInfo
        System.out.println("✅ Query " + queryId + " completed!");

        runningQueries.remove(queryId); // Clean up completed query

        return new PollInfo(
            queryState.result, // Final result
            null, // No more polling needed (flight_descriptor unset)
            Double.valueOf(1.0), // 100% complete
            null // No expiration time
        );
      } else {
        // Query still running, return progress update with partial FlightInfo
        FlightDescriptor pollDescriptor = FlightDescriptor.path(queryId);

        // Create partial FlightInfo showing current progress (still no endpoints)
        FlightInfo partialFlightInfo = new FlightInfo(
            schema,
            FlightDescriptor.path(queryState.originalPath),
            Collections.emptyList(), // Still no endpoints - query not complete
            -1, // Unknown number of bytes
            -1  // Unknown number of records
        );

        return new PollInfo(
            partialFlightInfo, // Partial FlightInfo (empty endpoints)
            pollDescriptor, // Continue polling with this descriptor
            Double.valueOf(queryState.progress),
            null // No expiration time
        );
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
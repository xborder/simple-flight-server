# Simple Flight Server

A simple Apache Arrow Flight server and client implementation in Java that demonstrates basic Flight RPC functionality.

## Overview

This application can run in two modes:
- **Server Mode**: Starts a Flight server that provides sample data
- **Client Mode**: Connects to a Flight server and retrieves data

## Prerequisites

- Java 21 or higher
- Maven 3.6 or higher

## Building the Project

### Compile Only
```bash
mvn clean compile
```

### Create Executable JAR (Recommended)
```bash
mvn clean package
```

This creates an executable JAR file at `target/simple-flight-server-1.0-SNAPSHOT.jar` (~24MB) that includes all dependencies and can run on any server with Java 17+.

## Running the Application

**Important**: This project includes a `.mvn/jvm.config` file that automatically applies the required JVM arguments for Apache Arrow.

### Method 1: Using Maven (Development)

#### Server Mode
Start the Flight server on port 8815:
```bash
mvn exec:java -Dexec.mainClass="org.example.Main" -Dexec.args="-server"
```

#### Client Mode (Local)
Connect to a local server:
```bash
mvn exec:java -Dexec.mainClass="org.example.Main"
```

#### AWS Client Mode
Connect to AWS-deployed server:
```bash
mvn exec:java -Dexec.mainClass="org.example.AWSFlightClient" -Dexec.args="your-load-balancer-dns.elb.amazonaws.com"
```

**Note**: Due to Maven exec plugin configuration, you may need to use Method 2 for the AWS client.

### Method 2: Using Java with Classpath (Recommended)

First, compile the project:
```bash
mvn compile
```

#### Server Mode
```bash
java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
     -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
     org.example.Main -server
```

#### Client Mode (Local)
```bash
java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
     -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
     org.example.Main
```

#### AWS Client Mode
```bash
java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
     -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
     org.example.AWSFlightClient your-load-balancer-dns.elb.amazonaws.com
```

### Method 3: Using Convenience Scripts

#### Server Mode
```bash
./run-server.sh
```

#### Client Mode
```bash
./run-client.sh
```

### Method 4: Manual JVM Arguments (Alternative)

If you prefer to run without the `.mvn/jvm.config` file:

#### Server Mode
```bash
mvn exec:java -Dexec.mainClass="org.example.Main" \
  -Dexec.args="-server" \
  -Dexec.jvmArgs="--add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED"
```

#### Client Mode
```bash
mvn exec:java -Dexec.mainClass="org.example.Main" \
  -Dexec.jvmArgs="--add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED"
```

## Running with JAR (No Maven Required)

After building the project with `mvn clean package`, you can run the application on any server with Java 17+ using the generated JAR file.

### Server Mode (JAR)
```bash
java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
     -jar target/simple-flight-server-1.0-SNAPSHOT.jar -server
```

### Client Mode (JAR) - Local Connection
```bash
java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
     -jar target/simple-flight-server-1.0-SNAPSHOT.jar
```

### AWS Client Mode (JAR)
```bash
java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
     -jar target/simple-flight-server-1.0-SNAPSHOT.jar your-load-balancer-dns.elb.amazonaws.com
```

**Note**: The main JAR only supports local client connections. For AWS connections, use the AWSFlightClient class with Method 2 above.

## Quick Start Guide

### 1. Compile the Project
```bash
mvn clean compile
```

### 2. Run Server (Choose one method)
```bash
# Method 1: Maven
mvn exec:java -Dexec.mainClass="org.example.Main" -Dexec.args="-server"

# Method 2: Java with classpath (recommended)
java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
     -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
     org.example.Main -server

# Method 3: Convenience script
./run-server.sh
```

### 3. Run Client (Choose one method)
```bash
# Method 1: Maven (local connection)
mvn exec:java -Dexec.mainClass="org.example.Main"

# Method 2: Java with classpath - Local connection
java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
     -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
     org.example.Main

# Method 2: Java with classpath - AWS connection
java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
     -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
     org.example.AWSFlightClient your-load-balancer-dns.elb.amazonaws.com

# Method 3: Convenience script (local connection)
./run-client.sh
```

### Expected Client Behavior
The client will:
1. Connect to the server at localhost:8815 (or specified AWS endpoint)
2. List available flights
3. Get flight information
4. Retrieve sample data (10 rows: 0, 10, 20, ..., 90)
5. Perform an echo action

## Sample Output

### Server Output (Maven Method)
```
WARNING: Unknown module: org.apache.arrow.memory.core specified to --add-opens
[INFO] Scanning for projects...
[INFO]
[INFO] ------------------< org.example:simple-flight-server >------------------
[INFO] Building simple-flight-server 1.0-SNAPSHOT
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO]
[INFO] --- exec:3.5.1:java (default-cli) @ simple-flight-server ---
SLF4J(W): No SLF4J providers were found.
SLF4J(W): Defaulting to no-operation (NOP) logger implementation
SLF4J(W): See https://www.slf4j.org/codes.html#noProviders for further details.
Flight server started on port 8815
Press Ctrl+C to stop the server
```

### Server Output (Java Method)
```
WARNING: Unknown module: org.apache.arrow.memory.core specified to --add-opens
SLF4J(W): No SLF4J providers were found.
SLF4J(W): Defaulting to no-operation (NOP) logger implementation
SLF4J(W): See https://www.slf4j.org/codes.html#noProviders for further details.
Flight server started on port 8815
Press Ctrl+C to stop the server
```

### Local Client Output (Maven Method)
```
WARNING: Unknown module: org.apache.arrow.memory.core specified to --add-opens
[INFO] Scanning for projects...
[INFO]
[INFO] ------------------< org.example:simple-flight-server >------------------
[INFO] Building simple-flight-server 1.0-SNAPSHOT
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO]
[INFO] --- exec:3.5.1:java (default-cli) @ simple-flight-server ---
SLF4J(W): No SLF4J providers were found.
SLF4J(W): Defaulting to no-operation (NOP) logger implementation
SLF4J(W): See https://www.slf4j.org/codes.html#noProviders for further details.
Connected to server at Location{uri=grpc+tcp://localhost:8815}
Listing flights:
Found flight: sample
Schema: Schema<value: Int(32, true)>
Got flight info for: sample
Getting data stream:
Stream schema: Schema<value: Int(32, true)>
Received batch with 10 rows:
  Row 0: 0
  Row 1: 10
  Row 2: 20
  Row 3: 30
  Row 4: 40
  Row 5: 50
  Row 6: 60
  Row 7: 70
  Row 8: 80
  Row 9: 90
Performing action:
Action result: Hello, Flight!
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### AWS Client Output (Java Method)
```
WARNING: Unknown module: org.apache.arrow.memory.core specified to --add-opens
Connecting to AWS Flight Server...
Host: your-load-balancer-dns.elb.amazonaws.com
Port: 8815

SLF4J(W): No SLF4J providers were found.
SLF4J(W): Defaulting to no-operation (NOP) logger implementation
SLF4J(W): See https://www.slf4j.org/codes.html#noProviders for further details.
✓ Connected to AWS Flight server at Location{uri=grpc+tcp://your-load-balancer-dns.elb.amazonaws.com:8815}

📋 Listing flights:
  Found flight: sample
  Schema: Schema<value: Int(32, true)>

📊 Got flight info for: sample
  Endpoints: 1
  Records: 10

📥 Getting data stream:
  Stream schema: Schema<value: Int(32, true)>
  Received batch with 10 rows:
    Row 0: 0
    Row 1: 10
    Row 2: 20
    Row 3: 30
    Row 4: 40
    Row 5: 50
    Row 6: 60
    Row 7: 70
    Row 8: 80
    Row 9: 90

🔄 Performing action:
  Action result: Hello from AWS client!

✅ AWS Flight client test completed successfully!
```

## Important Notes

### JVM Arguments Required

The `--add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED` JVM argument is **required** for Apache Arrow to access Java's internal memory structures. This project includes a `.mvn/jvm.config` file that automatically applies this argument.

**Note**: You may see a warning `WARNING: Unknown module: org.apache.arrow.memory.core specified to --add-opens` - this is harmless and can be ignored.

### Port Configuration

Both server and client are configured to use port 8815. Make sure this port is available when running the server.

### Automatic Configuration

The project includes:
- `.mvn/jvm.config` - Automatically applies required JVM arguments
- `pom.xml` - Configured with all necessary Arrow dependencies

## Project Structure

```
.mvn/
└── jvm.config                # JVM arguments for Arrow compatibility
src/main/java/org/example/
├── Main.java                 # Main application with server/client modes
├── AWSFlightClient.java      # AWS-specific client for testing deployments
└── SampleFlightProducer      # Flight server implementation (inner class)
target/
└── simple-flight-server-1.0-SNAPSHOT.jar  # Executable JAR with all dependencies (~24MB)
aws-infrastructure.yaml       # CloudFormation template for AWS deployment
deploy-to-aws.sh             # AWS deployment automation script
test-aws-deployment.sh       # AWS deployment testing script
AWS-SETUP.md                 # Detailed AWS setup guide
pom.xml                      # Maven configuration with Arrow dependencies
run-server.sh                # Convenience script to run server locally
run-client.sh                # Convenience script to run client locally
```

## Compilation and Execution Summary

### Quick Reference

| Method | Use Case | Command |
|--------|----------|---------|
| **Maven Server** | Development | `mvn exec:java -Dexec.mainClass="org.example.Main" -Dexec.args="-server"` |
| **Maven Client** | Local testing | `mvn exec:java -Dexec.mainClass="org.example.Main"` |
| **Java Server** | Production/Reliable | `java --add-opens=... -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" org.example.Main -server` |
| **Java Local Client** | Local testing | `java --add-opens=... -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" org.example.Main` |
| **Java AWS Client** | AWS testing | `java --add-opens=... -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" org.example.AWSFlightClient <hostname>` |
| **JAR Server** | Deployment | `java --add-opens=... -jar target/simple-flight-server-1.0-SNAPSHOT.jar -server` |
| **JAR Client** | Deployment | `java --add-opens=... -jar target/simple-flight-server-1.0-SNAPSHOT.jar` |

### Recommended Approach
1. **Development**: Use Maven methods for quick testing
2. **Production/AWS**: Use Java with classpath for reliability
3. **Deployment**: Use JAR files for standalone execution

## Dependencies

The project uses the following Apache Arrow dependencies:
- `flight-core` (17.0.0) - Flight server/client functionality
- `arrow-memory-core` (17.0.0) - Memory management
- `arrow-vector` (17.0.0) - Vector operations
- `arrow-memory-netty` (17.0.0) - Runtime memory implementation

**Java Version**: Compiled for Java 17 (compatible with Java 17+)

## Deployment

### Server Deployment

1. Build the JAR: `mvn clean package`
2. Copy `target/simple-flight-server-1.0-SNAPSHOT.jar` to your server
3. Run: `java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED -jar simple-flight-server-1.0-SNAPSHOT.jar -server`

The JAR file (~24MB) contains all dependencies and only requires Java 21+ on the target server.

### Client Usage

The same JAR can be used as a client by omitting the `-server` argument:
```bash
java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED -jar simple-flight-server-1.0-SNAPSHOT.jar
```

## AWS Deployment

This project includes complete AWS infrastructure automation to deploy the Flight server on EC2 behind a Network Load Balancer.

### Quick AWS Deployment

1. **Setup AWS access** (see [AWS-SETUP.md](AWS-SETUP.md) for detailed instructions):
   ```bash
   aws configure
   ```

2. **Create EC2 Key Pair**:
   ```bash
   aws ec2 create-key-pair --key-name flight-server-key --query 'KeyMaterial' --output text > flight-server-key.pem
   chmod 400 flight-server-key.pem
   ```

3. **Deploy to AWS**:
   ```bash
   ./deploy-to-aws.sh
   ```

4. **Test the deployment**:
   ```bash
   ./test-aws-deployment.sh
   ```

### What Gets Deployed

- **EC2 Instance**: Amazon Linux 2023 with Java 21 and your Flight server
- **Network Load Balancer**: Internet-facing NLB for gRPC traffic on port 8815
- **VPC & Networking**: Complete networking setup with security groups
- **Auto-start Service**: Systemd service that automatically starts your Flight server

### AWS Client Usage

Test your AWS deployment with the included AWS client:
```bash
mvn exec:java -Dexec.mainClass="org.example.AWSFlightClient" -Dexec.args="your-load-balancer-dns.elb.amazonaws.com"
```

For detailed AWS setup instructions, see [AWS-SETUP.md](AWS-SETUP.md).

## Troubleshooting

### Connection Refused Error
If the client shows "Connection refused", make sure the server is running first.

### Memory Initialization Error
If you see "Failed to initialize MemoryUtil", ensure you're using the required JVM arguments.

### Port Already in Use
If port 8815 is already in use, you'll need to stop the existing process or modify the port in the code.

### JAR File Not Found
If you get "JAR file not found", run `mvn clean package` first to build the executable JAR.

### Compilation Issues
If you encounter compilation errors:
```bash
# Clean and rebuild
mvn clean compile

# Or clean and package
mvn clean package
```

### Maven Exec Plugin Issues
If the Maven exec plugin doesn't work correctly:
- Use **Method 2** (Java with classpath) instead
- The classpath method is more reliable for running different main classes

### AWS Client Connection Issues
If the AWS client shows connection errors:
- Verify the load balancer DNS name is correct
- Ensure the server is running and healthy
- Check that port 8815 is accessible
- Use the AWSFlightClient class with Method 2 (Java with classpath)

### Wrong Main Class Executed
If you see the wrong client running (e.g., local client instead of AWS client):
- Use **Method 2** (Java with classpath) which allows explicit main class specification
- The Maven exec plugin may have configuration conflicts

### Classpath Issues
If you get "ClassNotFoundException":
```bash
# Regenerate the classpath
mvn dependency:build-classpath -Dmdep.outputFile=classpath.txt
cat classpath.txt

# Use the explicit classpath
java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \
     -cp "target/classes:$(cat classpath.txt)" \
     org.example.Main -server
```

## Development

To modify the server behavior, edit the `SampleFlightProducer` class in `Main.java`. The producer implements:
- `listFlights()` - Returns available flights
- `getFlightInfo()` - Provides flight metadata
- `getStream()` - Serves data streams
- `doAction()` - Handles custom actions

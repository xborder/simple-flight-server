#!/bin/bash

# Flight Server AWS Deployment Script
# This script deploys the Flight server to AWS using CloudFormation

set -e

# Configuration
STACK_NAME="flight-server-stack"
TEMPLATE_FILE="aws-infrastructure.yaml"
JAR_FILE="target/simple-flight-server-1.0-SNAPSHOT.jar"
REGION="us-east-1"  # Change this to your preferred region

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Flight Server AWS Deployment Script${NC}"
echo "======================================"

# Check for update-server flag
if [[ "$1" == "--update-server" ]]; then
    echo -e "\n${YELLOW}Server Update Mode${NC}"
    echo "Updating existing deployment with new server binary..."

    # Check prerequisites for update
    if ! command -v aws &> /dev/null; then
        echo -e "${RED}Error: AWS CLI is not installed.${NC}"
        exit 1
    fi

    if ! aws sts get-caller-identity &> /dev/null; then
        echo -e "${RED}Error: AWS CLI is not configured.${NC}"
        exit 1
    fi

    if [ ! -f "$JAR_FILE" ]; then
        echo -e "${RED}Error: JAR file not found at $JAR_FILE${NC}"
        echo "Please run 'mvn clean package' first to build the JAR."
        exit 1
    fi

    # Get existing deployment info
    echo -e "\n${YELLOW}Getting existing deployment information...${NC}"
    INSTANCE_IP=$(aws cloudformation describe-stacks \
        --stack-name "$STACK_NAME" \
        --region "$REGION" \
        --query 'Stacks[0].Outputs[?OutputKey==`InstancePublicIP`].OutputValue' \
        --output text 2>/dev/null)

    if [ -z "$INSTANCE_IP" ] || [ "$INSTANCE_IP" == "None" ]; then
        echo -e "${RED}Error: Could not find existing deployment. Stack '$STACK_NAME' not found.${NC}"
        echo "Please run full deployment first: ./deploy-to-aws.sh"
        exit 1
    fi

    echo "Found existing instance: $INSTANCE_IP"

    # Upload and restart server
    echo -e "\n${YELLOW}Uploading new server binary...${NC}"
    scp -i flight-server-key.pem -o StrictHostKeyChecking=no "$JAR_FILE" "ec2-user@$INSTANCE_IP:/tmp/"

    echo -e "\n${YELLOW}Restarting Flight server...${NC}"
    ssh -i flight-server-key.pem -o StrictHostKeyChecking=no "ec2-user@$INSTANCE_IP" << 'EOF'
sudo systemctl stop flight-server
sudo mv /tmp/simple-flight-server-1.0-SNAPSHOT.jar /opt/flight-server/
sudo chown flight:flight /opt/flight-server/simple-flight-server-1.0-SNAPSHOT.jar
sudo systemctl start flight-server
echo "Waiting for service to start..."
sleep 5
sudo systemctl status flight-server
EOF

    echo -e "\n${GREEN}✓ Server update completed successfully!${NC}"

    # Get load balancer info
    LOAD_BALANCER_DNS=$(aws cloudformation describe-stacks \
        --stack-name "$STACK_NAME" \
        --region "$REGION" \
        --query 'Stacks[0].Outputs[?OutputKey==`LoadBalancerDNS`].OutputValue' \
        --output text 2>/dev/null)

    FLIGHT_ENDPOINT=$(aws cloudformation describe-stacks \
        --stack-name "$STACK_NAME" \
        --region "$REGION" \
        --query 'Stacks[0].Outputs[?OutputKey==`LoadBalancerEndpoint`].OutputValue' \
        --output text 2>/dev/null)

    echo ""
    echo "======================================"
    echo -e "${GREEN}Updated Deployment Information:${NC}"
    echo "======================================"
    echo "Instance Public IP: $INSTANCE_IP"
    echo "Load Balancer DNS: $LOAD_BALANCER_DNS"
    echo "Flight Server Endpoint: $FLIGHT_ENDPOINT"
    echo ""
    echo "Test the updated server:"
    echo "java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED \\"
    echo "     -cp \"target/classes:\$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)\" \\"
    echo "     org.example.AWSFlightClient $LOAD_BALANCER_DNS"

    exit 0
fi

# Check prerequisites
echo -e "\n${YELLOW}Checking prerequisites...${NC}"

# Check if AWS CLI is installed
if ! command -v aws &> /dev/null; then
    echo -e "${RED}Error: AWS CLI is not installed. Please install it first.${NC}"
    echo "Install instructions: https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html"
    exit 1
fi

# Check if AWS CLI is configured
if ! aws sts get-caller-identity &> /dev/null; then
    echo -e "${RED}Error: AWS CLI is not configured. Please run 'aws configure' first.${NC}"
    exit 1
fi

# Check if JAR file exists
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}Error: JAR file not found at $JAR_FILE${NC}"
    echo "Please run 'mvn clean package' first to build the JAR."
    exit 1
fi

# Check if CloudFormation template exists
if [ ! -f "$TEMPLATE_FILE" ]; then
    echo -e "${RED}Error: CloudFormation template not found at $TEMPLATE_FILE${NC}"
    exit 1
fi

echo -e "${GREEN}✓ All prerequisites met${NC}"

# Get user inputs
echo -e "\n${YELLOW}Configuration:${NC}"
read -p "Enter AWS region (default: $REGION): " input_region
REGION=${input_region:-$REGION}

read -p "Enter EC2 Key Pair name (required for SSH access): " KEY_PAIR_NAME
if [ -z "$KEY_PAIR_NAME" ]; then
    echo -e "${RED}Error: Key Pair name is required${NC}"
    exit 1
fi

read -p "Enter instance type (default: t3.micro): " input_instance_type
INSTANCE_TYPE=${input_instance_type:-t3.micro}

read -p "Enter allowed CIDR for access (default: 0.0.0.0/0): " input_cidr
ALLOWED_CIDR=${input_cidr:-0.0.0.0/0}

echo -e "\n${YELLOW}Deployment Configuration:${NC}"
echo "Stack Name: $STACK_NAME"
echo "Region: $REGION"
echo "Instance Type: $INSTANCE_TYPE"
echo "Key Pair: $KEY_PAIR_NAME"
echo "Allowed CIDR: $ALLOWED_CIDR"

read -p "Continue with deployment? (y/N): " confirm
if [[ ! $confirm =~ ^[Yy]$ ]]; then
    echo "Deployment cancelled."
    exit 0
fi

# Deploy CloudFormation stack
echo -e "\n${YELLOW}Deploying CloudFormation stack...${NC}"
aws cloudformation deploy \
    --template-file "$TEMPLATE_FILE" \
    --stack-name "$STACK_NAME" \
    --parameter-overrides \
        InstanceType="$INSTANCE_TYPE" \
        KeyPairName="$KEY_PAIR_NAME" \
        AllowedCIDR="$ALLOWED_CIDR" \
    --capabilities CAPABILITY_IAM \
    --region "$REGION"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ CloudFormation stack deployed successfully${NC}"
else
    echo -e "${RED}✗ CloudFormation deployment failed${NC}"
    exit 1
fi

# Get stack outputs
echo -e "\n${YELLOW}Getting deployment information...${NC}"
INSTANCE_IP=$(aws cloudformation describe-stacks \
    --stack-name "$STACK_NAME" \
    --region "$REGION" \
    --query 'Stacks[0].Outputs[?OutputKey==`InstancePublicIP`].OutputValue' \
    --output text)

LOAD_BALANCER_DNS=$(aws cloudformation describe-stacks \
    --stack-name "$STACK_NAME" \
    --region "$REGION" \
    --query 'Stacks[0].Outputs[?OutputKey==`LoadBalancerDNS`].OutputValue' \
    --output text)

FLIGHT_ENDPOINT=$(aws cloudformation describe-stacks \
    --stack-name "$STACK_NAME" \
    --region "$REGION" \
    --query 'Stacks[0].Outputs[?OutputKey==`LoadBalancerEndpoint`].OutputValue' \
    --output text)

# Upload JAR file to EC2 instance
echo -e "\n${YELLOW}Uploading JAR file to EC2 instance...${NC}"
echo "Waiting for instance to be ready..."
sleep 30  # Give the instance time to complete initialization

# Copy JAR file
echo "Uploading $JAR_FILE to EC2 instance..."
scp -i "$KEY_PAIR_NAME.pem" -o StrictHostKeyChecking=no "$JAR_FILE" "ec2-user@$INSTANCE_IP:/tmp/"

# Install and start the service
echo "Installing and starting Flight server service..."
ssh -i "$KEY_PAIR_NAME.pem" -o StrictHostKeyChecking=no "ec2-user@$INSTANCE_IP" << 'EOF'
sudo systemctl stop flight-server 2>/dev/null || true
sudo mv /tmp/simple-flight-server-1.0-SNAPSHOT.jar /opt/flight-server/
sudo chown flight:flight /opt/flight-server/simple-flight-server-1.0-SNAPSHOT.jar
sudo systemctl start flight-server
sudo systemctl status flight-server
EOF

echo -e "\n${GREEN}✓ Deployment completed successfully!${NC}"
echo ""
echo "======================================"
echo -e "${GREEN}Deployment Information:${NC}"
echo "======================================"
echo "Instance Public IP: $INSTANCE_IP"
echo "Load Balancer DNS: $LOAD_BALANCER_DNS"
echo "Flight Server Endpoint: $FLIGHT_ENDPOINT"
echo ""
echo "SSH Command:"
echo "ssh -i $KEY_PAIR_NAME.pem ec2-user@$INSTANCE_IP"
echo ""
echo "Test your Flight client with:"
echo "java --add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED -jar $JAR_FILE"
echo "(Update your client code to connect to: $FLIGHT_ENDPOINT)"
echo ""
echo -e "${YELLOW}Note: It may take a few minutes for the load balancer health checks to pass.${NC}"
echo ""
echo "======================================"
echo -e "${GREEN}Update Commands:${NC}"
echo "======================================"
echo "To update the server binary only:"
echo "./deploy-to-aws.sh --update-server"
echo ""
echo "To rebuild and update:"
echo "mvn clean package && ./deploy-to-aws.sh --update-server"

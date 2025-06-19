#!/bin/bash

# Test AWS Flight Server Deployment
# This script helps test your deployed Flight server on AWS

set -e

STACK_NAME="flight-server-stack"
REGION="us-east-1"  # Change this to match your deployment region

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}AWS Flight Server Deployment Test${NC}"
echo "=================================="

# Check if AWS CLI is available
if ! command -v aws &> /dev/null; then
    echo -e "${RED}Error: AWS CLI is not installed.${NC}"
    exit 1
fi

# Get deployment information
echo -e "\n${YELLOW}Getting deployment information...${NC}"

# Check if stack exists
if ! aws cloudformation describe-stacks --stack-name "$STACK_NAME" --region "$REGION" &> /dev/null; then
    echo -e "${RED}Error: CloudFormation stack '$STACK_NAME' not found in region '$REGION'${NC}"
    echo "Please deploy the infrastructure first using ./deploy-to-aws.sh"
    exit 1
fi

# Get stack outputs
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

INSTANCE_ID=$(aws cloudformation describe-stacks \
    --stack-name "$STACK_NAME" \
    --region "$REGION" \
    --query 'Stacks[0].Outputs[?OutputKey==`InstanceId`].OutputValue' \
    --output text)

echo -e "${GREEN}✓ Found deployment information${NC}"
echo "Instance ID: $INSTANCE_ID"
echo "Instance IP: $INSTANCE_IP"
echo "Load Balancer: $LOAD_BALANCER_DNS"
echo "Flight Endpoint: $FLIGHT_ENDPOINT"

# Check instance status
echo -e "\n${YELLOW}Checking EC2 instance status...${NC}"
INSTANCE_STATE=$(aws ec2 describe-instances \
    --instance-ids "$INSTANCE_ID" \
    --region "$REGION" \
    --query 'Reservations[0].Instances[0].State.Name' \
    --output text)

if [ "$INSTANCE_STATE" = "running" ]; then
    echo -e "${GREEN}✓ EC2 instance is running${NC}"
else
    echo -e "${RED}✗ EC2 instance is not running (state: $INSTANCE_STATE)${NC}"
    exit 1
fi

# Check load balancer target health
echo -e "\n${YELLOW}Checking load balancer target health...${NC}"
TARGET_GROUP_ARN=$(aws elbv2 describe-target-groups \
    --names "FlightServer-TG" \
    --region "$REGION" \
    --query 'TargetGroups[0].TargetGroupArn' \
    --output text)

TARGET_HEALTH=$(aws elbv2 describe-target-health \
    --target-group-arn "$TARGET_GROUP_ARN" \
    --region "$REGION" \
    --query 'TargetHealthDescriptions[0].TargetHealth.State' \
    --output text)

if [ "$TARGET_HEALTH" = "healthy" ]; then
    echo -e "${GREEN}✓ Load balancer target is healthy${NC}"
elif [ "$TARGET_HEALTH" = "initial" ] || [ "$TARGET_HEALTH" = "unhealthy" ]; then
    echo -e "${YELLOW}⚠ Load balancer target is $TARGET_HEALTH (may need more time)${NC}"
    echo "Health checks can take 2-3 minutes to pass. You can continue testing."
else
    echo -e "${RED}✗ Load balancer target health: $TARGET_HEALTH${NC}"
fi

# Test network connectivity
echo -e "\n${YELLOW}Testing network connectivity...${NC}"
if timeout 10 bash -c "</dev/tcp/$LOAD_BALANCER_DNS/8815" 2>/dev/null; then
    echo -e "${GREEN}✓ Port 8815 is accessible on load balancer${NC}"
else
    echo -e "${RED}✗ Cannot connect to port 8815 on load balancer${NC}"
    echo "This might be normal if the service is still starting up."
fi

# Compile and test the AWS client
echo -e "\n${YELLOW}Compiling AWS Flight client...${NC}"
if mvn compile -q; then
    echo -e "${GREEN}✓ AWS Flight client compiled successfully${NC}"
else
    echo -e "${RED}✗ Failed to compile AWS Flight client${NC}"
    exit 1
fi

# Test the Flight client
echo -e "\n${YELLOW}Testing Flight client connection...${NC}"
echo "Connecting to: $LOAD_BALANCER_DNS"
echo ""

if mvn exec:java -Dexec.mainClass="org.example.AWSFlightClient" -Dexec.args="$LOAD_BALANCER_DNS" -q; then
    echo -e "\n${GREEN}✅ Flight client test completed successfully!${NC}"
else
    echo -e "\n${RED}❌ Flight client test failed${NC}"
    echo ""
    echo -e "${YELLOW}Troubleshooting steps:${NC}"
    echo "1. Wait a few more minutes for the service to fully start"
    echo "2. Check the service status on the EC2 instance:"
    echo "   ssh -i your-key.pem ec2-user@$INSTANCE_IP"
    echo "   sudo systemctl status flight-server"
    echo "   sudo journalctl -u flight-server -f"
    echo "3. Verify the load balancer health checks are passing"
    echo "4. Check security group rules allow traffic on port 8815"
fi

echo ""
echo "=================================="
echo -e "${BLUE}Deployment Summary${NC}"
echo "=================================="
echo "Flight Server Endpoint: $FLIGHT_ENDPOINT"
echo "Instance SSH: ssh -i your-key.pem ec2-user@$INSTANCE_IP"
echo "AWS Console: https://console.aws.amazon.com/ec2/v2/home?region=$REGION#Instances:instanceId=$INSTANCE_ID"
echo ""
echo "To use this endpoint in your client code:"
echo 'Location location = Location.forGrpcInsecure("'$LOAD_BALANCER_DNS'", 8815);'

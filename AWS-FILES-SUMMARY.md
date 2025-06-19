# AWS Deployment Files Summary

This document provides an overview of all the AWS-related files created for deploying your Flight server to AWS.

## 📁 Files Overview

### 🏗️ Infrastructure Files

#### `aws-infrastructure.yaml`
**CloudFormation template** that creates the complete AWS infrastructure:
- **VPC & Networking**: VPC, subnets, internet gateway, route tables
- **Security Groups**: For EC2 instance and Network Load Balancer
- **EC2 Instance**: Amazon Linux 2023 with Java 21 and systemd service
- **Network Load Balancer**: Internet-facing NLB for gRPC traffic
- **IAM Roles**: For EC2 instance permissions

**Key Features:**
- Parameterized for flexibility (instance type, key pair, CIDR)
- Automatic Java 21 installation and service setup
- Health checks and monitoring ready
- Multi-AZ deployment for high availability

### 🚀 Deployment Scripts

#### `deploy-to-aws.sh`
**Automated deployment script** that:
- Validates prerequisites (AWS CLI, JAR file, etc.)
- Prompts for configuration (region, instance type, key pair)
- Deploys CloudFormation stack
- Uploads JAR file to EC2 instance
- Starts the Flight server service
- Provides connection information

**Usage:**
```bash
./deploy-to-aws.sh
```

#### `test-aws-deployment.sh`
**Testing and validation script** that:
- Checks deployment status
- Validates EC2 instance health
- Tests load balancer target health
- Verifies network connectivity
- Runs Flight client test
- Provides troubleshooting information

**Usage:**
```bash
./test-aws-deployment.sh
```

### 📖 Documentation

#### `AWS-SETUP.md`
**Comprehensive setup guide** covering:
- AWS CLI installation for all platforms
- AWS account setup and IAM configuration
- EC2 key pair creation
- Step-by-step deployment process
- Testing and validation procedures
- Troubleshooting common issues
- Cost estimation and security considerations

### 💻 Application Files

#### `src/main/java/org/example/AWSFlightClient.java`
**AWS-specific Flight client** that:
- Connects to AWS-deployed Flight server
- Provides enhanced error messages and troubleshooting
- Includes user-friendly output with emojis
- Validates connection parameters
- Tests all Flight operations (list, info, stream, actions)

**Usage:**
```bash
mvn exec:java -Dexec.mainClass="org.example.AWSFlightClient" -Dexec.args="your-nlb-dns.elb.amazonaws.com"
```

## 🔄 Deployment Workflow

### 1. Prerequisites Setup
```bash
# Install AWS CLI
# Configure AWS credentials
aws configure

# Create EC2 key pair
aws ec2 create-key-pair --key-name flight-server-key --query 'KeyMaterial' --output text > flight-server-key.pem
chmod 400 flight-server-key.pem
```

### 2. Build Application
```bash
mvn clean package
```

### 3. Deploy to AWS
```bash
./deploy-to-aws.sh
```

### 4. Test Deployment
```bash
./test-aws-deployment.sh
```

## 🏗️ Infrastructure Components

### Network Load Balancer
- **Type**: Network Load Balancer (Layer 4)
- **Scheme**: Internet-facing
- **Protocol**: TCP
- **Port**: 8815
- **Health Checks**: TCP on port 8815

### EC2 Instance
- **AMI**: Amazon Linux 2023
- **Default Type**: t3.medium
- **Java**: OpenJDK 21 (Amazon Corretto)
- **Service**: Systemd service for auto-start
- **Monitoring**: CloudWatch agent enabled

### Security
- **Security Groups**: Restrictive rules (only necessary ports)
- **IAM Roles**: Minimal permissions for CloudWatch
- **SSH Access**: Key-based authentication only

### Networking
- **VPC**: Dedicated VPC (10.0.0.0/16)
- **Subnets**: Public subnets in 2 AZs
- **Internet Gateway**: For public internet access
- **Route Tables**: Proper routing configuration

## 💰 Cost Considerations

**Estimated Monthly Costs (us-east-1):**
- t3.medium EC2: ~$30
- Network Load Balancer: ~$16 + data processing
- Data Transfer: Variable
- **Total**: ~$50-60/month

## 🔧 Customization Options

### Instance Types
- **t3.small**: $15/month (1 vCPU, 2GB RAM) - Light workloads
- **t3.medium**: $30/month (2 vCPU, 4GB RAM) - Default
- **t3.large**: $60/month (2 vCPU, 8GB RAM) - Higher memory
- **m5.large**: $70/month (2 vCPU, 8GB RAM) - Consistent performance

### Security
- Modify `AllowedCIDR` parameter to restrict access
- Add SSL/TLS termination at load balancer
- Implement VPN or private connectivity

### Scaling
- Add Auto Scaling Groups for multiple instances
- Implement health checks and automatic replacement
- Add CloudWatch alarms and notifications

## 🛠️ Troubleshooting

### Common Issues
1. **Key Pair Not Found**: Ensure key exists in correct region
2. **Permission Denied**: Check AWS credentials and permissions
3. **Service Not Starting**: Check systemd logs on EC2 instance
4. **Health Check Failing**: Verify service is running on port 8815
5. **Client Connection Issues**: Use load balancer DNS, not instance IP

### Useful Commands
```bash
# Check service status
ssh -i flight-server-key.pem ec2-user@INSTANCE_IP
sudo systemctl status flight-server
sudo journalctl -u flight-server -f

# Check load balancer health
aws elbv2 describe-target-health --target-group-arn TARGET_GROUP_ARN

# View CloudFormation events
aws cloudformation describe-stack-events --stack-name flight-server-stack
```

## 🧹 Cleanup

To avoid ongoing charges:
```bash
aws cloudformation delete-stack --stack-name flight-server-stack --region us-east-1
```

This will delete all resources created by the CloudFormation template.

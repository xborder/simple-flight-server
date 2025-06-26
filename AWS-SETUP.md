# AWS Setup Guide for Flight Server Deployment

This guide walks you through setting up AWS access and deploying your Flight server to AWS EC2 behind a Network Load Balancer.

## Prerequisites

1. **AWS Account**: You need an active AWS account
2. **Java Application**: Built JAR file (`mvn clean package`)
3. **AWS CLI**: Installed and configured on your local machine

## Step 1: Install AWS CLI

### macOS
```bash
curl "https://awscli.amazonaws.com/AWSCLIV2.pkg" -o "AWSCLIV2.pkg"
sudo installer -pkg AWSCLIV2.pkg -target /
```

### Linux
```bash
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install
```

### Windows
Download and run the AWS CLI MSI installer from:
https://awscli.amazonaws.com/AWSCLIV2.msi

### Verify Installation
```bash
aws --version
```

## Step 2: Create AWS Access Keys

1. **Log into AWS Console**: https://console.aws.amazon.com/
2. **Navigate to IAM**: Services → IAM
3. **Create a User**:
   - Click "Users" → "Add users"
   - Username: `flight-server-deployer`
   - Select "Programmatic access"
4. **Attach Policies**:
   - `AmazonEC2FullAccess`
   - `CloudFormationFullAccess`
   - `ElasticLoadBalancingFullAccess`
   - `IAMFullAccess` (for creating roles)
5. **Save Access Keys**: Download the CSV file with your Access Key ID and Secret Access Key

## Step 3: Configure AWS CLI

```bash
aws configure
```

Enter the following when prompted:
- **AWS Access Key ID**: Your access key from Step 2
- **AWS Secret Access Key**: Your secret key from Step 2
- **Default region name**: `us-east-1` (or your preferred region)
- **Default output format**: `json`

### Verify Configuration
```bash
aws sts get-caller-identity
```

This should return your account information.

## Step 4: Create EC2 Key Pair

You need an EC2 Key Pair for SSH access to your instance.

### Option A: Create via AWS CLI
```bash
aws ec2 create-key-pair --key-name your-key-name --query 'KeyMaterial' --output text > your-key-name.pem
chmod 400 your-key-name.pem
```

### Option B: Create via AWS Console
1. Go to EC2 Console → Key Pairs
2. Click "Create key pair"
3. Name: `flight-server-key`
4. Type: RSA
5. Format: .pem
6. Download and save the .pem file
7. Set permissions: `chmod 400 flight-server-key.pem`

## Step 5: Deploy the Infrastructure

### Build the JAR (if not already done)
```bash
mvn clean package
```

### Run the Deployment Script
```bash
./deploy-to-aws.sh
```

The script will prompt you for:
- **AWS Region**: Default is `us-east-1`
- **EC2 Key Pair Name**: Enter `flight-server-key` (or your key name)
- **Instance Type**: Default is `t3.medium`
- **Allowed CIDR**: Default is `0.0.0.0/0` (allows access from anywhere)

### What the Deployment Creates

1. **VPC and Networking**:
   - New VPC with public subnets in 2 availability zones
   - Internet Gateway and routing tables
   - Security groups for EC2 and Load Balancer

2. **EC2 Instance**:
   - Amazon Linux 2023 with Java 21 pre-installed
   - Systemd service for the Flight server
   - CloudWatch monitoring enabled

3. **Network Load Balancer**:
   - Internet-facing NLB
   - Target group pointing to EC2 instance
   - Health checks on port 8815

4. **Security**:
   - IAM role for EC2 instance
   - Security groups allowing only necessary traffic

## Step 6: Test the Deployment

After deployment completes, you'll get output like:
```
Instance Public IP: 54.123.45.67
Load Balancer DNS: flight-server-nlb-1234567890.elb.us-east-1.amazonaws.com
Flight Server Endpoint: flight-server-nlb-1234567890.elb.us-east-1.amazonaws.com:8815
```

### Test with SSH
```bash
ssh -i flight-server-key.pem ec2-user@54.123.45.67
```

### Check Service Status
```bash
sudo systemctl status flight-server
sudo journalctl -u flight-server -f
```

### Test Flight Client
Update your client code to connect to the load balancer endpoint:
```java
Location location = Location.forGrpcInsecure("flight-server-nlb-1234567890.elb.us-east-1.amazonaws.com", 8815);
```

## Step 7: Monitoring and Logs

### View Application Logs
```bash
ssh -i flight-server-key.pem ec2-user@54.123.45.67
sudo journalctl -u flight-server -f
```

### CloudWatch Logs
The instance is configured with CloudWatch agent for centralized logging.

## Step 8: Cleanup (When Done)

To avoid ongoing charges, delete the CloudFormation stack:
```bash
aws cloudformation delete-stack --stack-name flight-server-stack --region us-east-1
```

## Troubleshooting

### Common Issues

1. **Key Pair Not Found**:
   - Ensure the key pair exists in the correct region
   - Check the key pair name spelling

2. **Permission Denied (SSH)**:
   - Check key file permissions: `chmod 400 your-key.pem`
   - Verify you're using the correct key file

3. **Service Not Starting**:
   - SSH to instance and check: `sudo systemctl status flight-server`
   - Check logs: `sudo journalctl -u flight-server`

4. **Load Balancer Health Check Failing**:
   - Verify the Flight server is running on port 8815
   - Check security group rules
   - Wait a few minutes for health checks to stabilize

5. **Client Connection Issues**:
   - Ensure you're using the Load Balancer DNS name, not the instance IP
   - Verify port 8815 is accessible
   - Check that your client has the correct JVM arguments

## Cost Estimation

Approximate monthly costs (us-east-1 region):
- **t3.medium EC2 instance**: ~$30/month
- **Network Load Balancer**: ~$16/month + data processing charges
- **Data transfer**: Varies based on usage
- **Total**: ~$50-60/month for basic usage

## Security Considerations

1. **Restrict Access**: Change the `AllowedCIDR` parameter to limit access to specific IP ranges
2. **Key Management**: Store your .pem files securely
3. **Regular Updates**: Keep the EC2 instance updated with security patches
4. **Monitoring**: Set up CloudWatch alarms for unusual activity

## Next Steps

1. **Custom Domain**: Set up a custom domain name pointing to your load balancer
2. **SSL/TLS**: Configure SSL certificates for encrypted communication
3. **Auto Scaling**: Add auto scaling groups for high availability
4. **Monitoring**: Set up detailed CloudWatch monitoring and alerting

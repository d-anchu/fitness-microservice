# 🚀 Deployment Guide - Fitness Microservice Platform

This guide covers the complete deployment process for the Fitness Tracking microservices application, including CI/CD pipeline setup, Docker containerization, and AWS EC2 deployment with blue-green strategy.

---

## 📋 Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Prerequisites](#prerequisites)
3. [GitHub Secrets Configuration](#github-secrets-configuration)
4. [AWS EC2 Setup](#aws-ec2-setup)
5. [CI/CD Pipeline](#cicd-pipeline)
6. [Blue-Green Deployment](#blue-green-deployment)
7. [Manual Deployment](#manual-deployment)
8. [Monitoring & Health Checks](#monitoring--health-checks)
9. [Rollback Procedure](#rollback-procedure)
10. [Troubleshooting](#troubleshooting)

---

## 🏗️ Architecture Overview

### **Microservices Stack**
```
┌─────────────┐
│   Frontend  │ (React + Vite + Nginx)
│   Port 5173 │
└──────┬──────┘
       │
┌──────▼──────┐
│ API Gateway │ (Spring Cloud Gateway)
│  Port 8080  │
└──────┬──────┘
       │
       ├─────────────┬─────────────┬─────────────┐
       │             │             │             │
┌──────▼──────┐ ┌───▼────┐ ┌──────▼──────┐ ┌───▼────┐
│ User Service│ │Activity│ │ AI Service  │ │ Eureka │
│  Port 8081  │ │Port8082│ │  Port 8083  │ │Port8761│
└──────┬──────┘ └───┬────┘ └──────┬──────┘ └────────┘
       │            │              │
       └────────────┼──────────────┘
                    │
         ┌──────────┼──────────┐
         │          │          │
    ┌────▼────┐ ┌──▼──────┐   │
    │ MongoDB │ │ RabbitMQ│   │
    │Port27017│ │Port 5672│   │
    └─────────┘ └─────────┘   │
                               │
                        ┌──────▼──────┐
                        │Config Server│
                        │  Port 8888  │
                        └─────────────┘
```

### **Deployment Architecture**
```
GitHub Repository
       ↓
GitHub Actions CI/CD
       ↓
Build & Test
       ↓
Docker Images → GitHub Container Registry
       ↓
Deploy to EC2 (Blue-Green)
       ↓
Production Environment
```

---

## ✅ Prerequisites

### **1. Required Accounts**
- ✅ **GitHub Account**: For repository and CI/CD
- ✅ **AWS Account**: For EC2 hosting (Free Tier eligible)
- ✅ **GROQ Account**: For AI recommendations API key

### **2. Local Development Tools** (Optional)
- Docker & Docker Compose
- Git
- Java 17 JDK (for local builds)
- Node.js 18+ (for frontend development)

### **3. System Requirements for EC2**

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| Instance Type | t2.small | t2.medium |
| vCPU | 1 | 2 |
| RAM | 2GB | 4GB |
| Storage | 20GB | 30GB |
| OS | Ubuntu 22.04 LTS | Ubuntu 22.04 LTS |

**Note**: Running all 7 services + MongoDB + RabbitMQ requires at least 2-4GB RAM.

---

## 🔐 GitHub Secrets Configuration

Configure these secrets in your GitHub repository for CI/CD automation.

### **Navigate to Secrets**
1. Go to: `https://github.com/YOUR_USERNAME/fitness-microservice`
2. Click **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret** for each secret below

### **Required Secrets**

#### **1. DOCKER_USERNAME** (Optional - if using Docker Hub)
```
Name: DOCKER_USERNAME
Value: your-dockerhub-username
```
**Note**: Not needed if using GitHub Container Registry (ghcr.io)

#### **2. DOCKER_PASSWORD** (Optional - if using Docker Hub)
```
Name: DOCKER_PASSWORD
Value: your-dockerhub-access-token
```
Get token from: https://hub.docker.com/settings/security

#### **3. AWS_ACCESS_KEY_ID**
```
Name: AWS_ACCESS_KEY_ID
Value: AKIAIOSFODNN7EXAMPLE
```

**How to get it:**
1. Go to AWS Console → IAM → Users → Your User
2. Security credentials → Create access key
3. Copy the Access Key ID

#### **4. AWS_SECRET_ACCESS_KEY**
```
Name: AWS_SECRET_ACCESS_KEY
Value: wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
```
**⚠️ Important**: This is shown only once during key creation. Save it securely!

#### **5. EC2_HOST**
```
Name: EC2_HOST
Value: 54.123.45.67
```
Your EC2 instance public IP address (found in EC2 console)

#### **6. EC2_USER**
```
Name: EC2_USER
Value: ubuntu
```
- Use `ubuntu` for Ubuntu AMI
- Use `ec2-user` for Amazon Linux AMI

#### **7. EC2_SSH_KEY**
```
Name: EC2_SSH_KEY
Value: -----BEGIN RSA PRIVATE KEY-----
MIIEpAIBAAKCAQEA...
(entire private key content)
...
-----END RSA PRIVATE KEY-----
```

**How to get it:**
1. Open your `.pem` file in a text editor
2. Copy the **entire content** including BEGIN and END lines
3. Paste into GitHub Secret

#### **8. GROQ_API_KEY**
```
Name: GROQ_API_KEY
Value: gsk_YOUR_GROQ_API_KEY_HERE
```
Your GROQ API key for AI recommendations (get from https://console.groq.com)

---

## 🖥️ AWS EC2 Setup

### **Step 1: Create EC2 Instance**

1. **Login to AWS Console**: https://console.aws.amazon.com
2. **Navigate to EC2**: Services → EC2 → Launch Instance
3. **Configure Instance**:

```yaml
Name: fitness-microservice-prod
AMI: Ubuntu Server 22.04 LTS (Free tier eligible)
Instance Type: t2.medium (2 vCPU, 4GB RAM)
Key Pair: Create new → Download .pem file → Save securely
```

4. **Network Settings**:
```yaml
VPC: Default
Auto-assign Public IP: Enable
Firewall (Security Group):
  - SSH (22): Your IP
  - HTTP (80): 0.0.0.0/0
  - Custom TCP (8080): 0.0.0.0/0  # API Gateway
  - Custom TCP (8761): 0.0.0.0/0  # Eureka
  - Custom TCP (8888): 0.0.0.0/0  # Config Server
  - Custom TCP (5173): 0.0.0.0/0  # Frontend
  - Custom TCP (15672): 0.0.0.0/0 # RabbitMQ Dashboard
```

5. **Storage**: 30GB gp3 (Free tier: 30GB)
6. **Click**: Launch Instance

### **Step 2: Connect to EC2**

```bash
# Change permissions on your key file
chmod 400 your-key.pem

# Connect via SSH
ssh -i your-key.pem ubuntu@YOUR_EC2_PUBLIC_IP
```

### **Step 3: Install Docker**

```bash
# Update system packages
sudo apt update
sudo apt upgrade -y

# Install Docker
sudo apt install -y docker.io

# Install Docker Compose
sudo apt install -y docker-compose

# Start Docker service
sudo systemctl start docker
sudo systemctl enable docker

# Add user to docker group (run commands without sudo)
sudo usermod -aG docker ubuntu

# Logout and login again for group changes to take effect
exit
ssh -i your-key.pem ubuntu@YOUR_EC2_PUBLIC_IP

# Verify installation
docker --version
docker-compose --version
```

### **Step 4: Prepare Deployment Directory**

```bash
# Create deployment directory
mkdir -p ~/fitness-microservice
cd ~/fitness-microservice

# Create deploy scripts directory
mkdir -p deploy
```

---

## 🔄 CI/CD Pipeline

### **Pipeline Stages**

#### **1. Build & Test** (Runs on every push/PR)
```yaml
Triggers: push to main, pull requests
Duration: ~5-8 minutes
Actions:
  - Checkout code
  - Setup Java 17 & Node 18
  - Build all 6 Spring Boot services
  - Build React frontend
  - Run unit tests
  - Upload build artifacts
```

#### **2. Docker Build & Push** (After successful build)
```yaml
Duration: ~10-15 minutes
Actions:
  - Build 7 Docker images
  - Tag with git SHA and 'latest'
  - Push to GitHub Container Registry
  - Cache layers for faster builds
```

#### **3. Deploy to EC2** (Only on main branch)
```yaml
Duration: ~3-5 minutes
Actions:
  - SSH to EC2 instance
  - Pull latest Docker images
  - Run blue-green deployment
  - Execute health checks
  - Switch traffic if healthy
  - Rollback if unhealthy
```

### **Workflow File Location**
`.github/workflows/ci-cd.yml`

### **Manual Trigger**
You can manually trigger deployment:
1. Go to: Actions → CI/CD Pipeline → Run workflow
2. Select branch: main
3. Click: Run workflow

---

## 🔵🟢 Blue-Green Deployment

### **What is Blue-Green Deployment?**

Blue-green deployment is a release strategy that reduces downtime and risk by running two identical production environments:

- **Blue**: Currently running production environment
- **Green**: New version being deployed

### **How It Works**

```
Step 1: Blue environment is running
        └── Users are accessing Blue

Step 2: Deploy new version to Green
        ├── Blue still serving users
        └── Green starting up

Step 3: Run health checks on Green
        ├── If healthy → Continue
        └── If unhealthy → Stop and rollback

Step 4: Switch traffic to Green
        ├── Users now access Green
        └── Blue is idle

Step 5: Stop Blue environment
        └── Free up resources
```

### **Advantages**
- ✅ **Zero downtime**: Users never see outage
- ✅ **Easy rollback**: Just switch back to Blue
- ✅ **Reduced risk**: Test before switching traffic
- ✅ **Fast recovery**: Previous version still available

### **Deployment Script**
Located at: `deploy/deploy.sh`

**Usage:**
```bash
cd ~/fitness-microservice
./deploy/deploy.sh
```

---

## 🛠️ Manual Deployment

If you need to deploy manually (without CI/CD):

### **Step 1: SSH to EC2**
```bash
ssh -i your-key.pem ubuntu@YOUR_EC2_IP
```

### **Step 2: Clone/Update Repository**
```bash
# First time
git clone https://github.com/YOUR_USERNAME/fitness-microservice.git
cd fitness-microservice

# Subsequent deployments
cd fitness-microservice
git pull origin main
```

### **Step 3: Set Environment Variables**
```bash
# Create .env file
cat > .env << EOF
GROQ_API_KEY=your_groq_api_key_here
IMAGE_TAG=latest
EOF
```

### **Step 4: Login to Container Registry**
```bash
# For GitHub Container Registry
echo YOUR_GITHUB_TOKEN | docker login ghcr.io -u YOUR_USERNAME --password-stdin

# For Docker Hub (if using)
echo YOUR_DOCKER_PASSWORD | docker login -u YOUR_USERNAME --password-stdin
```

### **Step 5: Pull Images**
```bash
docker-compose pull
```

### **Step 6: Run Deployment**
```bash
chmod +x deploy/*.sh
./deploy/deploy.sh
```

### **Step 7: Verify Deployment**
```bash
./deploy/health-check.sh
```

---

## 🏥 Monitoring & Health Checks

### **Health Check Endpoints**

| Service | Endpoint | Expected Response |
|---------|----------|-------------------|
| Eureka Server | http://EC2_IP:8761/actuator/health | `{"status":"UP"}` |
| Config Server | http://EC2_IP:8888/actuator/health | `{"status":"UP"}` |
| API Gateway | http://EC2_IP:8080/actuator/health | `{"status":"UP"}` |
| User Service | http://EC2_IP:8081/actuator/health | `{"status":"UP"}` |
| Activity Service | http://EC2_IP:8082/actuator/health | `{"status":"UP"}` |
| AI Service | http://EC2_IP:8083/actuator/health | `{"status":"UP"}` |
| Frontend | http://EC2_IP:5173/health | `healthy` |

### **Run Health Checks**
```bash
# From EC2
cd ~/fitness-microservice
./deploy/health-check.sh fitness-blue
```

### **Check Service Logs**
```bash
# View all logs
docker-compose -p fitness-blue logs

# View specific service logs
docker-compose -p fitness-blue logs user-service

# Follow logs in real-time
docker-compose -p fitness-blue logs -f activity-service

# Last 100 lines
docker-compose -p fitness-blue logs --tail=100 ai-service
```

### **Check Container Status**
```bash
# List all containers
docker ps -a

# Check resource usage
docker stats

# Check specific project containers
docker ps -a | grep fitness-blue
```

---

## ⏪ Rollback Procedure

If a deployment causes issues, you can quickly rollback to the previous version.

### **Automatic Rollback**
If health checks fail during deployment, the system automatically rolls back.

### **Manual Rollback**
If issues are discovered after deployment:

```bash
# SSH to EC2
ssh -i your-key.pem ubuntu@YOUR_EC2_IP

# Navigate to project directory
cd ~/fitness-microservice

# Run rollback script
./deploy/rollback.sh

# Confirm rollback
# Type: yes
```

### **What Rollback Does**
1. Stops current (problematic) environment
2. Starts previous (stable) environment
3. Updates environment marker
4. Verifies health of previous environment

### **Rollback Time**
- **Typical**: 30-60 seconds
- **Includes**: Stop current + Start previous + Health checks

---

## 🔧 Troubleshooting

### **Problem: Deployment Fails with Health Check Errors**

**Symptoms:**
```
❌ user-service is unhealthy (HTTP 503)
❌ activity-service is unhealthy (HTTP 000)
```

**Solutions:**
1. **Check if MongoDB is running:**
   ```bash
   docker ps | grep mongo
   docker logs fitness-blue_mongodb_1
   ```

2. **Check if RabbitMQ is running:**
   ```bash
   docker ps | grep rabbitmq
   docker logs fitness-blue_rabbitmq_1
   ```

3. **Check service logs:**
   ```bash
   docker-compose -p fitness-blue logs activity-service
   ```

4. **Verify environment variables:**
   ```bash
   docker inspect fitness-blue_aiservice_1 | grep -A 10 Env
   ```

---

### **Problem: Out of Memory**

**Symptoms:**
```
Container keeps restarting
Logs show: java.lang.OutOfMemoryError
```

**Solutions:**
1. **Check available memory:**
   ```bash
   free -h
   docker stats
   ```

2. **Upgrade EC2 instance type:**
   - From t2.small (2GB) to t2.medium (4GB)
   - Or reduce number of services

3. **Adjust Java heap size:**
   ```yaml
   # In docker-compose.yml
   environment:
     JAVA_OPTS: "-Xmx512m -Xms256m"
   ```

---

### **Problem: Port Already in Use**

**Symptoms:**
```
Error: port is already allocated
```

**Solutions:**
1. **Check port usage:**
   ```bash
   sudo netstat -tulpn | grep :8080
   ```

2. **Stop conflicting containers:**
   ```bash
   docker ps -a
   docker stop CONTAINER_ID
   ```

3. **Clean up old containers:**
   ```bash
   docker system prune -a
   ```

---

### **Problem: Cannot Connect to EC2**

**Symptoms:**
```
ssh: connect to host X.X.X.X port 22: Connection refused
```

**Solutions:**
1. **Check EC2 is running:**
   - AWS Console → EC2 → Instances
   - State should be "running"

2. **Check Security Group:**
   - Inbound rules allow SSH (port 22) from your IP

3. **Check key permissions:**
   ```bash
   chmod 400 your-key.pem
   ```

4. **Use correct username:**
   - Ubuntu AMI: `ubuntu`
   - Amazon Linux: `ec2-user`

---

### **Problem: Docker Images Not Pulling**

**Symptoms:**
```
Error response from daemon: pull access denied
```

**Solutions:**
1. **Login to registry:**
   ```bash
   echo $GITHUB_TOKEN | docker login ghcr.io -u YOUR_USERNAME --password-stdin
   ```

2. **Check image names:**
   ```bash
   # In docker-compose.yml, verify:
   image: ghcr.io/YOUR_USERNAME/fitness-microservice/SERVICE_NAME:latest
   ```

3. **Check GitHub Actions completed:**
   - GitHub → Actions → Verify "Docker Build & Push" succeeded

---

## 📞 Support & Resources

### **Documentation**
- Spring Boot Docs: https://spring.io/projects/spring-boot
- Docker Docs: https://docs.docker.com
- AWS EC2 Docs: https://docs.aws.amazon.com/ec2

### **Logs Location**
- **GitHub Actions**: Repository → Actions → Workflow run
- **EC2 Logs**: `docker-compose logs`
- **Health Checks**: `./deploy/health-check.sh`

### **Cost Monitoring**
- AWS Billing Dashboard: https://console.aws.amazon.com/billing
- Set up billing alerts to avoid unexpected charges

---

## ✅ Deployment Checklist

Before deploying to production:

- [ ] All GitHub Secrets configured
- [ ] EC2 instance created and accessible
- [ ] Docker & Docker Compose installed on EC2
- [ ] Security groups configured correctly
- [ ] GitHub Actions workflow passing
- [ ] Health check script tested
- [ ] Rollback script tested
- [ ] MongoDB data backed up (if applicable)
- [ ] Team notified of deployment
- [ ] Monitoring dashboard ready

---

## 🎉 Success!

If everything is configured correctly:

1. **Push code to main branch**
2. **GitHub Actions automatically:**
   - Builds all services
   - Creates Docker images
   - Deploys to EC2 with blue-green strategy
3. **Access your application:**
   - Frontend: `http://YOUR_EC2_IP:5173`
   - API Gateway: `http://YOUR_EC2_IP:8080`
   - Eureka Dashboard: `http://YOUR_EC2_IP:8761`

**🚀 You now have a fully automated CI/CD pipeline with zero-downtime deployments!**

# GitHub Actions CI/CD Workflow

## Overview

This directory contains the GitHub Actions workflow for the Fitness Microservices project.

## Workflow: `ci-cd.yml`

### Triggers
- **Push to `main` branch**: Runs full CI/CD (build + deploy)
- **Pull Request to `main`**: Runs CI only (build + test)
- **Manual dispatch**: Can be triggered manually from GitHub Actions tab

### Jobs

#### 1. **CI (Continuous Integration)**
- **Runs on**: Every push and pull request
- **Purpose**: Build and verify all microservices
- **Steps**:
  - Checkout repository
  - Setup Java 21 (Temurin distribution)
  - Build all microservices with Maven (`mvn clean verify`)
  - Maven cache is enabled for faster builds

**Status**: ✅ **ACTIVE - Can be tested immediately**

#### 2. **CD (Continuous Deployment)**
- **Runs on**: Push to `main` only (after CI passes)
- **Purpose**: Deploy to AWS EC2
- **Steps**:
  - SSH into EC2 instance
  - Pull latest code
  - Rebuild and restart Docker containers
  - Verify deployment

**Status**: ⚠️ **INACTIVE - Requires setup**

---

## Prerequisites for Deployment

### Required GitHub Secrets

Set these in: Repository Settings → Secrets and variables → Actions

| Secret Name | Description | Example |
|-------------|-------------|---------|
| `EC2_HOST` | AWS EC2 public IP or hostname | `54.123.456.789` |
| `EC2_USER` | SSH username | `ubuntu` |
| `EC2_SSH_KEY` | Private SSH key for EC2 access | Contents of your `.pem` file |

### Required on EC2 Server

1. **Docker & Docker Compose installed**
   ```bash
   sudo apt update
   sudo apt install docker.io docker-compose -y
   sudo systemctl start docker
   sudo systemctl enable docker
   ```

2. **Project directory structure**
   ```bash
   mkdir -p /home/ubuntu/fitness-microservice
   cd /home/ubuntu/fitness-microservice
   git clone https://github.com/d-anchu/fitness-microservice.git .
   ```

3. **Docker Compose file** (`docker-compose.yml`)
   - Not yet created in this repository
   - Required before deployment can work

4. **SSH access configured**
   - EC2 security group allows SSH (port 22)
   - SSH key pair configured

---

## Current Status

### ✅ What's Working Now

- **CI Job**: Fully functional
  - Builds all 6 microservices
  - Runs on every push and PR
  - Tests Maven build process
  - No external dependencies needed

### ⚠️ What Needs Setup for Deployment

- [ ] Create `docker-compose.yml` in repository root
- [ ] Setup AWS EC2 instance
- [ ] Configure GitHub secrets (EC2_HOST, EC2_USER, EC2_SSH_KEY)
- [ ] Install Docker on EC2
- [ ] Clone repository on EC2
- [ ] Test manual deployment first

---

## Testing the CI Job

The CI job is **ready to use immediately**. It will run automatically on your next push to `main`.

To test manually:
1. Go to: https://github.com/d-anchu/fitness-microservice/actions
2. Click "Fitness Microservices - CI/CD"
3. Click "Run workflow" → Select branch `main` → "Run workflow"

The build will:
- Use Java 21
- Build all microservices with Maven
- Report success/failure

---

## Disabling Deployment (Recommended for Now)

The deployment job is already configured to run **only if**:
- It's a push to `main` (not a PR)
- CI job passes
- GitHub secrets are configured

If secrets are not set, the deploy job will fail gracefully. The CI job will still succeed.

---

## Future Enhancements

- [ ] Add Docker build caching
- [ ] Add frontend build and deployment
- [ ] Add database migration step
- [ ] Add health check after deployment
- [ ] Add Slack/email notifications
- [ ] Add staging environment
- [ ] Add rollback mechanism

---

## Workflow File Location

```
fitness-microservice/
└── .github/
    └── workflows/
        ├── ci-cd.yml          ← Main workflow
        └── README.md          ← This file
```

---

## Support

For issues with the workflow:
1. Check the Actions tab for error logs
2. Verify Java 21 compatibility
3. Ensure all `pom.xml` files are valid
4. Check Maven dependencies

---

**Created**: November 2026  
**Status**: CI Active ✅ | CD Pending Setup ⚠️

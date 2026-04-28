# Complete Implementation Guide: S3P Payment Callback Integration

## Overview

This implementation provides a complete callback mechanism for S3P payment transactions with the following features:

✓ **Static Callback Server URL** - Generated from deployed server (not sent in payload)
✓ **2-Minute Callback Timeout** - Automatic fallback to /verifytx if no callback received
✓ **GitHub Actions Deployment** - Automatic deployment to AWS Elastic Beanstalk
✓ **Production-Ready** - Database persistence, logging, monitoring
✓ **Public Deployment** - Accessible from payment provider servers

## Architecture

```
Payment Flow:
    ┌─────────────────────────────────────────────────┐
    │  1. Client initiates payment                     │
    │     - Generates TRID (transaction ID)            │
    │     - Generates static callback URL              │
    └────────────────┬────────────────────────────────┘
                     │
    ┌────────────────▼────────────────────────────────┐
    │  2. S3P Payment Provider                         │
    │     - Processes payment                          │
    │     - Sends callback to static URL               │
    └────────────────┬────────────────────────────────┘
                     │
    ┌────────────────▼────────────────────────────────┐
    │  3. Callback Server (AWS EB)                     │
    │     - Receives callback                          │
    │     - Stores in database                         │
    │     - Returns 200 OK                             │
    └────────────────┬────────────────────────────────┘
                     │
    ┌────────────────▼────────────────────────────────┐
    │  4. Client waits for callback (2 minutes)        │
    │     - Polls callback server every 10 seconds     │
    │     - If received: Update status                 │
    │     - If timeout: Fall back to /verifytx         │
    └─────────────────────────────────────────────────┘
```

## Project Structure

```
s3pTest/
├── callback-server/                    # Spring Boot callback server
│   ├── src/main/java/com/maviance/s3p/callback/
│   │   ├── CallbackServerApplication.java
│   │   ├── controller/
│   │   │   └── CallbackController.java        # REST endpoints
│   │   ├── model/
│   │   │   └── TransactionCallback.java       # Entity
│   │   ├── repository/
│   │   │   └── CallbackRepository.java        # DB access
│   │   └── service/
│   │       └── CallbackService.java           # Business logic
│   ├── src/main/resources/
│   │   ├── application.properties             # Dev config (H2)
│   │   └── application-prod.properties        # Prod config (MySQL)
│   ├── .ebextensions/                         # AWS EB config
│   │   ├── 01-app.config                      # Application settings
│   │   ├── 02-healthcheck.config              # Health check
│   │   └── 03-logging.config                  # Logging setup
│   ├── pom.xml                                # Maven dependencies
│   ├── Dockerfile                             # Docker container
│   ├── DEPLOYMENT.md                          # Deployment guide
│   └── EB_DEPLOYMENT_GUIDE.md                 # AWS setup steps
├── src/
│   ├── CallbackClientExample.java             # Updated client example
│   ├── CashOutCollectionExample.java          # Original example
│   ├── VerifyWithCallbackExample.java         # Local callback example
│   └── ... (other examples)
├── .github/workflows/
│   └── deploy-callback-server.yml             # GitHub Actions workflow
└── README.md
```

## Setup Instructions

### 1. Deploy Callback Server to AWS Elastic Beanstalk

#### Prerequisites:
- AWS Account with appropriate permissions
- GitHub account for version control
- AWS CLI installed locally (optional, but recommended)

#### Steps:

**Step A: Create S3 Bucket for Deployments**
```bash
aws s3 mb s3://s3p-callback-server-deployments --region us-east-1
```

**Step B: Create RDS Database (Production)**
```bash
aws rds create-db-instance \
  --db-instance-identifier s3p-callback-db \
  --db-instance-class db.t3.micro \
  --engine mysql \
  --master-username admin \
  --master-user-password YourSecurePassword123! \
  --allocated-storage 20 \
  --publicly-accessible false \
  --region us-east-1
```

Note the endpoint (e.g., `s3p-callback-db.xxxxx.us-east-1.rds.amazonaws.com`)

**Step C: Create Elastic Beanstalk Application**
```bash
aws elasticbeanstalk create-application \
  --application-name s3p-callback-server \
  --description "S3P Payment Callback Server"
```

**Step D: Create EB Environment**
```bash
aws elasticbeanstalk create-environment \
  --application-name s3p-callback-server \
  --environment-name s3p-callback-server-prod \
  --solution-stack-name "64bit Amazon Linux 2 v5.6.1 running Tomcat 9 Corretto 11" \
  --instance-type t3.small \
  --option-settings \
    Namespace=aws:elasticbeanstalk:application:environment,OptionName=SPRING_PROFILES_ACTIVE,Value=prod \
    Namespace=aws:elasticbeanstalk:application:environment,OptionName=DB_HOST,Value=s3p-callback-db.xxxxx.us-east-1.rds.amazonaws.com \
    Namespace=aws:elasticbeanstalk:application:environment,OptionName=DB_PORT,Value=3306 \
    Namespace=aws:elasticbeanstalk:application:environment,OptionName=DB_NAME,Value=s3pcallback \
    Namespace=aws:elasticbeanstalk:application:environment,OptionName=DB_USER,Value=admin \
    Namespace=aws:elasticbeanstalk:application:environment,OptionName=DB_PASSWORD,Value=YourSecurePassword123!
```

### 2. Configure GitHub Secrets

Add the following secrets to your GitHub repository (Settings > Secrets and variables > Actions):

1. **AWS_ACCESS_KEY_ID** - Your AWS access key
2. **AWS_SECRET_ACCESS_KEY** - Your AWS secret access key  
3. **EB_BUCKET** - `s3p-callback-server-deployments`

### 3. Deploy via GitHub Actions

Push code to trigger automatic deployment:
```bash
git add .
git commit -m "Deploy callback server"
git push origin main
```

Or manually trigger from GitHub Actions tab.

### 4. Get Your Callback URL

After successful deployment:
```bash
aws elasticbeanstalk describe-environments \
  --application-name s3p-callback-server \
  --environment-names s3p-callback-server-prod \
  --query 'Environments[0].CNAME' \
  --output text
```

This returns: `s3p-callback-server-prod.elasticbeanstalk.com`

**Your callback URL is:**
```
https://s3p-callback-server-prod.elasticbeanstalk.com/api/v1/payment-callback
```

## Using the Callback Server

### Option 1: Environment Variable (Recommended)

```bash
export CALLBACK_HOST="https://s3p-callback-server-prod.elasticbeanstalk.com"
javac -cp ".:lib/smobilpay-s3p-java-client-1.0.0.jar" -d out src/CallbackClientExample.java
java -cp ".:lib/*:out" CallbackClientExample
```

### Option 2: Hardcoded in Code

Modify `CallbackClientExample.java`:
```java
String callbackHost = "https://s3p-callback-server-prod.elasticbeanstalk.com";
```

### Option 3: Local Development

Start the callback server locally:
```bash
cd callback-server
mvn spring-boot:run
```

Then use:
```bash
export CALLBACK_HOST="http://localhost:8080"
java CallbackClientExample
```

## API Endpoints

### Health Check
```bash
GET /api/v1/health
```
Response:
```json
{
  "status": "UP",
  "timestamp": "2024-04-28T10:00:00",
  "service": "S3P Callback Server"
}
```

### Receive Callback (Called by Payment Provider)
```bash
POST /api/v1/payment-callback
Content-Type: application/json

{
  "trid": "tx-1234567890",
  "status": "SUCCESS",
  "payment_status": "COMPLETE",
  "message": "Payment processed",
  "amount": 5000,
  "currency": "XAF"
}
```

### Retrieve Callback Status
```bash
GET /api/v1/callback/{trid}

Response:
{
  "id": 1,
  "trid": "tx-1234567890",
  "status": "SUCCESS",
  "paymentStatus": "COMPLETE",
  "message": "Payment processed",
  "amount": 5000,
  "currency": "XAF",
  "receivedAt": "2024-04-28T10:00:00",
  "createdAt": "2024-04-28T10:00:00",
  "updatedAt": "2024-04-28T10:00:00"
}
```

### List Recent Callbacks
```bash
GET /api/v1/callbacks/recent?limit=10
```

### List All Callbacks
```bash
GET /api/v1/callbacks
```

### Delete Callback
```bash
DELETE /api/v1/callback/{trid}
```

## Flow Diagram

### Transaction with Callback

```
Timeline:
├─ T+0s   : Client creates transaction with callback URL
│          : Payment provider receives request
│
├─ T+10s  : Payment provider processes payment
│          : Sends callback to server
│
├─ T+15s  : Callback server receives and stores callback
│          : Returns 200 OK to provider
│
├─ T+20s  : Client polls callback server
│          │ (every 10 seconds)
│          └─ Callback found! Update status
│
├─ T+120s : If no callback by this time:
│          │ Fall back to /verifytx endpoint
│          │ Query status directly from provider
│          └─ Get status
│
└─ Done   : Display final status to user
```

### Callback Timeout Handling

```
2-Minute Timeout Logic:

┌─ Start waiting (T+0s)
│  ├─ T+10s  : Check callback server (not found)
│  ├─ T+20s  : Check callback server (not found)
│  ├─ T+30s  : Check callback server (not found)
│  ├─ ...
│  ├─ T+110s : Check callback server (not found)
│  │
│  └─ T+120s : TIMEOUT!
│             └─ Execute fallback to /verifytx
│                └─ Get status from provider directly
│
└─ Return final status
```

## Monitoring

### View Logs

**AWS CloudWatch:**
```bash
aws elasticbeanstalk request-environment-info \
  --application-name s3p-callback-server \
  --environment-name s3p-callback-server-prod \
  --information-type tail

aws elasticbeanstalk retrieve-environment-info \
  --application-name s3p-callback-server \
  --environment-name s3p-callback-server-prod \
  --information-type tail \
  --query 'EnvironmentInfo[0].Message' \
  --output text | tail -50
```

**Local Development:**
```bash
# Application logs
tail -f /var/log/s3p-callback-server/application.log

# H2 Console (development only)
http://localhost:8080/h2-console
```

### Database Monitoring

**Check stored callbacks:**
```bash
# Query via EB environment
mysql -h s3p-callback-db.xxxxx.us-east-1.rds.amazonaws.com \
      -u admin -p s3pcallback

SELECT * FROM transaction_callbacks ORDER BY created_at DESC LIMIT 10;
```

**Check recent callbacks via API:**
```bash
curl https://s3p-callback-server-prod.elasticbeanstalk.com/api/v1/callbacks/recent
```

## Performance Tuning

### Database Connection Pool
- Current: 20 connections max, 5 minimum
- Adjust in `application-prod.properties`

### JVM Heap Size
- Current: 256MB - 512MB
- Modify in `.ebextensions/01-app.config`

### Instance Type
- Current: t3.small (1 vCPU, 2GB RAM)
- Scale up to t3.medium or t3.large for high traffic
- Or use auto-scaling with multiple instances

### Auto-Scaling Configuration
```bash
aws elasticbeanstalk create-configuration-template \
  --application-name s3p-callback-server \
  --template-name s3p-callback-scaling \
  --source-configuration EnvironmentName=s3p-callback-server-prod

# Add auto-scaling policy...
```

## Security Best Practices

1. **HTTPS**: AWS Elastic Beanstalk handles HTTPS with ACM certificates
2. **Database**: RDS encrypted at rest and in transit
3. **Network**: Security groups restrict access
4. **API Authentication**: Consider adding API key for callback endpoint
5. **Rate Limiting**: Add if needed for high traffic
6. **CORS**: Currently open; restrict in production if needed

## Troubleshooting

### Deployment Fails
```bash
# Check EB events
aws elasticbeanstalk describe-events \
  --application-name s3p-callback-server \
  --query 'Events[0:10]' \
  --output table
```

### Health Check Failing
```bash
# Test health endpoint
curl -v https://s3p-callback-server-prod.elasticbeanstalk.com/api/v1/health

# Check logs for errors
aws elasticbeanstalk request-environment-info \
  --application-name s3p-callback-server \
  --environment-name s3p-callback-server-prod \
  --information-type tail
```

### Database Connection Issues
```bash
# Verify RDS is running
aws rds describe-db-instances \
  --db-instance-identifier s3p-callback-db

# Check EB instance can reach RDS
# (Verify security groups allow MySQL port 3306)
```

## Maintenance

### Database Backup
```bash
# Enable automated backups (recommended)
aws rds modify-db-instance \
  --db-instance-identifier s3p-callback-db \
  --backup-retention-period 7
```

### Updates
- Framework updates: Update `pom.xml` dependencies
- Java updates: Modify EB solution stack
- Push changes and GitHub Actions redeploys automatically

### Scaling
```bash
# Increase instance type
aws elasticbeanstalk update-environment \
  --application-name s3p-callback-server \
  --environment-name s3p-callback-server-prod \
  --option-settings Namespace=aws:autoscaling:launchconfiguration,OptionName=InstanceType,Value=t3.medium
```

## Example: Complete Transaction Flow

```bash
# 1. Set callback server URL
export CALLBACK_HOST="https://s3p-callback-server-prod.elasticbeanstalk.com"

# 2. Compile and run
cd /Users/admin/Documents/s3pTest
javac -cp ".:lib/smobilpay-s3p-java-client-1.0.0.jar" -d out src/CallbackClientExample.java
java -cp ".:lib/*:out" CallbackClientExample

# Expected output:
# ═══════════════════════════════════════════════════════════
# S3P Payment with Callback Server Example
# ═══════════════════════════════════════════════════════════
# Callback Server: https://s3p-callback-server-prod.elasticbeanstalk.com
# Timeout: 2 minutes
# 
# Step 1: Generating Transaction ID
#   Transaction ID: tx-1234567890
#   Callback URL: https://s3p-callback-server-prod.elasticbeanstalk.com/api/v1/payment-callback
# 
# Step 2: Retrieving Cashout Packages
# ...
# 
# Step 5: Waiting for Callback Notification
#   Waiting up to 2 minutes for callback...
#   ✓ Callback received!
# 
# Step 6: Transaction Status
#   Status: SUCCESS
#   Amount: 5000 XAF
#   ✓ Transaction completed successfully!
```

## Next Steps

1. **Deploy callback server** - Follow AWS setup steps above
2. **Get your callback URL** - From EB CNAME
3. **Update client code** - Set CALLBACK_HOST environment variable
4. **Test locally** - Run with local server first
5. **Test in staging** - Deploy to staging environment
6. **Monitor in production** - Check CloudWatch logs and metrics
7. **Scale as needed** - Adjust instance type or enable auto-scaling

## Support

For issues or questions:
1. Check logs: `aws elasticbeanstalk describe-events`
2. Verify health: `curl /api/v1/health`
3. Test database: Connect to RDS and query `transaction_callbacks` table
4. Review GitHub Actions workflow: `.github/workflows/deploy-callback-server.yml`

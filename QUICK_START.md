# Quick Start Guide: S3P Callback Integration

## 5-Minute Setup

### Step 1: Deploy Callback Server

```bash
# Deploy to AWS Elastic Beanstalk
cd callback-server
git add .
git commit -m "Initial callback server"
git push origin main

# Wait for GitHub Actions to complete (check Actions tab)
```

### Step 2: Get Your URL

```bash
# After deployment succeeds
CALLBACK_URL="https://s3p-callback-server-prod.elasticbeanstalk.com"
echo "Callback URL: $CALLBACK_URL/api/v1/payment-callback"
```

### Step 3: Test Health

```bash
curl https://s3p-callback-server-prod.elasticbeanstalk.com/api/v1/health
```

Should return:
```json
{"status":"UP","timestamp":"2024-04-28T...","service":"S3P Callback Server"}
```

### Step 4: Run Transaction Example

```bash
cd src
export CALLBACK_HOST="https://s3p-callback-server-prod.elasticbeanstalk.com"
javac -cp ".:../lib/smobilpay-s3p-java-client-1.0.0.jar" -d ../out CallbackClientExample.java
java -cp ".:../lib/*:../out" CallbackClientExample
```

## How It Works

```
┌──────────────────────────────────────────────────────────┐
│  Your Application                                         │
├──────────────────────────────────────────────────────────┤
│  1. Initiates payment transaction                         │
│  2. Provides static callback URL to payment provider      │
│  3. Waits up to 2 minutes for callback                    │
│  4. Falls back to /verifytx if no callback               │
└────────────────┬─────────────────────────────────────────┘
                 │
    ┌────────────▼──────────────────────────┐
    │  Payment Provider (S3P)               │
    │  Processes payment, sends callback    │
    └────────────┬──────────────────────────┘
                 │ (HTTP POST)
    ┌────────────▼──────────────────────────┐
    │  Your Callback Server (AWS EB)        │
    │  http://your-url.elasticbeanstalk.com │
    │  Receives & stores callback           │
    └────────────┬──────────────────────────┘
                 │
    ┌────────────▼──────────────────────────┐
    │  Your Application                     │
    │  Retrieves status from callback server│
    └──────────────────────────────────────┘
```

## Environment Variables

```bash
# Set before running examples
export CALLBACK_HOST="https://your-callback-url.elasticbeanstalk.com"

# For local testing
export CALLBACK_HOST="http://localhost:8080"
```

## Files Created

### Core Callback Server
- `callback-server/src/main/java/` - Spring Boot application
- `callback-server/pom.xml` - Maven configuration
- `callback-server/.ebextensions/` - AWS Elastic Beanstalk config

### Client Examples  
- `src/CallbackClientExample.java` - Main example with 2-min timeout
- `src/CashOutWithCallbackExample.java` - Alternative example

### Documentation
- `CALLBACK_IMPLEMENTATION_GUIDE.md` - Complete implementation guide
- `callback-server/DEPLOYMENT.md` - Deployment procedures
- `callback-server/EB_DEPLOYMENT_GUIDE.md` - AWS setup details
- `callback-server/README.md` - Callback server docs

### GitHub Actions
- `.github/workflows/deploy-callback-server.yml` - Automatic deployment

## Common Tasks

### View Deployment Status
```bash
# GitHub Actions
# Go to: https://github.com/your-repo/actions

# AWS Console
aws elasticbeanstalk describe-environments \
  --application-name s3p-callback-server \
  --environment-names s3p-callback-server-prod
```

### Check Recent Callbacks
```bash
curl https://your-callback-url.elasticbeanstalk.com/api/v1/callbacks/recent?limit=5
```

### View Logs
```bash
# Tail production logs
aws elasticbeanstalk request-environment-info \
  --application-name s3p-callback-server \
  --environment-name s3p-callback-server-prod \
  --information-type tail

# Retrieve logs
aws elasticbeanstalk retrieve-environment-info \
  --application-name s3p-callback-server \
  --environment-name s3p-callback-server-prod \
  --information-type tail
```

### Local Development
```bash
cd callback-server
mvn spring-boot:run

# In another terminal
export CALLBACK_HOST="http://localhost:8080"
cd src
java CallbackClientExample
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| GitHub Actions fails | Check AWS credentials in secrets (Settings > Secrets) |
| Deployment timeout | Increase wait time in workflow or check AWS CloudFormation events |
| Health check fails | Verify security groups allow HTTP/HTTPS traffic |
| Callbacks not received | Check database connection and verify callback URL is correct |
| Local server won't start | Ensure port 8080 is free, check Java version (11+) |

## Key Timeouts & Intervals

- **Callback Timeout**: 2 minutes (configurable in client code)
- **Callback Check Interval**: 10 seconds (polls every 10s)
- **Health Check**: 30-second intervals
- **Connection Timeout**: 5 seconds

## Production Checklist

- ✓ Deploy callback server to AWS EB
- ✓ Set up RDS MySQL database
- ✓ Configure security groups
- ✓ Set environment variables
- ✓ Test health endpoint
- ✓ Monitor CloudWatch logs
- ✓ Set up alarms for failures
- ✓ Configure auto-scaling (optional)
- ✓ Enable database backups
- ✓ Set up HTTPS (automatic with AWS EB)

## Next Steps

1. **Deploy**: Push code to main branch
2. **Wait**: GitHub Actions completes deployment (5-10 minutes)
3. **Test**: Run CallbackClientExample
4. **Monitor**: Check CloudWatch logs and API health
5. **Integrate**: Update your transaction flows to use callback URL
6. **Scale**: Enable auto-scaling for production traffic

## Support Resources

- [Callback Implementation Guide](../CALLBACK_IMPLEMENTATION_GUIDE.md)
- [Callback Server README](./README.md)
- [EB Deployment Guide](./EB_DEPLOYMENT_GUIDE.md)
- [Deployment Guide](./DEPLOYMENT.md)

## Example Output

```
═══════════════════════════════════════════════════════════
S3P Payment with Callback Server Example
═══════════════════════════════════════════════════════════
Callback Server: https://s3p-callback-server-prod.elasticbeanstalk.com
Timeout: 2 minutes

✓ Callback server is reachable and healthy

Step 1: Generating Transaction ID
  Transaction ID: tx-1714300800123
  Callback URL: https://s3p-callback-server-prod.elasticbeanstalk.com/api/v1/payment-callback

Step 2: Retrieving Cashout Packages
  Selected Package: PAY_ITEM_ID_123

Step 3: Getting Quote
  Quote ID: QUOTE_456
  Amount: 5000 XAF

Step 4: Initiating Payment Collection
  Payment initiated. Collection Reference: REF_789

Step 5: Waiting for Callback Notification
  Waiting up to 2 minutes for callback...
  Waiting... (10s / 120s)
  Waiting... (20s / 120s)
  ✓ Callback received!

═══════════════════════════════════════════════════════════
Transaction Status
═══════════════════════════════════════════════════════════
Transaction ID: tx-1714300800123
Status: SUCCESS
Message: Payment processed successfully

✓ Transaction completed successfully!
═══════════════════════════════════════════════════════════
```

---

**Last Updated**: April 2024
**Status**: Production Ready

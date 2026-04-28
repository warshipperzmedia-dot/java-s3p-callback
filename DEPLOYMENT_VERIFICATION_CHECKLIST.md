# 🚀 Deployment Verification Checklist

Complete this checklist to ensure your callback system is properly deployed and functioning.

## ✅ Pre-Deployment Verification

- [ ] Git repository is initialized: `git status`
- [ ] All changes are committed: `git log` shows recent commits
- [ ] You're on the main branch: `git branch` shows `* main`
- [ ] AWS account has appropriate permissions
- [ ] GitHub repository is connected to AWS

## ✅ GitHub Setup Verification

- [ ] Repository secrets are configured:
  - [ ] `AWS_ACCESS_KEY_ID` - not empty
  - [ ] `AWS_SECRET_ACCESS_KEY` - not empty  
  - [ ] `EB_BUCKET` = `s3p-callback-server-deployments`
- [ ] `.github/workflows/deploy-callback-server.yml` exists
- [ ] Workflow file has correct branch triggers (main, deploy)

## ✅ Code Structure Verification

- [ ] `callback-server/pom.xml` exists
- [ ] `callback-server/src/main/java/com/maviance/s3p/callback/` directory exists
- [ ] `callback-server/.ebextensions/` has 3 config files:
  - [ ] `01-app.config`
  - [ ] `02-healthcheck.config`
  - [ ] `03-logging.config`
- [ ] `src/CallbackClientExample.java` exists
- [ ] `src/CashOutWithCallbackExample.java` exists
- [ ] All documentation files exist

## ✅ AWS Resource Creation Verification

Before deployment, verify AWS resources exist or create them:

### S3 Bucket
```bash
aws s3 ls | grep s3p-callback-server-deployments
```
- [ ] Bucket exists: `s3p-callback-server-deployments`

### RDS Database
```bash
aws rds describe-db-instances --db-instance-identifier s3p-callback-db
```
- [ ] Database exists: `s3p-callback-db`
- [ ] Engine: MySQL
- [ ] Endpoint recorded: `_________________________________`

### EB Application
```bash
aws elasticbeanstalk describe-applications --application-names s3p-callback-server
```
- [ ] Application exists: `s3p-callback-server`

### EB Environment
```bash
aws elasticbeanstalk describe-environments \
  --application-name s3p-callback-server \
  --environment-names s3p-callback-server-prod
```
- [ ] Environment exists: `s3p-callback-server-prod`
- [ ] Status: `Ready` or `Updating`
- [ ] CNAME recorded: `_________________________________`

## ✅ Deployment Execution Verification

### Trigger Deployment
```bash
git add .
git commit -m "Deploy S3P callback server"
git push origin main
```

- [ ] Push succeeds
- [ ] GitHub Actions workflow triggers automatically
- [ ] Check GitHub > Actions tab

### Monitor GitHub Actions

Watch the workflow: `Deploy Callback Server to AWS Elastic Beanstalk`

- [ ] **Step: Set up JDK 11** - ✓ Completes
- [ ] **Step: Build with Maven** - ✓ Completes (2-3 min)
- [ ] **Step: Configure AWS credentials** - ✓ Completes
- [ ] **Step: Create EB deployment package** - ✓ Completes
- [ ] **Step: Upload package to S3** - ✓ Completes
- [ ] **Step: Create EB application version** - ✓ Completes
- [ ] **Step: Update EB environment** - ✓ Completes (5-10 min)
- [ ] **Step: Wait for environment to update** - ✓ Completes
- [ ] **Step: Get deployment URL** - ✓ Completes
- [ ] **Step: Health check** - ✓ Completes
- [ ] **Step: Notify deployment success** - ✓ Completes

**Workflow should complete in: 5-15 minutes**

## ✅ Post-Deployment Verification

### Verify Deployment URL

```bash
aws elasticbeanstalk describe-environments \
  --application-name s3p-callback-server \
  --environment-names s3p-callback-server-prod \
  --query 'Environments[0].CNAME' \
  --output text
```

- [ ] Returns: `s3p-callback-server-prod.elasticbeanstalk.com` (or similar)
- [ ] URL recorded: `_________________________________`

### Test Health Endpoint

```bash
curl https://YOUR_CALLBACK_URL/api/v1/health
```

- [ ] Returns HTTP 200
- [ ] Response contains: `"status":"UP"`
- [ ] Response contains: `"service":"S3P Callback Server"`

### Check CloudWatch Logs

```bash
aws logs describe-log-streams \
  --log-group-name /aws/elasticbeanstalk/s3p-callback-server
```

- [ ] Log group exists
- [ ] Recent log entries (within last 5 minutes)
- [ ] No ERROR entries

### Verify EB Environment

```bash
aws elasticbeanstalk describe-environments \
  --application-name s3p-callback-server \
  --environment-names s3p-callback-server-prod
```

- [ ] Status: `Ready`
- [ ] Health: `Green`
- [ ] Instances: Running

## ✅ Client Configuration Verification

### Set Environment Variable

```bash
export CALLBACK_HOST="https://YOUR_CALLBACK_URL"
echo $CALLBACK_HOST
```

- [ ] URL is set correctly
- [ ] Starts with `https://`
- [ ] No trailing slash

### Compile Example

```bash
cd src
javac -cp ".:../lib/smobilpay-s3p-java-client-1.0.0.jar" -d ../out CallbackClientExample.java
```

- [ ] Compilation succeeds
- [ ] No errors or warnings
- [ ] `CallbackClientExample.class` created in `../out`

## ✅ Transaction Flow Verification

### Run Example

```bash
java -cp ".:../lib/*:../out" CallbackClientExample
```

Watch for output:

- [ ] **Callback server health check** - ✓ UP
- [ ] **Step 1: Generating Transaction ID** - ✓ Complete
- [ ] **Step 2: Retrieving Cashout Packages** - ✓ Complete
- [ ] **Step 3: Getting Quote** - ✓ Complete
- [ ] **Step 4: Initiating Payment** - ✓ Complete
- [ ] **Step 5: Waiting for Callback** - ✓ Starts
- [ ] **Callback received or timeout** - ✓ Either one
- [ ] **Final transaction status** - ✓ Displayed

### Callback Storage Verification

```bash
curl https://YOUR_CALLBACK_URL/api/v1/callbacks/recent
```

- [ ] Returns recent callbacks
- [ ] List contains your test transaction
- [ ] Status recorded correctly

## ✅ API Endpoints Verification

### Test Each Endpoint

**1. Health Check**
```bash
curl https://YOUR_CALLBACK_URL/api/v1/health
```
- [ ] ✓ Returns 200 OK

**2. Get Callback**
```bash
curl https://YOUR_CALLBACK_URL/api/v1/callback/TRID_FROM_ABOVE
```
- [ ] ✓ Returns 200 OK
- [ ] ✓ Callback data shown

**3. List Recent**
```bash
curl https://YOUR_CALLBACK_URL/api/v1/callbacks/recent
```
- [ ] ✓ Returns 200 OK
- [ ] ✓ Array of callbacks

**4. Send Test Callback**
```bash
curl -X POST https://YOUR_CALLBACK_URL/api/v1/payment-callback \
  -H "Content-Type: application/json" \
  -d '{"trid":"test-123","status":"SUCCESS"}'
```
- [ ] ✓ Returns 200 OK
- [ ] ✓ Response includes `"status":"success"`

## ✅ Monitoring Verification

### Check CloudWatch Logs

```bash
aws logs tail /aws/elasticbeanstalk/s3p-callback-server/var/log/web.stdout --follow --since 1h
```

- [ ] Application started successfully
- [ ] No ERROR lines
- [ ] Recent requests logged
- [ ] Callback received logged (if tested)

### Check Metrics

```bash
aws cloudwatch get-metric-statistics \
  --namespace AWS/ElasticBeanstalk \
  --metric-name EnvironmentHealth \
  --dimensions Name=EnvironmentName,Value=s3p-callback-server-prod \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Average
```

- [ ] Recent data points exist
- [ ] No warnings or errors

## ✅ Database Verification

### Connect to Database

```bash
mysql -h YOUR_RDS_ENDPOINT -u admin -p s3pcallback
```

- [ ] Connection successful
- [ ] Password entered: `YourSecurePassword123!`

### Check Table

```sql
SHOW TABLES;
DESC transaction_callbacks;
SELECT COUNT(*) FROM transaction_callbacks;
```

- [ ] [ ] Table `transaction_callbacks` exists
- [ ] [ ] Table has expected columns:
  - [ ] `id`, `trid`, `status`, `payment_status`
  - [ ] `message`, `amount`, `currency`, `payload`
  - [ ] `received_at`, `created_at`, `updated_at`

### Verify Data

```sql
SELECT * FROM transaction_callbacks ORDER BY created_at DESC LIMIT 5;
```

- [ ] Rows exist (if transactions were processed)
- [ ] Data looks correct
- [ ] Timestamps are recent

## ✅ Security Verification

- [ ] HTTPS is working: `curl -I https://YOUR_URL` returns 301/200
- [ ] SSL certificate is valid: No warnings in browser
- [ ] RDS security group allows only EB instances
- [ ] Credentials are environment variables (not in code)
- [ ] Logs don't contain sensitive data
- [ ] Database backups are enabled

## ✅ Performance Verification

### Response Time
```bash
time curl https://YOUR_CALLBACK_URL/api/v1/health
```

- [ ] Response time < 500ms
- [ ] Real time < 1 second

### Load Testing (Optional)
```bash
# Install: brew install ab (Apache Bench)
ab -n 100 -c 10 https://YOUR_CALLBACK_URL/api/v1/health
```

- [ ] All requests successful
- [ ] No failed requests
- [ ] Average response time reasonable

## ✅ Documentation Verification

All documentation files exist and are complete:

- [ ] `README.md` - Updated with callback info
- [ ] `QUICK_START.md` - 5-minute setup guide
- [ ] `IMPLEMENTATION_SUMMARY.md` - Complete overview
- [ ] `CALLBACK_IMPLEMENTATION_GUIDE.md` - Detailed guide
- [ ] `ENV_SETUP.md` - Configuration examples
- [ ] `callback-server/README.md` - Server docs
- [ ] `callback-server/DEPLOYMENT.md` - Deployment procedures
- [ ] `callback-server/EB_DEPLOYMENT_GUIDE.md` - AWS setup

## ✅ Cleanup & Best Practices

- [ ] Sensitive data not committed to Git
- [ ] AWS credentials in GitHub Secrets (not in code)
- [ ] RDS password is strong (minimum 12 characters)
- [ ] EB environment has auto-scaling configured
- [ ] CloudWatch logs retention set appropriately
- [ ] Database backups enabled (7+ day retention)
- [ ] SSL certificate auto-renewal configured

## ✅ Final Sign-Off

**Deployment Date**: _________________________

**Deployed By**: _________________________

**Verified By**: _________________________

**Callback URL**: https://_________________________________

**Status**: ✅ **PRODUCTION READY**

---

## 📝 Notes & Observations

```
_________________________________________________________________
_________________________________________________________________
_________________________________________________________________
_________________________________________________________________
```

## 🆘 If Anything Fails

1. Check the **GitHub Actions** workflow for error messages
2. Review **CloudWatch Logs** for application errors
3. Check **EB Events** for infrastructure issues:
   ```bash
   aws elasticbeanstalk describe-events \
     --application-name s3p-callback-server \
     --query 'Events[0:20]'
   ```
4. Verify **AWS Resources** exist and are configured correctly
5. Test **Database Connection** manually
6. Consult **CALLBACK_IMPLEMENTATION_GUIDE.md** troubleshooting section

## 📞 Support

- **Deployment Issues**: Check GitHub Actions workflow logs
- **AWS Issues**: Check EB events and CloudWatch logs
- **Connection Issues**: Verify security groups and database settings
- **Code Issues**: Check application logs in CloudWatch
- **Documentation**: See CALLBACK_IMPLEMENTATION_GUIDE.md

---

**Checklist Version**: 1.0  
**Last Updated**: April 2024  
**Status**: Ready for Use ✅

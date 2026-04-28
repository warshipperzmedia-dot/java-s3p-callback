# S3P Callback Server - Environment Setup

This file contains configuration examples for different deployment scenarios.

## Local Development

File: `callback-server/src/main/resources/application.properties`

```properties
spring.application.name=s3p-callback-server
server.port=8080
spring.jpa.hibernate.ddl-auto=create-drop
spring.h2.console.enabled=true
logging.level.com.maviance.s3p.callback=DEBUG
```

**Start server:**
```bash
mvn spring-boot:run
```

**Access:**
- Application: http://localhost:8080
- H2 Console: http://localhost:8080/h2-console
- Health: http://localhost:8080/api/v1/health

---

## Docker Development

Build and run:
```bash
docker build -t s3p-callback:latest .
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  s3p-callback:latest
```

---

## AWS Elastic Beanstalk Production

### Create S3 Bucket

```bash
aws s3 mb s3://s3p-callback-deployments --region us-east-1
```

### Create RDS Database

```bash
aws rds create-db-instance \
  --db-instance-identifier s3p-callback-db \
  --db-instance-class db.t3.micro \
  --engine mysql \
  --master-username admin \
  --master-user-password "YourSecurePassword123!" \
  --allocated-storage 20 \
  --publicly-accessible false \
  --db-name s3pcallback \
  --region us-east-1
```

**Get endpoint:**
```bash
aws rds describe-db-instances \
  --db-instance-identifier s3p-callback-db \
  --query 'DBInstances[0].Endpoint.Address' \
  --output text
```

### Create EB Application

```bash
aws elasticbeanstalk create-application \
  --application-name s3p-callback-server \
  --description "S3P Payment Callback Server"
```

### Create EB Environment

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
    Namespace=aws:elasticbeanstalk:application:environment,OptionName=DB_PASSWORD,Value="YourSecurePassword123!" \
    Namespace=aws:autoscaling:trigger,OptionName=MeasureName,Value=CPUUtilization \
    Namespace=aws:autoscaling:trigger,OptionName=Statistic,Value=Average \
    Namespace=aws:autoscaling:trigger,OptionName=Unit,Value=Percent \
    Namespace=aws:autoscaling:trigger,OptionName=UpperThreshold,Value=80 \
    Namespace=aws:autoscaling:trigger,OptionName=LowerThreshold,Value=20
```

### GitHub Secrets

Add to GitHub repository settings:

```
AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE
AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
EB_BUCKET=s3p-callback-deployments
```

### Deploy via GitHub Actions

```bash
git add .
git commit -m "Deploy callback server to AWS EB"
git push origin main
```

**Get deployment URL:**
```bash
aws elasticbeanstalk describe-environments \
  --application-name s3p-callback-server \
  --environment-names s3p-callback-server-prod \
  --query 'Environments[0].CNAME' \
  --output text
```

Returns: `s3p-callback-server-prod.elasticbeanstalk.com`

---

## Environment Variables Reference

### Development (application.properties)

```properties
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8080
spring.jpa.hibernate.ddl-auto=create-drop
spring.h2.console.enabled=true
```

### Production (application-prod.properties)

```properties
# Activated by GitHub Actions and EB deployment
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=5000

# Database Configuration (via environment variables)
DB_HOST=s3p-callback-db.xxxxx.us-east-1.rds.amazonaws.com
DB_PORT=3306
DB_NAME=s3pcallback
DB_USER=admin
DB_PASSWORD=YourSecurePassword123!

# Logging
LOGGING_FILE_NAME=/var/log/s3p-callback-server/application.log
LOGGING_FILE_MAX_SIZE=10MB
LOGGING_FILE_MAX_HISTORY=10
```

---

## Monitoring Configuration

### CloudWatch Logs

**Enable in EB:**
```bash
aws elasticbeanstalk update-environment \
  --application-name s3p-callback-server \
  --environment-name s3p-callback-server-prod \
  --option-settings \
    Namespace=aws:elasticbeanstalk:cloudwatch:logs,OptionName=StreamLogs,Value=true \
    Namespace=aws:elasticbeanstalk:cloudwatch:logs,OptionName=DeleteOnTerminate,Value=false \
    Namespace=aws:elasticbeanstalk:cloudwatch:logs,OptionName=RetentionInDays,Value=30
```

**View logs:**
```bash
aws logs tail /aws/elasticbeanstalk/s3p-callback-server/var/log/web.stdout --follow
```

### Health Checks

**Current configuration:**
- Endpoint: `/api/v1/health`
- Interval: 30 seconds
- Timeout: 5 seconds
- Healthy Threshold: 3
- Unhealthy Threshold: 5

---

## Auto-Scaling Configuration

### Enable Auto-Scaling

```bash
aws autoscaling create-launch-configuration \
  --launch-configuration-name s3p-callback-lc \
  --image-id ami-12345678 \
  --instance-type t3.small

aws autoscaling create-auto-scaling-group \
  --auto-scaling-group-name s3p-callback-asg \
  --launch-configuration-name s3p-callback-lc \
  --min-size 2 \
  --max-size 10 \
  --desired-capacity 2
```

### Scaling Policies

**Scale Up:**
```bash
aws autoscaling put-scaling-policy \
  --auto-scaling-group-name s3p-callback-asg \
  --policy-name scale-up \
  --policy-type TargetTrackingScaling \
  --target-tracking-configuration 'TargetValue=80,PredefinedMetricSpecification={PredefinedMetricType=ASGAverageCPUUtilization}'
```

**Scale Down:**
```bash
aws autoscaling put-scaling-policy \
  --auto-scaling-group-name s3p-callback-asg \
  --policy-name scale-down \
  --policy-type TargetTrackingScaling \
  --target-tracking-configuration 'TargetValue=20,PredefinedMetricSpecification={PredefinedMetricType=ASGAverageCPUUtilization}'
```

---

## Database Configuration

### RDS MySQL Parameters

```ini
[client]
user=admin
password=YourSecurePassword123!
host=s3p-callback-db.xxxxx.us-east-1.rds.amazonaws.com

[mysql]
database=s3pcallback
```

### Connection Pool Settings

`application-prod.properties`:
```properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=20000
spring.datasource.hikari.idle-timeout=300000
spring.datasource.hikari.max-lifetime=1200000
```

---

## Backup & Recovery

### Enable RDS Backups

```bash
aws rds modify-db-instance \
  --db-instance-identifier s3p-callback-db \
  --backup-retention-period 7 \
  --preferred-backup-window "03:00-04:00" \
  --preferred-maintenance-window "mon:04:00-mon:05:00"
```

### Create Manual Backup

```bash
aws rds create-db-snapshot \
  --db-instance-identifier s3p-callback-db \
  --db-snapshot-identifier s3p-callback-backup-$(date +%Y%m%d-%H%M%S)
```

### Restore from Backup

```bash
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier s3p-callback-db-restored \
  --db-snapshot-identifier s3p-callback-backup-20240428-120000
```

---

## Performance Tuning

### JVM Settings

`.ebextensions/01-app.config`:
```yaml
option_settings:
  aws:elasticbeanstalk:container:tomcat:jvm:
    JVM_ARGS: "-Xms512m -Xmx1024m -XX:+UseG1GC"
```

### Database Connection Pool

`application-prod.properties`:
```properties
# Increase for high concurrency
spring.datasource.hikari.maximum-pool-size=30
spring.datasource.hikari.minimum-idle=10
```

### Instance Type Scaling

```bash
# Change from t3.small to t3.medium
aws elasticbeanstalk update-environment \
  --application-name s3p-callback-server \
  --environment-name s3p-callback-server-prod \
  --option-settings \
    Namespace=aws:autoscaling:launchconfiguration,OptionName=InstanceType,Value=t3.medium
```

---

## Security Settings

### HTTPS Configuration

Automatic via AWS Load Balancer (no additional config needed)

### Security Groups

Allow inbound traffic:
- HTTP (80) - from 0.0.0.0/0
- HTTPS (443) - from 0.0.0.0/0
- Port 8080 - for internal communication

Allow outbound to RDS:
- Port 3306 - to RDS security group

### Environment Variable Encryption

Use AWS Secrets Manager (optional):
```bash
aws secretsmanager create-secret \
  --name s3p-callback-db-password \
  --secret-string "YourSecurePassword123!"
```

---

## Cleanup

### Delete Resources

```bash
# Delete EB environment
aws elasticbeanstalk terminate-environment \
  --application-name s3p-callback-server \
  --environment-name s3p-callback-server-prod

# Delete EB application
aws elasticbeanstalk delete-application \
  --application-name s3p-callback-server

# Delete RDS database (skip final snapshot)
aws rds delete-db-instance \
  --db-instance-identifier s3p-callback-db \
  --skip-final-snapshot

# Delete S3 bucket
aws s3 rb s3://s3p-callback-deployments --force
```

---

## Troubleshooting

### Check Environment Status

```bash
aws elasticbeanstalk describe-environments \
  --application-name s3p-callback-server \
  --environment-names s3p-callback-server-prod
```

### View Recent Events

```bash
aws elasticbeanstalk describe-events \
  --application-name s3p-callback-server \
  --query 'Events[0:20]' \
  --output table
```

### SSH into Instance

```bash
# Get instance ID
INSTANCE_ID=$(aws elasticbeanstalk describe-environment-resources \
  --application-name s3p-callback-server \
  --environment-name s3p-callback-server-prod \
  --query 'EnvironmentResources.Instances[0].Id' \
  --output text)

# SSH into instance
ssh -i s3p-callback-key.pem ec2-user@$INSTANCE_ID
```

---

**Last Updated**: April 2024
**Configuration Version**: 1.0.0

# Elastic Beanstalk Deployment Configuration

This file contains the setup instructions for deploying the S3P Callback Server to AWS Elastic Beanstalk.

## Prerequisites

1. **AWS Account** - You must have an active AWS account
2. **AWS CLI** - Install and configure AWS CLI with your credentials
3. **GitHub Account** - Repository access for GitHub Actions
4. **IAM Permissions** - Ensure your AWS user has permissions for:
   - Elastic Beanstalk
   - EC2
   - RDS (for database)
   - S3
   - CloudWatch

## Step 1: Create S3 Bucket for EB Deployments

```bash
aws s3 mb s3://s3p-callback-server-deployments --region us-east-1
```

## Step 2: Create RDS Database (Optional but Recommended)

For production, use an RDS MySQL database instead of local H2:

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

## Step 3: Create IAM Role for EC2 Instances

```bash
# Create the role
aws iam create-role \
  --role-name aws-elasticbeanstalk-ec2-role-s3p \
  --assume-role-policy-document file://trust-policy.json

# Attach the policy
aws iam attach-role-policy \
  --role-name aws-elasticbeanstalk-ec2-role-s3p \
  --policy-arn arn:aws:iam::aws:policy/ElasticBeanstalkMulticontainerDocker

aws iam attach-role-policy \
  --role-name aws-elasticbeanstalk-ec2-role-s3p \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2FullAccess
```

## Step 4: Create Elastic Beanstalk Application

```bash
aws elasticbeanstalk create-application \
  --application-name s3p-callback-server \
  --description "S3P Payment Callback Server"
```

## Step 5: Create Elastic Beanstalk Environment

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
    Namespace=aws:elasticbeanstalk:application:environment,OptionName=DB_PASSWORD,Value=YourSecurePassword123! \
    Namespace=aws:elasticbeanstalk:instances:stateful,OptionName=EnableSpot,Value=true
```

## Step 6: Add GitHub Secrets

Add the following secrets to your GitHub repository:

1. **AWS_ACCESS_KEY_ID** - Your AWS access key
2. **AWS_SECRET_ACCESS_KEY** - Your AWS secret key
3. **EB_BUCKET** - Name of your S3 bucket (e.g., `s3p-callback-server-deployments`)

Navigate to: **Settings > Secrets and variables > Actions > New repository secret**

## Step 7: Deploy Using GitHub Actions

Push changes to the `main` or `deploy` branch to trigger automatic deployment:

```bash
git add .
git commit -m "Deploy callback server"
git push origin main
```

Or manually trigger the workflow:

1. Go to **Actions** tab in GitHub
2. Select **Deploy Callback Server to AWS Elastic Beanstalk**
3. Click **Run workflow**

## Step 8: Access Your Callback Server

After successful deployment, you'll get the public URL:

```
http://s3p-callback-server-prod.elasticbeanstalk.com
```

Use this URL as your **CALLBACK_HOST** in your transaction examples:

```
http://s3p-callback-server-prod.elasticbeanstalk.com:8080/api/v1/payment-callback
```

## Environment Variables

Configure these environment variables in your EB environment:

- **SPRING_PROFILES_ACTIVE**: `prod` (for production configuration)
- **DB_HOST**: RDS database hostname
- **DB_PORT**: `3306`
- **DB_NAME**: `s3pcallback`
- **DB_USER**: Database username
- **DB_PASSWORD**: Database password

## Monitoring

### View Logs

```bash
# Stream recent logs
aws elasticbeanstalk request-environment-info \
  --application-name s3p-callback-server \
  --environment-name s3p-callback-server-prod \
  --information-type tail

# Get log location
aws elasticbeanstalk retrieve-environment-info \
  --application-name s3p-callback-server \
  --environment-name s3p-callback-server-prod \
  --information-type tail
```

### Check Environment Status

```bash
aws elasticbeanstalk describe-environments \
  --application-name s3p-callback-server \
  --environment-names s3p-callback-server-prod
```

## Health Check

```bash
curl https://your-callback-url.elasticbeanstalk.com/api/v1/health
```

Expected response:
```json
{
  "status": "UP",
  "timestamp": "2024-04-28T10:00:00",
  "service": "S3P Callback Server"
}
```

## Cleanup

To delete resources:

```bash
# Terminate EB environment
aws elasticbeanstalk terminate-environment \
  --application-name s3p-callback-server \
  --environment-name s3p-callback-server-prod

# Delete EB application
aws elasticbeanstalk delete-application \
  --application-name s3p-callback-server

# Delete RDS database
aws rds delete-db-instance \
  --db-instance-identifier s3p-callback-db \
  --skip-final-snapshot

# Delete S3 bucket
aws s3 rb s3://s3p-callback-server-deployments --force
```

## Troubleshooting

### Deployment fails with "No EC2 key pair"

```bash
# Create a key pair
aws ec2 create-key-pair \
  --key-name s3p-callback-key \
  --region us-east-1 \
  --query 'KeyMaterial' \
  --output text > s3p-callback-key.pem

chmod 400 s3p-callback-key.pem
```

### Environment not becoming "Ready"

Check logs:
```bash
aws elasticbeanstalk describe-events \
  --application-name s3p-callback-server \
  --environment-name s3p-callback-server-prod \
  --query 'Events[0:10]'
```

### Database connection issues

Verify security group allows MySQL (port 3306):
```bash
aws ec2 describe-security-groups \
  --filters Name=group-name,Values=default
```

Allow EB instance to access RDS by adding inbound rule to RDS security group.

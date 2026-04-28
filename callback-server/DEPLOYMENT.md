# S3P Callback Server Deployment Configuration

## Quick Start

### 1. Local Development

```bash
cd callback-server
mvn clean install
mvn spring-boot:run
```

The server will start at `http://localhost:8080`

### 2. Build JAR

```bash
mvn clean package
java -jar target/s3p-callback-server-1.0.0.jar
```

### 3. Docker Build (Optional)

```bash
docker build -t s3p-callback-server:latest .
docker run -p 8080:8080 s3p-callback-server:latest
```

## API Endpoints

### Health Check
```bash
GET /api/v1/health
```

### Receive Callback
```bash
POST /api/v1/payment-callback
Content-Type: application/json

{
  "trid": "tx-123456",
  "status": "SUCCESS",
  "payment_status": "COMPLETE",
  "message": "Payment processed successfully",
  "amount": 5000,
  "currency": "XAF"
}
```

### Get Callback by TRID
```bash
GET /api/v1/callback/{trid}
```

### List All Callbacks
```bash
GET /api/v1/callbacks
```

### Get Recent Callbacks
```bash
GET /api/v1/callbacks/recent?limit=10
```

### Delete Callback
```bash
DELETE /api/v1/callback/{trid}
```

## Database Configuration

### Local (H2)
- Auto-configured in `application.properties`
- Data stored in-memory
- H2 Console: `http://localhost:8080/h2-console`

### Production (MySQL)
- Configured via environment variables in `application-prod.properties`
- Connection pooling optimized with HikariCP

## AWS Elastic Beanstalk Setup

See [EB_DEPLOYMENT_GUIDE.md](EB_DEPLOYMENT_GUIDE.md) for detailed AWS setup instructions.

## Environment Variables

When deploying to AWS, set these environment variables:

```
SPRING_PROFILES_ACTIVE=prod
DB_HOST=your-rds-endpoint.rds.amazonaws.com
DB_PORT=3306
DB_NAME=s3pcallback
DB_USER=admin
DB_PASSWORD=your-secure-password
```

## Logging

Logs are output to:
- Console: `INFO` level minimum
- File: `/var/log/s3p-callback-server/application.log` (production only)

## Security Considerations

1. **HTTPS**: Use AWS Elastic Beanstalk's HTTPS certificate for production
2. **Database**: Use RDS with encryption at rest and in transit
3. **Network**: Use VPC with security groups restricting access
4. **Secrets**: Store database credentials in AWS Secrets Manager
5. **API**: Consider adding API key authentication for callback endpoint

## Performance Tuning

- Connection pool size: 20 (configurable)
- JVM heap: 256MB - 512MB (configurable in `.ebextensions/01-app.config`)
- Auto-scaling: Configure minimum/maximum instances in EB console

## Monitoring

- CloudWatch: Application logs and metrics
- Health checks: `/api/v1/health` endpoint monitored by EB
- Alarms: Configure in AWS CloudWatch for high error rates

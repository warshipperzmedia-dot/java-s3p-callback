# S3P Callback Server

Callback server project files.

## Configuration

### Development (application.properties)
- Database: H2 in-memory
- Port: 8080
- H2 Console: http://localhost:8080/h2-console

### Production (application-prod.properties)
- Database: MySQL (configured via environment variables)
- Port: 5000
- Logging: File + Console
- Profile: Set `SPRING_PROFILES_ACTIVE=prod`

### Environment Variables

**Production only:**
```
SPRING_PROFILES_ACTIVE=prod
DB_HOST=your-rds-endpoint
DB_PORT=3306
DB_NAME=s3pcallback
DB_USER=admin
DB_PASSWORD=your-secure-password
```

## Deployment

### AWS Elastic Beanstalk

See [EB_DEPLOYMENT_GUIDE.md](EB_DEPLOYMENT_GUIDE.md) for detailed setup.

Quick deploy:
```bash
git add .
git commit -m "Deploy callback server"
git push origin main
```

GitHub Actions will automatically build and deploy.

### Manual Deployment

```bash
# Build
mvn clean package

# Deploy to EB
eb deploy
```

## Database Schema

### transaction_callbacks table
```sql
CREATE TABLE transaction_callbacks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trid VARCHAR(100) UNIQUE NOT NULL,
    status VARCHAR(50),
    payment_status VARCHAR(50),
    message VARCHAR(500),
    amount DECIMAL(12,2),
    currency VARCHAR(10),
    payload LONGTEXT,
    received_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_trid ON transaction_callbacks(trid);
CREATE INDEX idx_status ON transaction_callbacks(status);
CREATE INDEX idx_created_at ON transaction_callbacks(created_at DESC);
```

## Monitoring

### Logs
```bash
# CloudWatch (production)
aws logs tail /aws/elasticbeanstalk/s3p-callback-server/var/log/web.stdout

# Local
tail -f /var/log/s3p-callback-server/application.log
```

### Health
```bash
curl http://localhost:8080/api/v1/health
```

### Database Queries
```bash
# Recent callbacks
mysql -h localhost -u admin -p s3pcallback
SELECT * FROM transaction_callbacks ORDER BY created_at DESC LIMIT 10;
```

## Development

### Project Structure
```
callback-server/
├── pom.xml
├── Dockerfile
├── .ebextensions/           # AWS EB configuration
├── src/
│   ├── main/java/com/maviance/s3p/callback/
│   │   ├── CallbackServerApplication.java
│   │   ├── controller/
│   │   ├── model/
│   │   ├── repository/
│   │   └── service/
│   └── main/resources/
│       ├── application.properties
│       └── application-prod.properties
└── README.md
```

### Building
```bash
mvn clean install
mvn test
mvn package -DskipTests
```

### Testing
```bash
# Run tests
mvn test

# Integration testing
curl -X POST http://localhost:8080/api/v1/payment-callback \
  -H "Content-Type: application/json" \
  -d '{
    "trid": "test-123",
    "status": "SUCCESS",
    "payment_status": "COMPLETE",
    "amount": 1000,
    "currency": "XAF"
  }'

curl http://localhost:8080/api/v1/callback/test-123
```

## Performance

- Connection pool: 20 max (configurable)
- JVM heap: 256MB-512MB
- Instance: t3.small (configurable)
- Response time: <100ms typical

## Security

- HTTPS: Enabled via AWS Load Balancer
- Database: Encrypted at rest
- Credentials: Environment variables (not in code)
- API: RESTful with standard HTTP semantics
- CORS: Open (configure as needed)

## Troubleshooting

### Server won't start
- Check Java version: `java -version` (must be 11+)
- Check port 8080 availability
- Check logs for errors

### Database errors
- Verify MySQL is running (production)
- Check connection string in environment variables
- Verify firewall/security group allows connection

### Callbacks not being stored
- Check API is receiving POST requests
- Verify `trid` field is in payload
- Check database logs for errors
- Use H2 Console (dev) to inspect tables

## License

(Specify your license)

## Support

For issues, see the main project's CALLBACK_IMPLEMENTATION_GUIDE.md

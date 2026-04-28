FROM openjdk:11-jre-slim

WORKDIR /app

# Copy the built JAR file
COPY target/s3p-callback-server-1.0.0.jar application.jar

# Copy EB extensions
COPY .ebextensions /app/.ebextensions

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/api/v1/health || exit 1

# Set environment
ENV SPRING_PROFILES_ACTIVE=prod

# Run the application
ENTRYPOINT ["java", "-jar", "application.jar"]

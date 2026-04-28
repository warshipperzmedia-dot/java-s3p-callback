package com.maviance.s3p.callback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application for handling payment transaction callbacks.
 * 
 * This server receives transaction status updates from the S3P payment provider
 * and stores them for later verification.
 */
@SpringBootApplication
public class CallbackServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CallbackServerApplication.class, args);
    }
}

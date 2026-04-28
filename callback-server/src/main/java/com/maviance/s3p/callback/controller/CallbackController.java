package com.maviance.s3p.callback.controller;

import com.maviance.s3p.callback.model.TransactionCallback;
import com.maviance.s3p.callback.service.CallbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for handling payment transaction callbacks.
 * 
 * Receives callback notifications from the S3P payment provider and stores them.
 */
@RestController
@RequestMapping("/api/v1")
public class CallbackController {

    private static final Logger logger = LoggerFactory.getLogger(CallbackController.class);

    @Autowired
    private CallbackService callbackService;

    /**
     * Health check endpoint
     * 
     * @return Status of the callback server
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("service", "S3P Callback Server");
        return ResponseEntity.ok(response);
    }

    /**
     * Payment callback endpoint
     * 
     * Receives transaction status updates from the payment provider and stores them.
     * Expected payload format (JSON):
     * {
     *   "trid": "transaction-id",
     *   "status": "SUCCESS|PENDING|FAILED",
     *   "payment_status": "COMPLETE|PENDING|FAILED",
     *   "message": "Status message",
     *   "timestamp": "2024-01-01T00:00:00",
     *   "amount": 5000,
     *   "currency": "XAF",
     *   ...other fields
     * }
     * 
     * @param payload The callback payload from the payment provider
     * @return Acknowledgment response
     */
    @PostMapping("/payment-callback")
    public ResponseEntity<Map<String, String>> handlePaymentCallback(@RequestBody Map<String, Object> payload) {
        try {
            logger.info("Received callback: {}", payload);

            TransactionCallback callback = callbackService.processCallback(payload);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Callback received and stored");
            response.put("callbackId", callback.getId().toString());
            response.put("trid", callback.getTrid());

            logger.info("Callback processed successfully. TRID: {}", callback.getTrid());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error processing callback", e);
            
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Failed to process callback: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Retrieve callback status by transaction ID
     * 
     * @param trid Transaction ID
     * @return Transaction callback details
     */
    @GetMapping("/callback/{trid}")
    public ResponseEntity<TransactionCallback> getCallbackByTrid(@PathVariable String trid) {
        TransactionCallback callback = callbackService.getCallbackByTrid(trid);
        
        if (callback == null) {
            logger.warn("Callback not found for TRID: {}", trid);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(callback);
    }

    /**
     * List all received callbacks
     * 
     * @return List of all transaction callbacks
     */
    @GetMapping("/callbacks")
    public ResponseEntity<List<TransactionCallback>> getAllCallbacks() {
        List<TransactionCallback> callbacks = callbackService.getAllCallbacks();
        return ResponseEntity.ok(callbacks);
    }

    /**
     * Retrieve recent callbacks (last N records)
     * 
     * @param limit Number of recent records to retrieve
     * @return List of recent transaction callbacks
     */
    @GetMapping("/callbacks/recent")
    public ResponseEntity<List<TransactionCallback>> getRecentCallbacks(
            @RequestParam(defaultValue = "10") int limit) {
        List<TransactionCallback> callbacks = callbackService.getRecentCallbacks(limit);
        return ResponseEntity.ok(callbacks);
    }

    /**
     * Delete callback (for cleanup/testing)
     * 
     * @param trid Transaction ID
     * @return Deletion confirmation
     */
    @DeleteMapping("/callback/{trid}")
    public ResponseEntity<Map<String, String>> deleteCallback(@PathVariable String trid) {
        callbackService.deleteCallbackByTrid(trid);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Callback deleted");
        response.put("trid", trid);

        return ResponseEntity.ok(response);
    }
}

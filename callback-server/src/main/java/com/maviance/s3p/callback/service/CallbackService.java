package com.maviance.s3p.callback.service;

import com.maviance.s3p.callback.model.TransactionCallback;
import com.maviance.s3p.callback.repository.CallbackRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service for processing and managing transaction callbacks.
 */
@Service
public class CallbackService {

    private static final Logger logger = LoggerFactory.getLogger(CallbackService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CallbackRepository callbackRepository;

    /**
     * Process an incoming callback payload and store it
     * 
     * @param payload The callback payload from the payment provider
     * @return The stored TransactionCallback entity
     * @throws Exception if processing fails
     */
    public TransactionCallback processCallback(Map<String, Object> payload) throws Exception {
        Objects.requireNonNull(payload, "Payload cannot be null");

        // Extract key fields from payload
        String trid = (String) payload.getOrDefault("trid", "");
        String status = (String) payload.getOrDefault("status", "");
        String paymentStatus = (String) payload.getOrDefault("payment_status", status);
        String message = (String) payload.getOrDefault("message", "");
        
        // Parse amount if present
        BigDecimal amount = null;
        Object amountObj = payload.get("amount");
        if (amountObj != null) {
            if (amountObj instanceof Number) {
                amount = new BigDecimal(amountObj.toString());
            } else if (amountObj instanceof String) {
                try {
                    amount = new BigDecimal((String) amountObj);
                } catch (NumberFormatException e) {
                    logger.warn("Failed to parse amount: {}", amountObj);
                }
            }
        }

        String currency = (String) payload.getOrDefault("currency", "XAF");
        String payloadJson = objectMapper.writeValueAsString(payload);

        if (trid.isEmpty()) {
            throw new IllegalArgumentException("Transaction ID (trid) is required in callback payload");
        }

        // Check if callback already exists
        TransactionCallback existing = callbackRepository.findByTrid(trid).orElse(null);
        if (existing != null) {
            logger.warn("Callback for TRID {} already exists. Updating...", trid);
            existing.setStatus(status);
            existing.setPaymentStatus(paymentStatus);
            existing.setMessage(message);
            existing.setAmount(amount);
            existing.setCurrency(currency);
            existing.setPayload(payloadJson);
            existing.setReceivedAt(LocalDateTime.now());
            return callbackRepository.save(existing);
        }

        // Create new callback entity
        TransactionCallback callback = new TransactionCallback();
        callback.setTrid(trid);
        callback.setStatus(status);
        callback.setPaymentStatus(paymentStatus);
        callback.setMessage(message);
        callback.setAmount(amount);
        callback.setCurrency(currency);
        callback.setPayload(payloadJson);

        TransactionCallback saved = callbackRepository.save(callback);
        logger.info("Callback saved successfully. TRID: {}, Status: {}", trid, status);

        return saved;
    }

    /**
     * Retrieve a callback by transaction ID
     * 
     * @param trid Transaction ID
     * @return TransactionCallback if found, null otherwise
     */
    public TransactionCallback getCallbackByTrid(String trid) {
        return callbackRepository.findByTrid(trid).orElse(null);
    }

    /**
     * Get all stored callbacks
     * 
     * @return List of all callbacks
     */
    public List<TransactionCallback> getAllCallbacks() {
        return callbackRepository.findAll();
    }

    /**
     * Get recent callbacks
     * 
     * @param limit Number of recent records to retrieve
     * @return List of recent callbacks
     */
    public List<TransactionCallback> getRecentCallbacks(int limit) {
        if (limit <= 0) {
            limit = 10;
        }
        return callbackRepository.findRecent(Math.min(limit, 1000));
    }

    /**
     * Get callbacks by status
     * 
     * @param status Status to search for
     * @return List of callbacks with the specified status
     */
    public List<TransactionCallback> getCallbacksByStatus(String status) {
        return callbackRepository.findByStatus(status);
    }

    /**
     * Get callbacks by payment status
     * 
     * @param paymentStatus Payment status to search for
     * @return List of callbacks with the specified payment status
     */
    public List<TransactionCallback> getCallbacksByPaymentStatus(String paymentStatus) {
        return callbackRepository.findByPaymentStatus(paymentStatus);
    }

    /**
     * Delete a callback by transaction ID
     * 
     * @param trid Transaction ID
     */
    public void deleteCallbackByTrid(String trid) {
        callbackRepository.deleteByTrid(trid);
        logger.info("Callback deleted for TRID: {}", trid);
    }

    /**
     * Clear all callbacks (use with caution)
     */
    public void clearAllCallbacks() {
        callbackRepository.deleteAll();
        logger.warn("All callbacks have been cleared");
    }
}

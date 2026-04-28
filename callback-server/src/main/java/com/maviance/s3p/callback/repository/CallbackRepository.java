package com.maviance.s3p.callback.repository;

import com.maviance.s3p.callback.model.TransactionCallback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for accessing TransactionCallback entities.
 */
@Repository
public interface CallbackRepository extends JpaRepository<TransactionCallback, Long> {

    /**
     * Find a callback by transaction ID
     * 
     * @param trid Transaction ID
     * @return Optional containing the callback if found
     */
    Optional<TransactionCallback> findByTrid(String trid);

    /**
     * Get recent callbacks ordered by creation date descending
     * 
     * @param limit Maximum number of records to return
     * @return List of recent callbacks
     */
    @Query(value = "SELECT * FROM transaction_callbacks ORDER BY created_at DESC LIMIT ?1", nativeQuery = true)
    List<TransactionCallback> findRecent(int limit);

    /**
     * Find all callbacks with a specific status
     * 
     * @param status Status to search for
     * @return List of callbacks with the specified status
     */
    List<TransactionCallback> findByStatus(String status);

    /**
     * Find all callbacks with a specific payment status
     * 
     * @param paymentStatus Payment status to search for
     * @return List of callbacks with the specified payment status
     */
    List<TransactionCallback> findByPaymentStatus(String paymentStatus);

    /**
     * Delete callback by transaction ID
     * 
     * @param trid Transaction ID
     */
    void deleteByTrid(String trid);
}

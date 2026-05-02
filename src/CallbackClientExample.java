/**
 * Example: Using S3P Callback Server for Transaction Status Updates
 * 
 * This example demonstrates:
 * 1. Using a deployed callback server (AWS Elastic Beanstalk or public URL)
 * 2. Waiting up to 2 minutes for callback notifications
 * 3. Falling back to /verifytx if callback doesn't arrive
 * 
 * Prerequisites:
 * - Callback server deployed to: https://your-callback-url.elasticbeanstalk.com
 * - Environment variable: CALLBACK_HOST set to your deployed server URL
 * 
 * Usage:
 *   export CALLBACK_HOST="https://your-callback-url.elasticbeanstalk.com"
 *   java CallbackClientExample
 */

import org.maviance.s3pjavaclient.ApiClient;
import org.maviance.s3pjavaclient.ApiException;
import org.maviance.s3pjavaclient.api.InitiateApi;
import org.maviance.s3pjavaclient.api.MasterdataApi;
import org.maviance.s3pjavaclient.api.VerifyApi;
import org.maviance.s3pjavaclient.model.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class CallbackClientExample {

    private static final String API_BASE_URL = "https://s3p.smobilpay.staging.maviance.info/v2";
    private static final String API_KEY = "a96f759a-37bf-4c42-a57f-1dd762d5ed48";
    private static final String API_SECRET = "d41eea8a-debd-4c6c-8b49-620b5855e745";
    private static final String X_API_VERSION = "3.0.5";

    // Configuration
    private static final int CALLBACK_TIMEOUT_MINUTES = 2;
    private static final int CALLBACK_CHECK_INTERVAL_SECONDS = 10;

    public static void main(String[] args) throws Exception {
        // Get callback server URL from environment or use default
        String callbackHost = System.getenv("CALLBACK_HOST");
        if (callbackHost == null) {
            callbackHost = "https://s3p-callback-server-prod.elasticbeanstalk.com";
        }

        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("S3P Payment with Callback Server Example");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("Callback Server: " + callbackHost);
        System.out.println("Timeout: " + CALLBACK_TIMEOUT_MINUTES + " minutes");
        System.out.println("Time: " + LocalDateTime.now());
        System.out.println("═══════════════════════════════════════════════════════════\n");

        try {
            // Step 1: Verify callback server is reachable
            verifyCallbackServerHealth(callbackHost);

            // Step 2: Initialize API client
            ApiClient apiClient = new ApiClient(API_BASE_URL, API_KEY, API_SECRET);
            apiClient.setDebugging(false);

            // Step 3: Generate transaction ID
            String trid = "tx-" + System.currentTimeMillis();
            String callbackUrl = callbackHost + "/api/v1/payment-callback";

            System.out.println("Step 1: Generating Transaction ID");
            System.out.println("  Transaction ID: " + trid);
            System.out.println("  Callback URL: " + callbackUrl + "\n");

            // Step 4: Get available cashout packages
            System.out.println("Step 2: Retrieving Cashout Packages");
            MasterdataApi masterDataApi = new MasterdataApi(apiClient);
            List<Cashout> packages = masterDataApi.cashoutGet(X_API_VERSION, 30053);
            Cashout cashout = packages.get(0);
            System.out.println("  Selected Package: " + cashout.getPayItemId() + "\n");

            // Step 5: Get quote
            System.out.println("Step 3: Getting Quote");
            InitiateApi initiateApi = new InitiateApi(apiClient);
            QuoteRequest quoteRequest = new QuoteRequest();
            quoteRequest.setAmount(5000);
            quoteRequest.setPayItemId(cashout.getPayItemId());
            Quote quote = initiateApi.quotestdPost(X_API_VERSION, quoteRequest);
            System.out.println("  Quote ID: " + quote.getQuoteId());
            System.out.println("  Amount: 5000 XAF\n");

            // Step 6: Perform transaction with callback URL
            System.out.println("Step 4: Initiating Payment Collection");
            CollectionRequest collection = new CollectionRequest();
            collection.setCustomerPhonenumber("237653754334");
            collection.setCustomerEmailaddress("jay@test.com");
            collection.setQuoteId(quote.getQuoteId());
            collection.setTag("Callback example");
            collection.setCdata("Using deployed callback server");
            collection.setServiceNumber("697123200");
            collection.setTrid(trid);

            org.maviance.s3pjavaclient.api.ConfirmApi confirmApi = 
                new org.maviance.s3pjavaclient.api.ConfirmApi(apiClient);
            CollectionResponse payment = confirmApi.collectstdPost(X_API_VERSION, collection);
            System.out.println("  Payment initiated. Receipt Number: " + payment.getReceiptNumber() + "\n");

            // Step 7: Wait for callback with timeout
            System.out.println("Step 5: Waiting for Callback Notification");
            System.out.println("  Waiting up to " + CALLBACK_TIMEOUT_MINUTES + " minutes for callback...\n");

            TransactionStatus status = waitForCallbackWithFallback(
                apiClient,
                callbackHost,
                trid,
                CALLBACK_TIMEOUT_MINUTES * 60,
                CALLBACK_CHECK_INTERVAL_SECONDS
            );

            // Step 8: Display result
            displayTransactionStatus(status);

        } catch (ApiException e) {
            System.err.println("API Error: " + e.getMessage());
            if (e.getResponseBody() != null) {
                System.err.println("Response: " + e.getResponseBody());
            }
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Verify that the callback server is reachable and healthy
     */
    private static void verifyCallbackServerHealth(String callbackHost) throws Exception {
        try {
            java.net.URL url = new java.net.URL(callbackHost + "/api/v1/health");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                System.out.println("✓ Callback server is reachable and healthy\n");
            } else {
                System.err.println("✗ Callback server returned status: " + responseCode);
                System.err.println("  Make sure the callback server is deployed at: " + callbackHost);
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("✗ Cannot reach callback server at: " + callbackHost);
            System.err.println("  Error: " + e.getMessage());
            System.err.println("\nSetup instructions:");
            System.err.println("  1. Deploy callback server to AWS Elastic Beanstalk");
            System.err.println("  2. Set CALLBACK_HOST environment variable:");
            System.err.println("     export CALLBACK_HOST=\"https://your-callback-url.elasticbeanstalk.com\"");
            System.err.println("  3. Or set in code: callbackHost = \"https://your-url\"");
            System.exit(1);
        }
    }

    /**
     * Wait for callback notification or fallback to /verifytx after timeout
     */
    private static TransactionStatus waitForCallbackWithFallback(
            ApiClient apiClient,
            String callbackHost,
            String trid,
            long timeoutSeconds,
            int checkIntervalSeconds) throws Exception {

        long startTime = System.currentTimeMillis();
        long endTime = startTime + (timeoutSeconds * 1000);

        while (System.currentTimeMillis() < endTime) {
            try {
                // Try to retrieve callback from callback server
                TransactionStatus status = getCallbackStatus(callbackHost, trid);
                if (status != null && !status.isPending()) {
                    System.out.println("  ✓ Callback received!");
                    return status;
                }

                long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                System.out.println("  Waiting... (" + elapsedSeconds + "s / " + timeoutSeconds + "s)");
                Thread.sleep(checkIntervalSeconds * 1000);

            } catch (Exception e) {
                System.out.println("  Check failed: " + e.getMessage());
                Thread.sleep(checkIntervalSeconds * 1000);
            }
        }

        // Timeout reached - fallback to /verifytx
        System.out.println("  ✗ No callback received within timeout\n");
        System.out.println("Step 6: Falling back to /verifytx endpoint");
        return verifyTransactionStatus(apiClient, trid);
    }

    /**
     * Retrieve callback status from the callback server
     */
    private static TransactionStatus getCallbackStatus(String callbackHost, String trid) 
            throws Exception {
        try {
            String url = callbackHost + "/api/v1/callback/" + trid;
            java.net.HttpURLConnection conn = 
                (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                java.util.Scanner scanner = new java.util.Scanner(conn.getInputStream());
                String response = scanner.useDelimiter("\\A").next();
                scanner.close();

                // Parse JSON response
                return parseCallbackResponse(response);
            } else if (responseCode == 404) {
                return null; // Callback not received yet
            } else {
                throw new Exception("Unexpected response code: " + responseCode);
            }
        } catch (java.net.ConnectException e) {
            throw new Exception("Connection refused: " + e.getMessage());
        }
    }

    /**
     * Parse callback JSON response
     */
    private static TransactionStatus parseCallbackResponse(String json) {
        TransactionStatus status = new TransactionStatus();

        // Simple JSON parsing (in production, use Jackson or Gson)
        java.util.regex.Pattern stringField = java.util.regex.Pattern.compile("\\\"(trid|status|message)\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        java.util.regex.Matcher matcher = stringField.matcher(json);
        while (matcher.find()) {
            String field = matcher.group(1);
            String value = matcher.group(2);
            switch (field) {
                case "trid":
                    status.setTrid(value);
                    break;
                case "status":
                    status.setStatus(value);
                    status.setPending("PENDING".equalsIgnoreCase(value));
                    break;
                case "message":
                    status.setMessage(value);
                    break;
            }
        }

        return status;
    }

    /**
     * Verify transaction status using /verifytx endpoint
     */
    private static TransactionStatus verifyTransactionStatus(ApiClient apiClient, String trid) 
            throws ApiException {
        VerifyApi verifyApi = new VerifyApi(apiClient);
        List<PaymentStatus> statusList = verifyApi.verifytxGet(X_API_VERSION, null, trid);

        if (statusList != null && !statusList.isEmpty()) {
            PaymentStatus paymentStatus = statusList.get(0);
            TransactionStatus status = new TransactionStatus();
            status.setTrid(trid);
            // Convert PaymentStatus.StatusEnum to String
            PaymentStatus.StatusEnum statusEnum = paymentStatus.getStatus();
            String statusStr = statusEnum != null ? statusEnum.toString() : "UNKNOWN";
            status.setStatus(statusStr);
            // Use available fields as message
            String message = "PTN: " + paymentStatus.getPtn() + ", Tag: " + paymentStatus.getTag();
            status.setMessage(message);
            status.setPending(false);
            return status;
        }

        return null;
    }

    /**
     * Display final transaction status
     */
    private static void displayTransactionStatus(TransactionStatus status) {
        System.out.println("\n═══════════════════════════════════════════════════════════");
        System.out.println("Transaction Status");
        System.out.println("═══════════════════════════════════════════════════════════");

        if (status != null) {
            System.out.println("Transaction ID: " + status.getTrid());
            System.out.println("Status: " + status.getStatus());
            if (status.getMessage() != null) {
                System.out.println("Message: " + status.getMessage());
            }
            System.out.println("Time: " + LocalDateTime.now());

            if ("SUCCESS".equals(status.getStatus())) {
                System.out.println("\n✓ Transaction completed successfully!");
            } else if ("PENDING".equals(status.getStatus())) {
                System.out.println("\n⏳ Transaction is still pending");
            } else {
                System.out.println("\n✗ Transaction failed");
            }
        } else {
            System.out.println("✗ Unable to determine transaction status");
        }
        System.out.println("═══════════════════════════════════════════════════════════");
    }

    /**
     * Simple transaction status model
     */
    private static class TransactionStatus {
        private String trid;
        private String status;
        private String message;
        private boolean pending;

        public String getTrid() { return trid; }
        public void setTrid(String trid) { this.trid = trid; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public boolean isPending() { return pending; }
        public void setPending(boolean pending) { this.pending = pending; }
    }
}

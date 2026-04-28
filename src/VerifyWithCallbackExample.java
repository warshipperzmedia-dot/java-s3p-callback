import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.maviance.s3pjavaclient.ApiClient;
import org.maviance.s3pjavaclient.ApiException;
import org.maviance.s3pjavaclient.api.InitiateApi;
import org.maviance.s3pjavaclient.api.MasterdataApi;
import org.maviance.s3pjavaclient.api.VerifyApi;
import org.maviance.s3pjavaclient.model.Cashout;
import org.maviance.s3pjavaclient.model.CollectionResponse;
import org.maviance.s3pjavaclient.model.PaymentStatus;
import org.maviance.s3pjavaclient.model.Quote;
import org.maviance.s3pjavaclient.model.QuoteRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Example showing how to wait for a callback and fall back to /verifytx after 2 minutes.
 *
 * Note: this example uses a local HTTP server for callback delivery. In production you
 * need a public webhook URL (or a tunnel) so the payment provider can reach it.
 */
public class VerifyWithCallbackExample {
    private static volatile String callbackPayload;
    private static volatile String callbackStatus;

    public static void main(String[] args) throws Exception {
        String baseUrl = "https://s3p.smobilpay.staging.maviance.info/v2";
        String apiKey = "a96f759a-37bf-4c42-a57f-1dd762d5ed48";
        String apiSecret = "d41eea8a-debd-4c6c-8b49-620b5855e745";
        String xApiVersion = "3.0.5";
        String trid = "tx-" + System.currentTimeMillis();

        ApiClient apiClient = new ApiClient(baseUrl, apiKey, apiSecret);
        apiClient.setDebugging(false);

        InitiateApi initiateApi = new InitiateApi(apiClient);
        MasterdataApi masterDataApi = new MasterdataApi(apiClient);

        int callbackPort = 8080;
        String callbackPath = "/payment-callback";
        String callbackUrl = "http://localhost:" + callbackPort + callbackPath;

        CountDownLatch callbackLatch = new CountDownLatch(1);
        HttpServer server = startCallbackServer(callbackPort, callbackPath, callbackLatch);

        try {
            // Retrieve cash-out packages.
            List<Cashout> packages = masterDataApi.cashoutGet(xApiVersion, 30053);
            Cashout cashout = packages.get(0);
            System.out.println("Selected cashout package: " + cashout);

            // Get quote.
            QuoteRequest quote = new QuoteRequest();
            quote.setAmount(5000);
            quote.setPayItemId(cashout.getPayItemId());
            Quote offer = initiateApi.quotestdPost(xApiVersion, quote);
            System.out.println("Quote: " + offer);

            // Build the request body with the callback URL.
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("customerPhonenumber", "237653754334");
            requestBody.put("customerEmailaddress", "jay@test.com");
            requestBody.put("quoteId", offer.getQuoteId());
            requestBody.put("tag", "Callback sample");
            requestBody.put("cdata", "Callback example");
            requestBody.put("serviceNumber", "697123200");
            requestBody.put("trid", trid);
            requestBody.put("callbackUrl", callbackUrl); // adjust this field name if provider expects callback_url

            System.out.println("Callback URL sent: " + callbackUrl);

            CollectionResponse payment = executeCollectStdWithCallbackUrl(apiClient, xApiVersion, requestBody);
            System.out.println("Collect response: " + payment);

            // Wait up to 2 minutes for the callback.
            System.out.println("Waiting up to 2 minutes for webhook callback...");
            boolean gotCallback = callbackLatch.await(2, TimeUnit.MINUTES);

            if (gotCallback) {
                System.out.println("Callback received:");
                System.out.println("  payload: " + callbackPayload);
                System.out.println("  status: " + callbackStatus);
            } else {
                System.out.println("No callback received in 2 minutes. Falling back to /verifytx.");
                verifyTransaction(apiClient, xApiVersion, trid);
            }
        } catch (ApiException e) {
            System.out.println("API error: " + e.getMessage());
            System.out.println(e.getResponseBody());
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer startCallbackServer(int port, String path, CountDownLatch latch) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext(path, new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }

                byte[] requestBody = exchange.getRequestBody().readAllBytes();
                callbackPayload = new String(requestBody, StandardCharsets.UTF_8);
                callbackStatus = extractJsonField(callbackPayload, "status");
                if (callbackStatus == null) {
                    callbackStatus = extractJsonField(callbackPayload, "payment_status");
                }

                String response = "Callback received";
                exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                latch.countDown();
            }
        });
        server.setExecutor(null);
        server.start();
        return server;
    }

    private static CollectionResponse executeCollectStdWithCallbackUrl(ApiClient apiClient, String xApiVersion, Map<String, Object> requestBody) throws ApiException {
        List<org.maviance.s3pjavaclient.Pair> queryParams = new ArrayList<>();
        List<org.maviance.s3pjavaclient.Pair> collectionQueryParams = new ArrayList<>();
        Map<String, String> headerParams = new HashMap<>();
        headerParams.put("x-api-version", xApiVersion);
        headerParams.put("Accept", "application/json");
        Map<String, Object> formParams = new HashMap<>();

        com.squareup.okhttp.Call call = apiClient.buildCall(
                "/collectstd",
                "POST",
                queryParams,
                collectionQueryParams,
                requestBody,
                headerParams,
                formParams,
                new String[0],
                null);

        org.maviance.s3pjavaclient.ApiResponse<CollectionResponse> response = apiClient.execute(call, CollectionResponse.class);
        return response.getData();
    }

    private static void verifyTransaction(ApiClient apiClient, String xApiVersion, String trid) throws ApiException {
        VerifyApi verifyApi = new VerifyApi(apiClient);
        List<PaymentStatus> statusList = verifyApi.verifytxGet(xApiVersion, null, trid);
        System.out.println("Verify result count=" + statusList.size());
        for (PaymentStatus status : statusList) {
            System.out.println(status);
        }
    }

    private static String extractJsonField(String json, String fieldName) {
        String quotedField = '"' + fieldName + '"';
        int index = json.indexOf(quotedField);
        if (index < 0) {
            return null;
        }
        int colon = json.indexOf(':', index + quotedField.length());
        if (colon < 0) {
            return null;
        }
        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote < 0) {
            return null;
        }
        int secondQuote = json.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) {
            return null;
        }
        return json.substring(firstQuote + 1, secondQuote);
    }
}

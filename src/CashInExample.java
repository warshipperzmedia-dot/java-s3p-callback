import org.maviance.s3pjavaclient.ApiClient;
import org.maviance.s3pjavaclient.ApiException;
import org.maviance.s3pjavaclient.api.ConfirmApi;
import org.maviance.s3pjavaclient.api.MasterdataApi;
import org.maviance.s3pjavaclient.api.VerifyApi;
import org.maviance.s3pjavaclient.api.InitiateApi;
import org.maviance.s3pjavaclient.model.*;

import java.util.List;
import java.util.UUID;

public class CashInExample {
    // Some sample values - these are not valid identifiers
    // Cash In service number -> In this case a msisdn
    private static final String serviceNumber = "697123200";
    private static final int serviceId = 50052;

    // Customer details
    private static final String phone = "237653754334";
    private static final String email = "jay@test.com";
    private static final String trid = UUID.randomUUID().toString(); // Unique transaction id
    private static final String xApiVersion = "3.0.5";

    public static void main(String[] args) {
        ApiClient apiClient = new ApiClient("https://s3p.smobilpay.staging.maviance.info/v2", "a96f759a-37bf-4c42-a57f-1dd762d5ed48", "d41eea8a-debd-4c6c-8b49-620b5855e745");
        
        apiClient.setDebugging(false);
        ConfirmApi confirmApi = new ConfirmApi(apiClient);
        InitiateApi initiateApi = new InitiateApi(apiClient);
        MasterdataApi masterDataApi = new MasterdataApi(apiClient);

        try {
        
            // Retrieve available cash in packages
            
            List<Cashin> packages = masterDataApi.cashinGet(xApiVersion, serviceId);
            
            // Select the first packages for sake of demonstration
            Cashin cashin = packages.get(0);

            System.out.println("The cashin data is: "+cashin);

            // Retrieve pricing information by requesting a quote for a set amount for the linked payment item id   

            QuoteRequest quote = new QuoteRequest();
            quote.setAmount(5000);
            quote.setPayItemId(cashin.getPayItemId());
            Quote offer = initiateApi.quotestdPost(xApiVersion, quote);
            System.out.println("The quote data is: "+offer);

            // Finalize by confirming the collection
            
            CollectionRequest collection = new CollectionRequest();
            collection.setCustomerPhonenumber(phone);
            collection.setCustomerEmailaddress(email);
            collection.setQuoteId(offer.getQuoteId());
            collection.setTag("Test cashin");
            collection.setCdata("Test Cdata with Jay");
            collection.setServiceNumber(""+serviceNumber);
            collection.setTrid(trid); // Add the generated trid
            CollectionResponse payment = confirmApi.collectstdPost(xApiVersion, collection);
            System.out.println("The payment is: "+payment);

            // Lookup record in Smobilpay by TRID to retrieve the payment status
            try {
                // Delay the lookup by 30 seconds
                System.out.println("Waiting for 30 seconds before verifying payment...");
                Thread.sleep(30000); // 30 seconds delay in milliseconds
            } catch (InterruptedException e) {
                System.out.println("The delay was interrupted.");
                Thread.currentThread().interrupt(); // Restore the interrupted status
            }
            
            VerifyApi verifyApi = new VerifyApi(apiClient);
            System.out.println("The payment verify data is: "+payment);
            List<PaymentStatus> historystds =  verifyApi.verifytxGet(xApiVersion, null, trid);
            System.out.println("The history data is: "+historystds);
            if (historystds.size() != 1) {
            // Should have found exactly one record."
                System.exit(0);
            }
        } catch (ApiException e) {
            // Add more detailed handling here 
            System.out.println("An error occurred: \n");
            System.out.println(e.getResponseBody());
        }

    }
}
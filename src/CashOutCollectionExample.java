import org.maviance.s3pjavaclient.ApiClient;
import org.maviance.s3pjavaclient.ApiException;
import org.maviance.s3pjavaclient.api.ConfirmApi;
import org.maviance.s3pjavaclient.api.MasterdataApi;
import org.maviance.s3pjavaclient.api.VerifyApi;
import org.maviance.s3pjavaclient.api.InitiateApi;
import org.maviance.s3pjavaclient.model.*;

import java.util.List;

public class CashOutCollectionExample {
    // Some sample values - these are not valid identifiers
    // Cash Out service number -> In this case a msisdn
    private static final String serviceNumber = "697123200";
    private static final int serviceId = 30053;

    // Customer details
    private static final String phone = "237653754334";
    private static final String email = "jay@test.com";
    private static final String trid = "202510141005";
    private static final String xApiVersion = "3.0.5";

    public static void main(String[] args) {
        ApiClient apiClient = new ApiClient("https://s3p.smobilpay.staging.maviance.info/v2", "a96f759a-37bf-4c42-a57f-1dd762d5ed48", "d41eea8a-debd-4c6c-8b49-620b5855e745");
        
        apiClient.setDebugging(false);
        ConfirmApi confirmApi = new ConfirmApi(apiClient);
        InitiateApi initiateApi = new InitiateApi(apiClient);
        MasterdataApi masterDataApi = new MasterdataApi(apiClient);

        try {
        
            // Retrieve available cash out packages
            
            List<Cashout> packages = masterDataApi.cashoutGet(xApiVersion, serviceId);
            
            // Select the first packages for sake of demonstration
            Cashout cashout = packages.get(0);

            System.out.println("The cashout data is: "+cashout);

            // Retrieve pricing information by requesting a quote for a set amount for the linked payment item id   

            QuoteRequest quote = new QuoteRequest();
            quote.setAmount(5000);
            quote.setPayItemId(cashout.getPayItemId());
            Quote offer = initiateApi.quotestdPost(xApiVersion, quote);
            System.out.println("The quote data is: "+offer);

            // Finalize by confirming the collection
            
            CollectionRequest collection = new CollectionRequest();
            collection.setCustomerPhonenumber(phone);
            collection.setCustomerEmailaddress(email);
            collection.setQuoteId(offer.getQuoteId());
            collection.setTag("Test cashout");
            collection.setCdata("Test Cdata with Jay");
            collection.setServiceNumber(""+serviceNumber);
            collection.setTrid(trid); // Add the generated trid
            CollectionResponse payment = confirmApi.collectstdPost(xApiVersion, collection);
            System.out.println("The payment is: "+payment);

            // Lookup record in Smobilpay by PTN to retrieve the payment status
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

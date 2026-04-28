import org.maviance.s3pjavaclient.ApiClient;
import org.maviance.s3pjavaclient.ApiException;
import org.maviance.s3pjavaclient.api.MasterdataApi;
import org.maviance.s3pjavaclient.api.ConfirmApi;
import org.maviance.s3pjavaclient.api.InitiateApi;
import org.maviance.s3pjavaclient.api.VerifyApi;
import org.maviance.s3pjavaclient.model.*;

import java.util.List;

public class ProductCollectionExample {
    // Some sample values - these are not valid identifiers
    private static String serviceNumber = "24210044235676";
    // Product service number
    private static int serviceId = 27112024;

    // Customer details
    private static String phone = "12000";
    private static String email = "name@example.com";

    public static void main(String[] args) {
        ApiClient apiClient = new ApiClient("https://s3p.smobilpay.staging.maviance.info/v2", "1c6fbc97-c186-4091-923c-e2535fe49215", "2b4e01f1-7600-4152-bd91-c309e4d91fb5");
        
        apiClient.setDebugging(false);
        ConfirmApi confirmApi = new ConfirmApi(apiClient);
        InitiateApi initiateApi = new InitiateApi(apiClient);
        MasterdataApi masterDataApi = new MasterdataApi(apiClient);
        String xApiVersion = "3.0.5"; 

        try {
            // Retrieve available product packages 

            List<Product> products = masterDataApi.productGet(xApiVersion, serviceId);
            
            // Select the first product for sake of demonstration
            
            Product product = products.get(0);

            // Retrieve pricing information by requesting a quote for a set amount for the linked payment item id   
            System.out.println("The product data is: {}"+product);
            QuoteRequest quote = new QuoteRequest();
            quote.setAmount(1000);
            // quote.setAmount(product.getAmountLocalCur().intValue());
            quote.setPayItemId(product.getPayItemId());

            Quote offer = initiateApi.quotestdPost(xApiVersion, quote);
            System.out.println("The quote data is: "+offer);
            // Finalize by confirming the collection
            
            CollectionRequest collection = new CollectionRequest();
            collection.setCustomerPhonenumber(phone);
            collection.setCustomerEmailaddress(email);
            collection.setQuoteId(offer.getQuoteId());
            collection.setServiceNumber(String.valueOf(serviceNumber));
            CollectionResponse payment = confirmApi.collectstdPost(xApiVersion, collection);

            System.out.println("The payment is: "+payment);

            // Lookup record in Smobilpay by PTN to retrieve the payment status
            
            VerifyApi verifyApi = new VerifyApi(apiClient);
            List<PaymentStatus> historystds =  verifyApi.verifytxGet(xApiVersion, payment.getPtn(), null);
            System.out.println("The history is: "+historystds);
            if (historystds.size() != 1) {
                // Should have found exactly one record."
                System.exit(0);
            }
        } catch (ApiException e) {
            // add more handling
            System.out.println("An error occurred: \n");
            System.out.println(e.getResponseBody());
        }
    }
}

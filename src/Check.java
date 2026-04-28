import org.maviance.s3pjavaclient.ApiClient;
import org.maviance.s3pjavaclient.ApiException;
import org.maviance.s3pjavaclient.api.HealthcheckApi;
import org.maviance.s3pjavaclient.model.Ping;


class Check {

 public static void main(String[] args) {
  ApiClient apiClient = new ApiClient("https://s3p.smobilpay.staging.maviance.info/v2", "a96f759a-37bf-4c42-a57f-1dd762d5ed48", "d41eea8a-debd-4c6c-8b49-620b5855e745");

  HealthcheckApi checksApi = new HealthcheckApi(apiClient);
  String xApiVersion = "3.0.5";

  try {
   Ping ping = checksApi.pingGet(xApiVersion);
   System.out.println(ping);
  } catch (ApiException e) {
   System.out.println("An error occurred: \n");
   System.out.println(e.getResponseBody());
  }
 }
}
package SignUp;

import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;

public class CreateLeadForMultipleUsers {
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String LEAD_URL = "https://api-stg.gromo.in/api/v2/miscellaneousLeads?allowSelfLead=false";
    private static final String LEAD_UPDATE_URL = "https://api-stg.gromo.in/api/v2/miscellaneousLeadUpdated";

    private static List<String> gpuidList;
    private String leadId;  // Lead ID for tracking

    public ResponseEntity<String> createLead(String gpuid, String productTypeId, String productTypeName, int kpi1Payin, int kpi1Payout) {
        String firstName = CreateLeadForMultipleUsers.generateRandomName();
        String lastName = CreateLeadForMultipleUsers.generateRandomName();
        String phoneNumber = CreateLeadForMultipleUsers.generateRandomPhoneNumber();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestBody = "{ \"lead\": { " +
                "\"gromoPartner\": \"" + gpuid + "\", " +
                "\"firstName\": \"" + firstName + "\", " +
                "\"lastName\": \"" + lastName + "\", " +
                "\"phone\": \"" + phoneNumber + "\", " +
                "\"email\": \"dfvatghwkt@gromo.in\", " +
                "\"productTypeId\": \"" + productTypeId + "\", " +
                "\"productTypeName\": \"" + productTypeName + "\", " +
                "\"gwaid\": \"1664900451.1709560814\", " +
                "\"whatsappConsent\": true, " +
                "\"pincode\": \"122003\", " +
                "\"leadMetaData\": { \"leadSource\": \"null\", \"leadSubSource\": \"null\" } " +
                "} }";

        System.out.println("Create Lead Request: " + requestBody);

        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(LEAD_URL, HttpMethod.POST, requestEntity, String.class);

        System.out.println("Create Lead Response: " + response.getBody());

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            JSONObject jsonResponse = new JSONObject(response.getBody());
            this.leadId = jsonResponse.optString("leadId", null);
        }

        return response;
    }

    public ResponseEntity<String> updateLead(int kpi1Payin, int kpi1Payout) {
        if (leadId == null) {
            System.out.println("Lead ID is null, cannot update lead.");
            return null;
        }

        try {
            Thread.sleep(3000); // Wait for 3 seconds before updating lead
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String todayDate = LocalDate.now().toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestBody = "{ \"leadId\": \"" + leadId + "\", " +
                "\"misSource\": \"api\", " +
                "\"freebie\": false, " +
                "\"leadStatus\": \"Success\", " +
                "\"subStatus\": \"Success\", " +
                "\"kpi1Payin\": " + kpi1Payin + ", " +
                "\"kpi1Payout\": " + kpi1Payout + ", " +
                "\"kpi1SuccessDate\": \"" + todayDate + "\", " +
                "\"dateOfSale\": \"" + todayDate + "\", " +
                "\"dateOfRevenue\": \"" + todayDate + "\", " +
                "\"loanAmount\": \"\" }";

        System.out.println("Update Lead Request: " + requestBody);

        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(LEAD_UPDATE_URL, HttpMethod.PUT, requestEntity, String.class);

        System.out.println("Update Lead Response: " + response.getBody());

        return response;
    }

    private static String generateRandomName() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        int nameLength = 5 + random.nextInt(6);
        for (int i = 0; i < nameLength; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static String generateRandomPhoneNumber() {
        Random random = new Random();
        int[] validFirstDigits = {6, 7, 8, 9};
        int firstDigit = validFirstDigits[random.nextInt(validFirstDigits.length)];
        StringBuilder phone = new StringBuilder();
        phone.append(firstDigit);
        for (int i = 1; i < 10; i++) {
            phone.append(random.nextInt(10));
        }
        return phone.toString();
    }


    public static void setGpuidList(List<String> gpuids) {
        gpuidList = gpuids;
    }

    public static List<String> getGpuidList() {
        return gpuidList;
    }

    public void createLeads() {
        for (String gpuid : gpuidList) {
            System.out.println("Creating lead for GPUID: " + gpuid);
            // Call API to create lead using gpuid
        }
    }

    public static void main(String[] args) {
        String[] gpuidList = { "76VF7719", "2IH10333", "58VE8875"};
        int[] kpi1Payouts =  {  100,2000,           5000,  };
        String[] productTypeIds = {"344", "400", "269"};
        String[] productTypeNames = {"Swiggy HDFC Bank Credit Card", "BlinkX", "KreditBee"};
        int[] kpi1Payins = {5500, 36000, 100500};



        for (int i = 0; i < 3; i++) {
            CreateLeadForMultipleUsers leadService = new CreateLeadForMultipleUsers();

            System.out.println("\nCreating lead for User " + (i + 1) + " with GPUID: " + gpuidList[i]);

            ResponseEntity<String> leadResponse = leadService.createLead(
                    gpuidList[i], productTypeIds[i], productTypeNames[i], kpi1Payins[i], kpi1Payouts[i]
            );

            if (leadResponse.getStatusCode() == HttpStatus.OK) {
                leadService.updateLead(kpi1Payins[i], kpi1Payouts[i]);
            }
        }
    }
}

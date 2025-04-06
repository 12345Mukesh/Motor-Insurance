package InsuranceWallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public class OnboardMerchant {

    private final JdbcTemplate jdbcTemplate;

    public OnboardMerchant(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public static void main(String[] args) throws Exception {
        // ✅ Initialize DB connection
        DataSource dataSource = DatabaseConfig.getDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // ✅ Create an instance and execute onboarding
        OnboardMerchant onboardMerchant = new OnboardMerchant(jdbcTemplate);
        onboardMerchant.processMerchantOnboarding();
    }

    public void processMerchantOnboarding() throws Exception {
        ResponseEntity<String> response = onboardMerchant();

        // ✅ Print Full API Response
        System.out.println("Full API Response: " + response.getBody());

        // ✅ Check if response body is null or empty
        if (response.getBody() == null || response.getBody().isEmpty()) {
            throw new IllegalStateException("❌ Error: Empty response received from merchant onboarding API");
        }

        // ✅ Parse response safely
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseJson = objectMapper.readTree(response.getBody());

        // ✅ Validate if "data" field exists
        if (!responseJson.has("data")) {
            throw new IllegalStateException("❌ Error: 'data' field missing in API response: " + response.getBody());
        }

        // ✅ Extract merchantId safely
        String merchantId = responseJson.path("data").path("merchantId").asText(null);
        if (merchantId == null || merchantId.isEmpty()) {
            throw new IllegalStateException("❌ Error: 'merchantId' is missing or empty in API response");
        }

        System.out.println("Extracted merchantId: " + merchantId);

        // ✅ Validate merchant in DB
        validateMerchantInDB(merchantId);
    }


    public ResponseEntity<String> onboardMerchant() throws Exception {
        // ✅ Read JSON file
        InputStream inputStream = new ClassPathResource("insuranceWallet.json").getInputStream();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(inputStream);

        // ✅ Extract API base URL & merchant endpoint
        String apiBaseUrl = jsonNode.get("API_URL").asText();
        String merchantEndpoint = jsonNode.get("merchant").asText();
        String fullApiUrl = apiBaseUrl + merchantEndpoint;

        // ✅ Convert JSON to a Map
        Map<String, Object> requestBody = objectMapper.convertValue(jsonNode, Map.class);
        requestBody.remove("API_URL"); // ✅ Remove API_URL key
        requestBody.remove("merchant");

        // ✅ Filter out unnecessary keys
        requestBody = filterMerchantKeys(requestBody);

        // ✅ Generate random two-digit suffix for accountOwnerId
        String baseAccountOwnerId = requestBody.get("accountOwnerId").toString();
        int randomSuffix = new Random().nextInt(90) + 10; // Generates 10-99
        String newAccountOwnerId = baseAccountOwnerId + randomSuffix;
        requestBody.put("accountOwnerId", newAccountOwnerId);

        System.out.println("Generated accountOwnerId: " + newAccountOwnerId);
        System.out.println("API URL: " + fullApiUrl);

        // ✅ Create Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // ✅ Create Request
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        RestTemplate restTemplate = new RestTemplate();

        // ✅ Send POST Request
        return restTemplate.exchange(fullApiUrl, HttpMethod.POST, requestEntity, String.class);
    }

    private Map<String, Object> filterMerchantKeys(Map<String, Object> requestBody) {
        // ✅ Define required keys for the Merchant API
        Set<String> allowedKeys = Set.of(
                "API_URL", "merchant","domain","name", "category",
                "purpose", "accountOwnerId", "metadata"
        );

        // ✅ Keep only the allowed keys
        return requestBody.entrySet()
                .stream()
                .filter(entry -> allowedKeys.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public void validateMerchantInDB(String merchantId) {
        String query = "SELECT account_type_id, account_owner_id FROM accounts WHERE account_id = ?";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(query, merchantId);

        if (!results.isEmpty()) {
            Map<String, Object> row = results.get(0);

            // ✅ Safely extract values
            Number accountTypeIdNumber = (Number) row.get("account_type_id");
            Number accountOwnerIdNumber = (Number) row.get("account_owner_id");

            // ✅ Explicitly check if it's Long and convert properly
            int accountTypeId = (accountTypeIdNumber != null) ? accountTypeIdNumber.intValue() : -1;
            int accountOwnerId = (accountOwnerIdNumber instanceof Long)
                    ? ((Long) accountOwnerIdNumber).intValue()
                    : (accountOwnerIdNumber != null ? accountOwnerIdNumber.intValue() : -1);

            // ✅ Print values
            System.out.println("✅ Merchant Details Found in DB:");
            System.out.println(" - Account Type ID: " + accountTypeId);
            System.out.println(" - Account Owner ID: " + accountOwnerId);

            // ✅ Validation: If accountOwnerId is 4, print success message
            if (accountOwnerId == 4) {
                System.out.println("✅ Merchant account created successfully!");
            }
        } else {
            System.out.println("❌ Merchant ID not found in the database!");
        }
    }



}

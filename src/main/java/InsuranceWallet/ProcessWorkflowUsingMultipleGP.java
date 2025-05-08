package InsuranceWallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class ProcessWorkflowUsingMultipleGP {
    private static String globalGpGbWallet;
    private static String globalGpGbBank;
    private static String globalGpGbTds;
    private static String leadId;
    private static String baseUrl;
    private static String processApiUrl;
    private static String reversalApiUrl;
    private static JsonNode jsonConfig;
    private BigDecimal matchedTransactionAmount;
    private static String matchedTransactionId;
    private static String gpId = "10795";
    private static String lead;
    private static final boolean USE_RANDOM_GP_ID = false;
    public static String merchantAccountId = "merchant_gromo_gromo-insure_cancel_bank_test";
    private final JdbcTemplate jdbcTemplate;

    public ProcessWorkflowUsingMultipleGP(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public static void main(String[] args) throws Exception {
        // Get DataSource from DatabaseConfig
        DataSource dataSource = DatabaseConfig.getDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // Test DB connection
        new ProcessWorkflowUsingMultipleGP(jdbcTemplate).testDBConnection();  // Call this to test the connection

        // Load the configuration from JSON
        loadJsonConfig();

        // Create an instance of ProcessWorkflow with the jdbcTemplate
        ProcessWorkflowUsingMultipleGP processWorkflow = new ProcessWorkflowUsingMultipleGP(jdbcTemplate);

        // Conditional flag to decide whether to onboard a single GP or multiple GPs
        boolean onboardMultiple = true; // Change this flag to false for single GP, true for multiple GPs

        if (onboardMultiple) {
            // Onboard multiple GPs
            int numberOfGPsToOnboard = 100;  // Specify the number of GPs to onboard
            processWorkflow.onboardMultipleGPs(numberOfGPsToOnboard);
        } else {
            // Onboard a single GP
            String gpIdToOnboard = (USE_RANDOM_GP_ID) ? generateRandomGpId() : gpId;
            processWorkflow.executeOnboarding(gpIdToOnboard);
        }
    }

    private static void loadJsonConfig() throws Exception {
        InputStream inputStream = new ClassPathResource("insuranceWallet.json").getInputStream();
        ObjectMapper objectMapper = new ObjectMapper();
        jsonConfig = objectMapper.readTree(inputStream);

        baseUrl = jsonConfig.get("API_URL").asText();
        processApiUrl = baseUrl + jsonConfig.get("workflow").asText();
        leadId = jsonConfig.get("leadId").asText();
        reversalApiUrl = baseUrl + jsonConfig.get("reversalApiUrl").asText();

        if (USE_RANDOM_GP_ID) {
            gpId = generateRandomGpId();
            System.out.println("🔁 Using new generated gpId: " + gpId);
        } else {
            System.out.println("📌 Using hardcoded gpId: " + gpId);
        }
    }

    // Method to onboard multiple GPs
    public void onboardMultipleGPs(int numberOfGPs) throws Exception {
        for (int i = 0; i < numberOfGPs; i++) {
            // Generate a new random GP ID for each GP
            String gpId = generateRandomGpId();

            // Call onboardGP for each GP
            ResponseEntity<String> response = onboardGP(gpId);
            System.out.println("Full API response for gpId " + gpId + ": " + response.getBody());
            validateGPDatabaseEntries(gpId);  // You can validate the GP database after each onboarding.
        }

        // Optionally return a success message or log it.
        System.out.println("✅ Successfully onboarded " + numberOfGPs + " GPs.");
    }

    public void executeOnboarding(String gpId) throws Exception {
        ResponseEntity<String> response = onboardGP(gpId);
        System.out.println("Full API response: " + response.getBody());
        validateGPDatabaseEntries(gpId);
    }

    public ResponseEntity<String> onboardGP(String gpId) throws Exception {
        String gpEndpoint = jsonConfig.get("gp_Account").asText();
        boolean isPosp = jsonConfig.get("isPosp").asBoolean();
        String posId = jsonConfig.get("posId").asText();

        if (!isPosp) {
            throw new IllegalArgumentException("❌ Error: isPosp is false. Cannot proceed.");
        }

        String apiUrl = baseUrl + gpEndpoint;
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("gpId", gpId);
        requestBody.put("isPosp", isPosp);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("additionalInfo", "gp all account is created general balance, coin, scratch card");
        metadata.put("posId", posId);
        requestBody.put("metadata", metadata);
        requestBody = filterGPKeys(requestBody);

        System.out.println("📤 Sending onboarding request for gpId: " + gpId);
        System.out.println("🔗 API URL: " + apiUrl);

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            String prettyPayload = mapper.writeValueAsString(requestBody);
            System.out.println("🚀 Creating GP Account:\n" + prettyPayload);
        } catch (Exception e) {
            System.out.println("❗ Error Creating GP Account: " + e.getMessage());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, requestEntity, String.class);

        // 🧠 Parse and extract accounts from the response
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.getBody());

        JsonNode accounts = root.path("data").path("accounts");

        for (JsonNode account : accounts) {
            String accountId = account.path("accountId").asText();

            if (accountId.startsWith("gp_gb_wallet_")) {
                globalGpGbWallet = accountId;
            } else if (accountId.startsWith("gp_gb_bank_")) {
                globalGpGbBank = accountId;
            } else if (accountId.startsWith("gp_gb_tds_")) {
                globalGpGbTds = accountId;
            }
        }

        System.out.println("✅ Extracted Accounts:");
        System.out.println("Wallet: " + globalGpGbWallet);
        System.out.println("Bank: " + globalGpGbBank);
        System.out.println("TDS: " + globalGpGbTds);

        return response;
    }

    private static String generateRandomGpId() {
        Random random = new Random();
        StringBuilder idBuilder = new StringBuilder();

        // Generate 5 random digits (first digit not zero)
        idBuilder.append(random.nextInt(9) + 1); // First digit: 1–9
        for (int i = 1; i < 5; i++) {
            idBuilder.append(random.nextInt(10)); // Remaining digits: 0–9
        }

        return idBuilder.toString();
    }

    public static Map<String, Object> filterGPKeys(Map<String, Object> requestBody) {
        Set<String> allowedKeys = Set.of("gpId", "isPosp", "metadata");
        return requestBody.entrySet()
                .stream()
                .filter(entry -> allowedKeys.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    // Dummy method to show the validation step
    public void validateGPDatabaseEntries(String gpId) {
        String sql = "SELECT * FROM accounts WHERE account_owner_id = ?";
        List<Map<String, Object>> accountRecords = jdbcTemplate.queryForList(sql, gpId);

        System.out.println("Accounts found: " + accountRecords.size());

        if (accountRecords.isEmpty()) {
            throw new IllegalStateException("❌ Error: No accounts found for gpId " + gpId);
        }

        if (accountRecords.size() < 3) {
            throw new IllegalStateException("❌ Error: Expected at least 3 accounts for gpId " + gpId + ", but found only " + accountRecords.size());
        }

        for (Map<String, Object> record : accountRecords) {
            String accountId = (String) record.get("account_id");
            String accountTypeIdStr = record.get("account_type_id").toString();
            int accountTypeId = Integer.parseInt(accountTypeIdStr);

            if (accountId.startsWith("gp_gb_wallet") && accountTypeId == 1) {
                globalGpGbWallet = accountId;
                System.out.println("✅ GP General Balance Wallet account created: " + accountId);
            } else if(accountId.startsWith("gp_gb_bank") && accountTypeId == 1) {
                System.out.println("✅ GP Bank account created: " + accountId);
            } else if(accountId.startsWith("gp_gb_tds") && accountTypeId == 1) {
                System.out.println("✅ GP tds account created: " + accountId);
            } else if (accountId.startsWith("gp_gc_wallet") && accountTypeId == 2) {
                System.out.println("✅ GP Gold Coin Wallet account created: " + accountId);
            } else if (accountId.startsWith("gp_sc_wallet") && accountTypeId == 3) {
                System.out.println("✅ GP Scratch Card Wallet account created: " + accountId);
            }

        }
    }

    // Dummy method to simulate a DB connection check
    public void testDBConnection() {
        String testSql = "SELECT NOW() as currentTime";

        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(testSql);
            System.out.println("✅ DB connected! Current DB time: " + result.get("currentTime"));
        } catch (Exception e) {
            System.err.println("❌ DB connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

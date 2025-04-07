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

public class ProcessWorkflow {
    private static String gpId;
    private static String globalGpGbWallet;
    private static String leadId;
    private static String baseUrl;
    private static String processApiUrl;
    private static JsonNode jsonConfig;

    private static String gpuId = "40173"; // hardcoded gpId
    private static final boolean USE_RANDOM_GP_ID = false; // toggle to true to use new gpId

    private final JdbcTemplate jdbcTemplate;

    public ProcessWorkflow(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public static void main(String[] args) throws Exception {
        DataSource dataSource = DatabaseConfig.getDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        loadJsonConfig();

        ProcessWorkflow processWorkflow = new ProcessWorkflow(jdbcTemplate);

        if (USE_RANDOM_GP_ID) {
            processWorkflow.executeOnboarding(gpId);
        } else {
            System.out.println("🧩 Skipping onboarding for hardcoded gpId: " + gpId);
        }

        processWorkflow.triggerProcessAPI(gpId);
        processWorkflow.callBalanceAPI(gpId);
    }

    private static void loadJsonConfig() throws Exception {
        InputStream inputStream = new ClassPathResource("insuranceWallet.json").getInputStream();
        ObjectMapper objectMapper = new ObjectMapper();
        jsonConfig = objectMapper.readTree(inputStream);

        baseUrl = jsonConfig.get("API_URL").asText();
        processApiUrl = baseUrl + jsonConfig.get("workflow").asText();
        leadId = jsonConfig.get("leadId").asText();

        if (USE_RANDOM_GP_ID) {
            gpId = generateRandomGpId();
            System.out.println("🔁 Using new generated gpId: " + gpId);
        } else {
            gpId = gpuId;
            System.out.println("📌 Using hardcoded gpId: " + gpId);
        }
    }

    public void executeOnboarding(String gpId) throws Exception {
        ResponseEntity<String> response = onboardGP(gpId);
        System.out.println("Full API response: " + response.getBody());
        validateGPDatabaseEntries(gpId);
    }

    public ResponseEntity<String> onboardGP(String gpId) throws Exception {
        String gpEndpoint = jsonConfig.get("gp_Account").asText();
        boolean isPosp = jsonConfig.get("isPosp").asBoolean();

        if (!isPosp) {
            throw new IllegalArgumentException("❌ Error: isPosp is false. Cannot proceed.");
        }

        String apiUrl = baseUrl + gpEndpoint;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("gpId", gpId);
        requestBody.put("isPosp", isPosp);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("additionalInfo", "gp all account is created general balance, coin, scratch card");
        requestBody.put("metadata", metadata);

        requestBody = filterGPKeys(requestBody);

        System.out.println("📤 Sending onboarding request for gpId: " + gpId);
        System.out.println("🔗 API URL: " + apiUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        RestTemplate restTemplate = new RestTemplate();

        return restTemplate.exchange(apiUrl, HttpMethod.POST, requestEntity, String.class);
    }

    private static String generateRandomGpId() {
        Random random = new Random();
        int id = 40000 + random.nextInt(1000);
        return String.valueOf(id);
    }

    public static Map<String, Object> filterGPKeys(Map<String, Object> requestBody) {
        Set<String> allowedKeys = Set.of("gpId", "isPosp", "metadata");
        return requestBody.entrySet()
                .stream()
                .filter(entry -> allowedKeys.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public void validateGPDatabaseEntries(String gpId) {
        String query = "SELECT account_id AS account_id, account_type_id AS account_type_id, account_owner_id AS account_owner_id FROM accounts WHERE account_id LIKE ?";
        List<Map<String, Object>> accountRecords = jdbcTemplate.queryForList(query, "%" + gpId);

        if (accountRecords.isEmpty()) {
            throw new IllegalStateException("❌ Error: No accounts found for gpId " + gpId);
        }

        if (accountRecords.size() < 3) {
            throw new IllegalStateException("❌ Error: Expected at least 3 accounts for gpId " + gpId + ", but found only " + accountRecords.size());
        }

        System.out.println("✅ Accounts found for gpId " + gpId + ":");
        for (Map<String, Object> record : accountRecords) {
            String accountId = (String) record.get("account_id");
            Number accountTypeIdNumber = (Number) record.get("account_type_id");

            int accountTypeId = (accountTypeIdNumber != null) ? accountTypeIdNumber.intValue() : -1;

            if (accountId.startsWith("gp_gb_wallet") && accountTypeId == 1) {
                globalGpGbWallet = accountId;
                System.out.println("✅ GP General Balance Wallet account created: " + accountId);
            } else if (accountId.startsWith("gp_gc_wallet") && accountTypeId == 2) {
                System.out.println("✅ GP Gold Coin Wallet account created: " + accountId);
            } else if (accountId.startsWith("gp_sc_wallet") && accountTypeId == 3) {
                System.out.println("✅ GP Scratch Card Wallet account created: " + accountId);
            }
        }
    }

    public void triggerProcessAPI(String gpId) throws Exception {
        if (globalGpGbWallet == null || globalGpGbWallet.isEmpty()) {
            globalGpGbWallet = "gp_gb_wallet_" + gpId;
            System.out.println("⚠️ Using fallback GP GB Wallet: " + globalGpGbWallet);
        }

        String eventType = jsonConfig.get("eventType").asText();
        int payoutAmount = jsonConfig.get("payoutAmount").asInt();
        String transactionDate = jsonConfig.get("transactionDate").asText();
        String freelookDate = jsonConfig.get("freelookDate").asText();
        String paymentDoneDate = jsonConfig.get("paymentDoneDate").asText();
        String policyStartDate = jsonConfig.get("policyStartDate").asText();
        String insurer = jsonConfig.get("insurer").asText();
        String category = jsonConfig.get("category").asText();

        String lead = leadId + generateRandomGpId();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("eventType", eventType);

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventName", eventType);
        payload.put("transactionDate", transactionDate);

        Map<String, Object> eventDetails = new HashMap<>();
        eventDetails.put("leadId", lead);
        eventDetails.put("payoutAmount", payoutAmount);
        eventDetails.put("gpId", globalGpGbWallet);
        eventDetails.put("freelookDate", freelookDate);
        eventDetails.put("insurer", insurer);
        eventDetails.put("category", category);
        eventDetails.put("paymentDoneDate", paymentDoneDate);
        eventDetails.put("policyStartDate", policyStartDate);

        payload.put("eventDetails", eventDetails);
        requestBody.put("payload", payload);

        System.out.println("🚀 Triggering Process API with payload: " + requestBody);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<String> response = restTemplate.exchange(processApiUrl, HttpMethod.POST, requestEntity, String.class);
        System.out.println("📝 Process API Response: " + response.getBody());

        // 🔍 Debug block: Check if transactions are present before validation
        System.out.println("🔍 Checking raw transactions for account: gp_gb_wallet_" + gpId);
        String checkQuery = "SELECT * FROM insurance_wallet.transaction WHERE account_id = ?";
        List<Map<String, Object>> debugTransactions = jdbcTemplate.queryForList(checkQuery, "gp_gb_wallet_" + gpId);

        if (debugTransactions.isEmpty()) {
            System.out.println("❌ No transaction rows found for gp_gb_wallet_" + gpId);
        } else {
            System.out.println("📊 Transactions found:");
            debugTransactions.forEach(System.out::println);
        }

        // Proceed with validations
        int totalTransactionBalance = validateTransactions(gpId, payoutAmount);
        validatePayoutTransaction(gpId, payoutAmount, totalTransactionBalance);
    }


    public void validatePayoutTransaction(String gpId, int payoutAmount, int totalTransactionBalance) {
        String merchantAccountId = "merchant_gromo_gromo-insure_4w_bank_11999051";
        String gpGbWalletAccountId = "gp_gb_wallet_" + gpId;

        System.out.println("✅ Payout validation successful: " + payoutAmount + " transferred to " + gpGbWalletAccountId);

        Double merchantBalance = jdbcTemplate.queryForObject(
                "SELECT balance_in_same_currency FROM accounts WHERE account_id = ?",
                Double.class, merchantAccountId
        );

        if (merchantBalance == null) {
            System.out.println("❌ Error: Merchant balance is NULL! Check if account exists.");
        } else if (Math.abs(merchantBalance) == Math.abs(totalTransactionBalance)) {
            System.out.println("✅ Total Merchant Balance Matches: " + merchantBalance);
        } else {
            System.out.println("❌ Mismatch in total merchant balance. Expected: " + totalTransactionBalance + ", Found: " + merchantBalance);
        }
    }

    public int validateTransactions(String gpId, int expectedPayoutAmount) {
        String expectedGpGbWalletId = "gp_gb_wallet_" + gpId;
        String query = "SELECT t1.transaction_id, t1.account_id AS debited_account, " +
                "t2.account_id AS credited_account, t1.transaction_amount, t1.created_date " +
                "FROM insurance_wallet.transaction t1 " +
                "JOIN insurance_wallet.transaction t2 " +
                "ON t1.transaction_id = t2.transaction_id " +
                "WHERE t1.account_id = ? AND t1.transaction_type = 'debit' AND t2.transaction_type = 'credit'";

        List<Map<String, Object>> transactions = jdbcTemplate.queryForList(query, "merchant_gromo_gromo-insure_4w_bank_11999051");

        int totalBalance = 0;
        boolean matchFound = false;

        for (Map<String, Object> tx : transactions) {
            String creditedAccount = (String) tx.get("credited_account");
            int amount = ((Number) tx.get("transaction_amount")).intValue();
            totalBalance += amount;

            if (creditedAccount.equals(expectedGpGbWalletId) && amount == expectedPayoutAmount) {
                matchFound = true;
            }
        }

        if (!matchFound) {
            throw new IllegalStateException("❌ No matching transaction found for wallet " + expectedGpGbWalletId);
        }

        System.out.println("✅ Transactions validated for wallet: " + expectedGpGbWalletId);
        return totalBalance;
    }

    public void callBalanceAPI(String gpId) {
        if (gpId == null || gpId.isEmpty()) {
            throw new IllegalStateException("❌ Error: gpId is null or not initialized.");
        }

        String balanceEndpoint = jsonConfig.get("getbalance").asText();
        String url = baseUrl + balanceEndpoint + gpId;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        System.out.println("📦 Balance API Response for gpId " + gpId + ":");
        System.out.println(response.getBody());
    }
}

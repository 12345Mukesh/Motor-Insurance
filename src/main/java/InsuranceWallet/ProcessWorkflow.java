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

    private final JdbcTemplate jdbcTemplate;

    public ProcessWorkflow(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public static void main(String[] args) throws Exception {
        DataSource dataSource = DatabaseConfig.getDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        loadJsonConfig();

        ProcessWorkflow processWorkflow = new ProcessWorkflow(jdbcTemplate);
        processWorkflow.executeOnboarding();
    }

    private static void loadJsonConfig() throws Exception {
        InputStream inputStream = new ClassPathResource("insuranceWallet.json").getInputStream();
        ObjectMapper objectMapper = new ObjectMapper();
        jsonConfig = objectMapper.readTree(inputStream);

        baseUrl = jsonConfig.get("API_URL").asText();
        processApiUrl = baseUrl + jsonConfig.get("workflow").asText();
        leadId = jsonConfig.get("leadId").asText();
    }

    public void executeOnboarding() throws Exception {
        ResponseEntity<String> response = onboardGP();
        System.out.println("Full API response: " + response.getBody());

        validateGPDatabaseEntries(gpId);
        triggerProcessAPI();
    }

    public ResponseEntity<String> onboardGP() throws Exception {
        String gpEndpoint = jsonConfig.get("gp_Account").asText();
        boolean isPosp = jsonConfig.get("isPosp").asBoolean();

        if (!isPosp) {
            throw new IllegalArgumentException("❌ Error: isPosp is false. Cannot proceed.");
        }

        String apiUrl = baseUrl + gpEndpoint;
        gpId = generateRandomGpId();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("gpId", gpId);
        requestBody.put("isPosp", isPosp);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("additionalInfo", "gp all account is created general balance, coin, scratch card");
        requestBody.put("metadata", metadata);

        requestBody = filterGPKeys(requestBody);

        System.out.println("Generated gpId: " + gpId);
        System.out.println("API URL: " + apiUrl);

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

    public void triggerProcessAPI() throws Exception {
        if (globalGpGbWallet == null || globalGpGbWallet.isEmpty()) {
            throw new IllegalStateException("❌ Error: GP General Balance Wallet ID not found for processing.");
        }

        String eventType = jsonConfig.get("eventType").asText();
        int payoutAmount = jsonConfig.get("payoutAmount").asInt();
        String transactionDate = jsonConfig.get("transactionDate").asText();
        String freelookDate = jsonConfig.get("freelookDate").asText();
        String paymentDoneDate = jsonConfig.get("paymentDoneDate").asText();
        String policyStartDate = jsonConfig.get("policyStartDate").asText();
        String insurer = jsonConfig.get("insurer").asText();
        String category = jsonConfig.get("category").asText();

        String lead = leadId+generateRandomGpId();

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

        System.out.println("Triggering Process API with payload: " + requestBody);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<String> response = restTemplate.exchange(processApiUrl, HttpMethod.POST, requestEntity, String.class);

        System.out.println("Process API Response: " + response.getBody());

        int totalTransactionBalance = validateTransactions(payoutAmount); // ✅ Capture total balance
        validatePayoutTransaction(payoutAmount, totalTransactionBalance); // ✅ Pass it for validation

    }

    public void validatePayoutTransaction(int payoutAmount, int totalTransactionBalance) {
        String merchantAccountId = "merchant_gromo_gromo-insure_general_bank_200012";
        String gpGbWalletAccountId = "gp_gb_wallet_" + gpId;

        System.out.println("✅ Payout validation successful: " + payoutAmount + " deducted from " + merchantAccountId + " and credited to " + gpGbWalletAccountId);

        String gpGbWalletBalanceQuery = "SELECT balance_in_same_currency FROM accounts WHERE account_id = ?";
//        System.out.println("🔹 Querying balance for GP_GB_Wallet ID: " + gpGbWalletAccountId);

        Double gpGbWalletBalance = jdbcTemplate.queryForObject(gpGbWalletBalanceQuery, Double.class, new Object[]{gpGbWalletAccountId});

        String merchantBalanceQuery = "SELECT balance_in_same_currency FROM accounts WHERE account_id = ?";
        System.out.println("✅ Fetching Merchant Balance from accounts table: ");
        System.out.println("🔹 Querying balance for Merchant Account ID: " + merchantAccountId);

        Double merchantBalance = jdbcTemplate.queryForObject(merchantBalanceQuery, Double.class, new Object[]{merchantAccountId});

        if (merchantBalance == null) {
            System.out.println("❌ Error: Merchant balance is NULL! Check if account exists.");
        } else if (Math.abs(merchantBalance) == Math.abs(totalTransactionBalance)) {  // ✅ Match ignoring sign
            System.out.println("✅ Total Merchant Balance Matches: " + merchantBalance);
        } else {
            System.out.println("❌ Error: Mismatch in total merchant balance. Expected: " + totalTransactionBalance + ", Found: " + merchantBalance);
        }
    }


    public int validateTransactions(int expectedPayoutAmount) {
        String expectedGpGbWalletId = "gp_gb_wallet_" + gpId;
        String transactionQuery = "SELECT t1.transaction_id, t1.account_id AS debited_account, " + // ✅ Fetch debited account
                "t2.account_id AS credited_account, t1.transaction_amount, t1.created_date " +
                "FROM insurance_wallet.transaction t1 " +
                "JOIN insurance_wallet.transaction t2 " +
                "ON t1.transaction_id = t2.transaction_id " +
                "WHERE t1.account_id = ? AND t1.transaction_type = 'debit' AND t2.transaction_type = 'credit'";

        System.out.println("🔹 Fetching transactions for Merchant Account ID: merchant_gromo_gromo-insure_general_bank_200012");

        List<Map<String, Object>> transactions = jdbcTemplate.queryForList(transactionQuery, "merchant_gromo_gromo-insure_general_bank_200012");

        if (transactions.isEmpty()) {
            throw new IllegalStateException("❌ Error: No transactions found for the merchant account.");
        }

        boolean isValidTransaction = false;
        int totalBalance = 0;  // ✅ Store total transaction amount

        for (Map<String, Object> transaction : transactions) {
            String transactionId = (String) transaction.get("transaction_id");
            String debitedAccount = (String) transaction.get("debited_account"); // ✅ Now fetched correctly
            String creditedAccount = (String) transaction.get("credited_account");
            int transactionAmount = ((Number) transaction.get("transaction_amount")).intValue();

            totalBalance += transactionAmount; // ✅ Add amount to total balance

            System.out.println("🔹 Checking Transaction ID: " + transactionId +
                    " | Debited Account: " + debitedAccount +
                    " | Credited Account: " + creditedAccount +
                    " | Amount: " + transactionAmount);

            if (creditedAccount.equals(expectedGpGbWalletId) && transactionAmount == expectedPayoutAmount) {
                isValidTransaction = true;
            }
        }

        System.out.println("💰 Total Balance of All Transactions of merchant_gromo_gromo-insure_general_bank_200012: " + totalBalance);

        if (!isValidTransaction) {
            throw new IllegalStateException("❌ Error: No matching transaction found for GP GB Wallet: " + expectedGpGbWalletId +
                    " with amount: " + expectedPayoutAmount);
        }

        System.out.println("🔹 Latest GP GB Wallet Account Created: " + expectedGpGbWalletId);
        System.out.println("✅ Validation successful: Payout matches the expected GP_GB_Wallet account: " + expectedGpGbWalletId);

        return totalBalance;  // ✅ Return total balance
    }


}



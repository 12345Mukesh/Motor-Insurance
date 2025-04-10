package InsuranceWallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class ProcessWorkflow {
    private static String gpId;
    private static String globalGpGbWallet;
    private static String leadId;
    private static String baseUrl;
    private static String processApiUrl;
    private static JsonNode jsonConfig;
    private BigDecimal matchedTransactionAmount;

    private String matchedTransactionId;

    private static String gpuId = "40913";
    private static final boolean USE_RANDOM_GP_ID = true;

    public static String merchantAccountId = "merchant_gromo_gromo-insure_cancel_bank_xxzbp30a5rdpz";

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
        processWorkflow.triggerCancelWorkflow();
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

        for (Map<String, Object> record : accountRecords) {
            String accountId = (String) record.get("account_id");
            String accountTypeIdStr = record.get("account_type_id").toString();
            int accountTypeId = Integer.parseInt(accountTypeIdStr);

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
        BigDecimal payoutAmount = new BigDecimal(jsonConfig.get("payoutAmount").asText());
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
        eventDetails.put("gpId", gpId);
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

        System.out.println("🔍 Checking raw transactions for account: gp_gb_wallet_" + gpId);
        String checkQuery = "SELECT * FROM insurance_wallet.transaction WHERE account_id = ?";
        List<Map<String, Object>> debugTransactions = jdbcTemplate.queryForList(checkQuery, "gp_gb_wallet_" + gpId);

        if (debugTransactions.isEmpty()) {
            System.out.println("❌ No transaction rows found for gp_gb_wallet_" + gpId);
        } else {
            System.out.println("📊 Transactions found:");
            debugTransactions.forEach(System.out::println);
        }

        // ✅ Validate once and store matched data
        matchedTransactionId = validateTransactions(gpId, payoutAmount);
        matchedTransactionAmount = payoutAmount;
    }

    private void prettyPrintTransaction(Map<String, Object> tx) {
        System.out.println(tx);
    }

    private void printEssentialFields(String label, Map<String, Object> tx) {
        System.out.println("[" + label + "]");
        System.out.println("Txn ID        : " + tx.get("transaction_id"));
        System.out.println("Account ID    : " + tx.get("account_id"));
        System.out.println("Merchant Txn  : " + tx.get("merchant_transaction_id"));
        System.out.println("Amount        : " + tx.get("transaction_amount"));
        System.out.println("Type          : " + tx.get("transaction_type"));
        System.out.println("Status        : " + tx.get("status"));
        System.out.println("Created Date  : " + tx.get("created_date"));
    }

    public String validateTransactions(String gpId, BigDecimal expectedPayoutAmount) {
        String walletAccountId = "gp_gb_wallet_" + gpId;
        System.out.println("wallet Account Id: " + walletAccountId);

        System.out.println("📢 Confirming if Process API created wallet transactions...");
        List<Map<String, Object>> debugTransactions = jdbcTemplate.queryForList(
                "SELECT * FROM insurance_wallet.transaction WHERE account_id = ?",
                "gp_gb_wallet_" + gpId
        );

        if (debugTransactions.isEmpty()) {
            System.out.println("❌ No wallet transactions found for gpId: " + gpId);
        } else {
            System.out.println("✅ Found wallet transactions:");
            debugTransactions.forEach(System.out::println);
        }

        String query = "SELECT transaction_id, account_id, transaction_type, transaction_amount, " +
                "event_id, status, created_date " +
                "FROM insurance_wallet.transaction " +
                "WHERE account_id IN (?, ?)";

        List<Map<String, Object>> transactions = jdbcTemplate.queryForList(query, walletAccountId, merchantAccountId);

        List<Map<String, Object>> walletCredits = new ArrayList<>();
        List<Map<String, Object>> walletDebits = new ArrayList<>();
        List<Map<String, Object>> merchantCredits = new ArrayList<>();
        List<Map<String, Object>> merchantDebits = new ArrayList<>();

        for (Map<String, Object> tx : transactions) {
            String accId = (String) tx.get("account_id");
            String type = (String) tx.get("transaction_type");

            if (accId.equals(walletAccountId)) {
                if ("credit".equalsIgnoreCase(type)) walletCredits.add(tx);
                else if ("debit".equalsIgnoreCase(type)) walletDebits.add(tx);
            } else if (accId.equals(merchantAccountId)) {
                if ("credit".equalsIgnoreCase(type)) merchantCredits.add(tx);
                else if ("debit".equalsIgnoreCase(type)) merchantDebits.add(tx);
            }
        }

        System.out.println("🟦 -- GP/GB Wallet Transactions --");
        walletCredits.forEach(this::prettyPrintTransaction);
        walletDebits.forEach(this::prettyPrintTransaction);

        System.out.println("🟥 -- Merchant Transactions --");
        merchantCredits.forEach(this::prettyPrintTransaction);
        merchantDebits.forEach(this::prettyPrintTransaction);

        System.out.println("✅ -- Matched Transactions (Wallet Credit ↔ Merchant Debit) --");

        boolean matchedAmountFound = false;

        for (Map<String, Object> walletTx : walletCredits) {
            String walletEventId = (String) walletTx.get("event_id");

            for (Map<String, Object> merchantTx : merchantDebits) {
                String merchantEventId = (String) merchantTx.get("event_id");

                boolean matchedByEventId = walletEventId != null && walletEventId.equals(merchantEventId);

                BigDecimal walletAmount = new BigDecimal(walletTx.get("transaction_amount").toString());
                BigDecimal merchantAmount = new BigDecimal(merchantTx.get("transaction_amount").toString());

                String walletTime = walletTx.get("created_date").toString();
                String merchantTime = merchantTx.get("created_date").toString();

                boolean matchedByAmountAndTime = walletAmount.equals(merchantAmount) && walletTime.equals(merchantTime);

                if (matchedByEventId || matchedByAmountAndTime) {
                    System.out.println("------ Matched Pair ------");
                    printEssentialFields("Wallet Credit", walletTx);
                    printEssentialFields("Merchant Debit", merchantTx);

                    if (walletAmount.compareTo(expectedPayoutAmount) == 0) {
                        matchedAmountFound = true;
                        matchedTransactionId = (String) walletTx.get("transaction_id");
                        matchedTransactionAmount = walletAmount;
                    }
                }

            }
        }

        if (!matchedAmountFound || matchedTransactionId == null) {
            throw new IllegalStateException("❌ No matching transaction found with amount: " + expectedPayoutAmount);
        }

        int totalCredited = walletCredits.stream()
                .mapToInt(tx -> ((Number) tx.get("transaction_amount")).intValue())
                .sum();

        System.out.println("✅ Total credited to wallet: ₹" + totalCredited);
        System.out.println("🎯 Matched Transaction ID: " + matchedTransactionId);
        System.out.println("💸 Matched Transaction Amount: ₹" + matchedTransactionAmount);

        return matchedTransactionId;

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

    public void triggerCancelWorkflow() {
        String gpId = USE_RANDOM_GP_ID ? generateRandomGpId() : gpuId;

        if (matchedTransactionAmount == null || matchedTransactionId == null) {
            throw new IllegalStateException("❌ No matched transaction data found. Run triggerProcessAPI() first.");
        }

        Map<String, Object> eventDetails = new LinkedHashMap<>();
        eventDetails.put("gpId", gpId);
        eventDetails.put("originalTransactionId", matchedTransactionId);
        eventDetails.put("cancellationType", "USER_INITIATED");

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("eventName", "cancel2w");
        requestBody.put("eventDetails", eventDetails);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<String> response = restTemplate.exchange(processApiUrl, HttpMethod.POST, entity, String.class);

        System.out.println("📤 Cancellation API called for gpId: " + gpId);
        System.out.println("🔁 Original Transaction ID: " + matchedTransactionId);
        System.out.println("💰 Matched Amount Used: ₹" + matchedTransactionAmount);
        System.out.println("📨 API Response:");
        System.out.println(response.getBody());
    }

}






package InsuranceWallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ProcessWorkflow {
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
    private static String gpId = "LI5E8809";
    private static String lead;
    private static final boolean USE_RANDOM_GP_ID = false;

    private final JdbcTemplate jdbcTemplate;

    private static String dynamicLeadId;
    private static String dynamicRegNum;
    private static String dynamicPolicyNumber;

    private static String payoutAddition;


    public ProcessWorkflow(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public static void main(String[] args) throws Exception {
        // Get DataSource from DatabaseConfig
        DataSource dataSource = DatabaseConfig.getDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        // Test DB connection
        new ProcessWorkflow(jdbcTemplate).testDBConnection();  // Call this to test the connection

        // Load the configuration from JSON
        loadJsonConfig();

        // Create an instance of ProcessWorkflow with the jdbcTemplate
        ProcessWorkflow processWorkflow = new ProcessWorkflow(jdbcTemplate);

        // Execute onboarding process if USE_RANDOM_GP_ID is true
        if (USE_RANDOM_GP_ID) {
            processWorkflow.executeOnboarding(gpId);
        } else {
            System.out.println("🧩 Skipping onboarding for hardcoded gpId: " + gpId);
        }


     //   processWorkflow.triggerProcessAPI(gpId);
       // processWorkflow.payoutAddition(gpId);

     /*
     //------------------ If want to hold the withdrawal for some particular time and make another operation-----//
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> {
            try {
                processWorkflow.processWithdrawalWorkflow(gpId, jsonConfig);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        try {
            future.get(10, TimeUnit.SECONDS); // Try to wait for 10 seconds
        } catch (TimeoutException e) {
            System.out.println("⏰ withdrawal took too long, moving ahead with other APIs...");
            // ⚡ DO NOT cancel the future, just continue
            // future.cancel(true);  <-- REMOVE this
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } finally {
            // ⚡ Do not shutdown executor immediately if you want background task to continue
            // executor.shutdownNow(); <-- REMOVE this
        }


        // (Optional) later, gracefully shutdown executor after all tasks are done
        executor.shutdown();
    */


// Continue with other APIs immediately
        processWorkflow.triggerProcessAPI(gpId);
        processWorkflow.callBalanceAPI(gpId);
       processWorkflow.payoutAddition(gpId);
       processWorkflow.triggerReversalAPI(gpId);
//        processWorkflow.triggerReversalAPI(gpId);
        processWorkflow.callBalanceAPI(gpId);
        processWorkflow.triggerCancelWorkflow();
        processWorkflow.payoutAddition(gpId);
        processWorkflow.processWithdrawalWorkflow(gpId,jsonConfig);
processWorkflow.processTransactions();
processWorkflow.callBalanceAPI(gpId);
//        System.out.println("<----------------------------------------------------------------------All transactions list -------------------------------------------------------------------------------->");
//
//        processWorkflow.processTransactions();
//         processWorkflow.fetchTransactionHistory(gpId);

    }

    private static String generateRandomGpId(int digits) {
        if (digits < 1) {
            throw new IllegalArgumentException("Number of digits must be at least 1");
        }

        Random random = new Random();
        StringBuilder idBuilder = new StringBuilder();

        // First digit (cannot be zero)
        idBuilder.append(random.nextInt(9) + 1);

        // Remaining digits
        for (int i = 1; i < digits; i++) {
            idBuilder.append(random.nextInt(10));
        }

        return idBuilder.toString();
    }

    private static String generateRandomAlphabet(int length) {
        if (length < 1) {
            throw new IllegalArgumentException("Length must be at least 1");
        }

        Random random = new Random();
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < length; i++) {
            char randomChar = (char) ('A' + random.nextInt(26)); // A-Z
            builder.append(randomChar);
        }

        return builder.toString();
    }



    private static void loadJsonConfig() throws Exception {
        InputStream inputStream = new ClassPathResource("insuranceWallet.json").getInputStream();
        ObjectMapper objectMapper = new ObjectMapper();
        jsonConfig = objectMapper.readTree(inputStream);

        baseUrl = jsonConfig.get("API_URL").asText();
        processApiUrl = baseUrl + jsonConfig.get("workflow").asText();
        leadId = jsonConfig.get("leadId").asText();
        payoutAddition = baseUrl+jsonConfig.get("payoutAddition").asText();
        reversalApiUrl = baseUrl + jsonConfig.get("reversalApiUrl").asText();
        String registrationNumber = jsonConfig.get("registrationNumber").asText();

        // Generate dynamic fields
         dynamicLeadId = "GI-" + generateRandomGpId(7);
         dynamicRegNum = registrationNumber+generateRandomAlphabet(2) + generateRandomGpId(4);
         dynamicPolicyNumber = "POLICY" + generateRandomAlphabet(4)+generateRandomGpId(7);



        if (USE_RANDOM_GP_ID) {
            gpId = generateRandomAlphabet(3)+generateRandomGpId(5);
            System.out.println("🔁 Using new generated gpId: " + gpId);
        } else {
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
        String posId = jsonConfig.get("posId").asText();
        String gpname = jsonConfig.get("gpname").asText();
        String gpemail = jsonConfig.get("gpemail").asText();
        String gpphone = jsonConfig.get("gpphone").asText();


        if (!isPosp) {
            throw new IllegalArgumentException("❌ Error: isPosp is false. Cannot proceed.");
        }

        String apiUrl = baseUrl + gpEndpoint;
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("gpId", gpId);
        requestBody.put("isPosp", isPosp);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("posId", posId);
        metadata.put("additionalInfo", "gp all account is created general balance, coin, scratch card");
        metadata.put("name", gpname);
        metadata.put("email", gpemail);
        metadata.put("phone", gpphone);


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




    public static Map<String, Object> filterGPKeys(Map<String, Object> requestBody) {
        Set<String> allowedKeys = Set.of("gpId", "isPosp", "metadata");
        return requestBody.entrySet()
                .stream()
                .filter(entry -> allowedKeys.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }


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






    public void triggerProcessAPI(String gpId) throws Exception {
        if (globalGpGbWallet == null || globalGpGbWallet.isEmpty()) {
            globalGpGbWallet = "gp_gb_wallet_" + gpId;
            System.out.println("⚠️ Using fallback GP GB Wallet: " + globalGpGbWallet);
        }

        // Load values from jsonConfig
        String eventType = jsonConfig.get("eventType").asText();
        BigDecimal payoutAmount = new BigDecimal(jsonConfig.get("payoutAmount").asText());
        String transactionDate = jsonConfig.get("transactionDate").asText();
        String freelookEndDate = jsonConfig.get("freelookDate").asText();
         String paymentDoneDate = jsonConfig.get("paymentDoneDate").asText();
       String policyStartDate = jsonConfig.get("policyStartDate").asText();
        String insurer = jsonConfig.get("insurer").asText();
        String category = jsonConfig.get("category").asText();
        String customerName = jsonConfig.get("customerName").asText();
        String insurerLogo = jsonConfig.get("insurerLogo").asText();


        // Build request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("eventType", eventType);

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventName", eventType);
        payload.put("transactionDate", transactionDate);

        Map<String, Object> eventDetails = new HashMap<>();
        eventDetails.put("leadId", dynamicLeadId);
        eventDetails.put("gpId", gpId);
        eventDetails.put("customerName", customerName);
        eventDetails.put("registrationNumber", dynamicRegNum);
        eventDetails.put("payoutAmount", payoutAmount);
        eventDetails.put("category", category);
//        eventDetails.put("policyStartDate", policyStartDate);
//        eventDetails.put("freelookEndDate", freelookEndDate);
                eventDetails.put("paymentDoneDate", paymentDoneDate);
        eventDetails.put("insurer", insurer);
        eventDetails.put("insurerLogo", insurerLogo);
        eventDetails.put("policyNumber", dynamicPolicyNumber);

        payload.put("eventDetails", eventDetails);
        requestBody.put("payload", payload);

        System.out.println("🔖 Generated LeadId: " + dynamicLeadId);
        System.out.println("🔖 Generated RegNum: " + dynamicRegNum);
        System.out.println("🔖 Generated PolicyNumber: " + dynamicPolicyNumber);

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            String prettyPayload = mapper.writeValueAsString(requestBody);
            System.out.println("🌐 Sending POST request to URL: " + processApiUrl);
            System.out.println((prettyPayload));
            System.out.println("🚀 Triggering Process API with payload:\n" + prettyPayload);
        } catch (Exception e) {
            System.out.println("❗ Error printing payload: " + e.getMessage());
        }

        // Trigger API
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<String> response = restTemplate.exchange(processApiUrl, HttpMethod.POST, requestEntity, String.class);
        System.out.println(response);
        System.out.println("📝 Process API Response: " + response.getBody());

        // Check transaction table
        System.out.println("🔍 Checking raw transactions for account: gp_gb_wallet_" + gpId);
        String checkQuery = "SELECT * FROM insurance_wallet.transaction WHERE account_id = ?";
        List<Map<String, Object>> debugTransactions = jdbcTemplate.queryForList(checkQuery, "gp_gb_wallet_" + gpId);

        if (debugTransactions.isEmpty()) {
            System.out.println("❌ No transaction rows found for gp_gb_wallet_" + gpId);
        } else {
            System.out.println("📊 Transactions found:");
            debugTransactions.forEach(System.out::println);
        }

        // Validate transaction
        validateTransactions(gpId);
        matchedTransactionAmount = payoutAmount;
    }



    public void payoutAddition(String gpId) {
        // Extract values from jsonConfig based on gpId
        String eventType = jsonConfig.get("eventType").asText();
        BigDecimal payoutAmount = new BigDecimal(jsonConfig.get("additionAmount").asText());
        String transactionDate = jsonConfig.get("transactionDate").asText();
        String freelookEndDate = jsonConfig.get("freelookDate").asText();
        String paymentDoneDate = jsonConfig.get("paymentDoneDate").asText();
        String policyStartDate = jsonConfig.get("policyStartDate").asText();
        String insurer = jsonConfig.get("insurer").asText();
        String category = jsonConfig.get("category").asText();
        String customerName = jsonConfig.get("customerName").asText();
        String insurerLogo = jsonConfig.get("insurerLogo").asText();


        // Prepare the payout addition payload based on matching keys
        Map<String, Object> payoutRequestBody = new HashMap<>();
        payoutRequestBody.put("event_id", dynamicLeadId);  // Using dynamic lead ID here for event ID
        payoutRequestBody.put("additionReason", "Dummy reason for addition");
        payoutRequestBody.put("additionAmount", payoutAmount);
        payoutRequestBody.put("gpId", gpId);  // Same GP ID
        payoutRequestBody.put("customerName", customerName);
        payoutRequestBody.put("registrationNumber", dynamicRegNum);  // Dynamic registration number
        payoutRequestBody.put("category", category);
       payoutRequestBody.put("freelookEndDate", freelookEndDate);
        payoutRequestBody.put("insurer", insurer);
        payoutRequestBody.put("paymentDoneDate", paymentDoneDate);
        payoutRequestBody.put("policyStartDate", policyStartDate);
        payoutRequestBody.put("insurerLogo", insurerLogo);
        payoutRequestBody.put("policyNumber", dynamicPolicyNumber);  // Dynamic policy number

        // Send the payload to the payout addition API using RestTemplate
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            String prettyPayload = mapper.writeValueAsString(payoutRequestBody);
            System.out.println("🚀 Payout Addition API Request Payload:\n" + prettyPayload);

            // Now send the request using RestTemplate
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payoutRequestBody, headers);
            RestTemplate restTemplate = new RestTemplate();

            // Assuming the URL for payout addition is defined

            ResponseEntity<String> response = restTemplate.exchange(payoutAddition, HttpMethod.POST, requestEntity, String.class);
            System.out.println("📝 Payout Addition API Response: " + response.getBody());
        } catch (Exception e) {
            System.out.println("❗ Error in sending payout addition request: " + e.getMessage());
        }
    }




    public void validateTransactions(String gpId) {
        // Deriving the wallet account ID from the gpId
        String walletAccountId = "gp_gb_wallet_" + gpId.toLowerCase();  // Force gpId to lowercase
        String merchantAccountId = "merchant_gromo_gromo-insure_cancel_bank_test";  // Hardcoded for now

        System.out.println("GP/GB Wallet Account ID: " + walletAccountId);
        System.out.println("Merchant Account ID: " + merchantAccountId);

        // SQL query to fetch both wallet and merchant transactions, ordered by 'id' DESC, and limited to 5
        String query = "SELECT transaction_id, account_id, transaction_type, transaction_amount, " +
                "event_id, status " +
                "FROM insurance_wallet.transaction " +
                "WHERE account_id IN (?, ?) " +
                "ORDER BY id DESC " +  // Order by ID, DESC
                "LIMIT 5";

        // Fetch the transactions from the database
        List<Map<String, Object>> transactions = jdbcTemplate.queryForList(query, walletAccountId, merchantAccountId);

        System.out.println("Fetched Transactions: ");
        transactions.forEach(tx -> {
            System.out.println("Transaction ID: " + tx.get("transaction_id"));
            System.out.println("Account ID: " + tx.get("account_id"));
        });

        // Lists to hold wallet and merchant transactions
        List<Map<String, Object>> walletTransactions = new ArrayList<>();
        List<Map<String, Object>> merchantTransactions = new ArrayList<>();

        // Categorize transactions into wallet and merchant
        for (Map<String, Object> tx : transactions) {
            String accId = ((String) tx.get("account_id")).toLowerCase();  // Convert to lowercase before comparison
            if (accId.equals(walletAccountId)) {
                walletTransactions.add(tx);
            } else if (accId.equals(merchantAccountId)) {
                merchantTransactions.add(tx);
            }
        }

        // Debugging: Output wallet and merchant transaction details
        System.out.println("🟦 -- GP/GB Wallet Transactions --");
        walletTransactions.forEach(this::prettyPrintTransaction);

        System.out.println("🟥 -- Merchant Transactions --");
        merchantTransactions.forEach(this::prettyPrintTransaction);

        // Matching transactions by event_id
        System.out.println("✅ -- Matched Transactions (Wallet Credit ↔ Merchant Debit) --");

        boolean matchedEventFound = false;

        // Iterate through wallet transactions
        for (Map<String, Object> walletTx : walletTransactions) {
            String walletEventId = ((String) walletTx.get("event_id")).trim().replaceAll("[^\\x20-\\x7E]", "").toLowerCase();  // sanitize non-printable characters and make lowercase
            System.out.println("Checking Wallet Event ID: " + walletEventId);  // Debugging

            // Iterate through merchant transactions
            for (Map<String, Object> merchantTx : merchantTransactions) {
                String merchantEventId = ((String) merchantTx.get("event_id")).trim().replaceAll("[^\\x20-\\x7E]", "").toLowerCase();  // sanitize non-printable characters and make lowercase
                System.out.println("Checking Merchant Event ID: " + merchantEventId); // Debugging

                // Check if the event_ids match AND ensure the transaction types are complementary
                if (walletEventId.equals(merchantEventId) &&
                        "credit".equalsIgnoreCase(walletTx.get("transaction_type").toString()) &&
                        "debit".equalsIgnoreCase(merchantTx.get("transaction_type").toString())) {
                    System.out.println("Matched event IDs: " + walletEventId);
                    System.out.println("Matched wallet transaction ID: " + walletTx.get("transaction_id"));
                    System.out.println("Matched merchant transaction ID: " + merchantTx.get("transaction_id"));

                    // Pretty print matched transactions
                    System.out.println("🟦 Matched Wallet Transaction Details:");
                    prettyPrintTransaction(walletTx);
                    System.out.println("🟥 Matched Merchant Transaction Details:");
                    prettyPrintTransaction(merchantTx);

                    matchedEventFound = true;
                    return;  // Exit once a match is found
                }
            }
        }

        if (!matchedEventFound) {
            System.out.println("❌ No matching wallet and merchant transactions found for event_id.");
        }
    }

    private void prettyPrintTransaction(Map<String, Object> transaction) {
        System.out.println("Transaction ID: " + transaction.get("transaction_id"));
        System.out.println("Account ID: " + transaction.get("account_id"));
        System.out.println("Transaction Type: " + transaction.get("transaction_type"));
        System.out.println("Transaction Amount: " + transaction.get("transaction_amount"));
        System.out.println("Event ID: " + transaction.get("event_id"));
        System.out.println("Status: " + transaction.get("status"));
        System.out.println("Created Date: " + transaction.get("created_date"));
    }


    public void callBalanceAPI(String gpId) {
        // Construct the wallet account id
        String walletAccountId = "gp_gb_wallet_" + gpId;

        // Get the base URL and the balance API endpoint from the configuration
        String baseUrl = jsonConfig.get("API_URL").asText();  // Extract the base URL
        String getBalanceEndpoint = jsonConfig.get("getbalance").asText();  // Extract the getbalance endpoint

        // Check if both baseUrl and getBalanceEndpoint are not null
        if (baseUrl != null && getBalanceEndpoint != null) {
            // Construct the full URL for the balance API
            String balanceApiUrl = baseUrl + getBalanceEndpoint + gpId;  // Append the gpId to the endpoint

            // Prepare the HTTP request
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>("", headers);

            // Make the GET request to the balance API
            ResponseEntity<String> response = restTemplate.exchange(balanceApiUrl, HttpMethod.GET, entity, String.class);

            // Print the response from the balance API
            System.out.println("Balance API response for " + walletAccountId + ": " + response.getBody());
        } else {
            // If baseUrl or getBalanceEndpoint is missing, log an error message
            System.out.println("❌ API_URL or getbalance endpoint is missing in the config.");
        }
    }


    public void triggerCancelWorkflow() {
        String eventType = "cancel2w"; // or "cancel2w" based on flow


        Map<String, Object> eventDetails = new LinkedHashMap<>();
        System.out.println("Event ID: " + dynamicLeadId );
        eventDetails.put("eventId", dynamicLeadId);
        eventDetails.put("cancellationType", "USER_INITIATED");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventName", eventType);
        payload.put("eventDetails", eventDetails);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("eventType", eventType);
        requestBody.put("payload", payload);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<String> response = restTemplate.exchange(
                processApiUrl, HttpMethod.POST, entity, String.class
        );

        System.out.println("📤 Cancellation API called with eventId: " + dynamicLeadId);
        System.out.println("📨 API Response:");
        System.out.println(response.getBody());
    }

    public void triggerReversalAPI(String gpId) {
        // Extract values from jsonConfig
        String customerName = jsonConfig.get("customerName").asText();
        String category = jsonConfig.get("category").asText();
        String freelookEndDate = jsonConfig.get("freelookDate").asText();
        String insurer = jsonConfig.get("insurer").asText();
        String paymentDoneDate = jsonConfig.get("paymentDoneDate").asText();
        String policyStartDate = jsonConfig.get("policyStartDate").asText();
        String insurerLogo = jsonConfig.get("insurerLogo").asText();
        int reversalAmount = jsonConfig.get("reversalAmount").asInt();


        // Hardcoded reversal reason
        String reversalReason = "Dummy reason for reversal";

        // Build the request body
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("event_id", dynamicLeadId);
        requestBody.put("reversalReason", reversalReason);
        requestBody.put("reversalAmount", reversalAmount);
        requestBody.put("gpId", gpId);
        requestBody.put("customerName", customerName);
        requestBody.put("registrationNumber", dynamicRegNum);
        requestBody.put("category", category);
        requestBody.put("freelookEndDate", freelookEndDate);
        requestBody.put("insurer", insurer);
        requestBody.put("paymentDoneDate", paymentDoneDate);
        requestBody.put("policyStartDate", policyStartDate);
        requestBody.put("insurerLogo", insurerLogo);
        requestBody.put("policyNumber", dynamicPolicyNumber);

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            String prettyPayload = mapper.writeValueAsString(requestBody);
            System.out.println("🚀 Reversal API Payload:\n" + prettyPayload);

            // Prepare API call
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            RestTemplate restTemplate = new RestTemplate();

            // Call the reversal API
            ResponseEntity<String> response = restTemplate.exchange(
                    reversalApiUrl, HttpMethod.POST, entity, String.class
            );

            System.out.println("📤 Reversal API called successfully.");
            System.out.println("📝 API Response:\n" + response.getBody());

        } catch (Exception e) {
            System.out.println("❗ Error triggering Reversal API: " + e.getMessage());
        }
    }


    public void processWithdrawalWorkflow(String gpId, JsonNode jsonConfig) throws InterruptedException {
        String baseUrl = jsonConfig.get("API_URL").asText();
        String summaryUrl = baseUrl + jsonConfig.get("summary").asText();
        String initiateUrl = baseUrl + jsonConfig.get("initiate").asText();
        String statusUrl = baseUrl + jsonConfig.get("status").asText();

        // 🔹 Summary is optional, so ignore result
        callSummaryAPI(gpId, summaryUrl);


        // 🔸 Initiate is mandatory
        String orderId = callInitiateAPI(gpId, initiateUrl);
        if (orderId == null) return;

        // 🔸 Status depends on orderId
        pollStatusAPI(orderId, statusUrl);

    }

    private void callSummaryAPI(String gpId, String summaryUrl) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String request = "{\"gpId\":\"" + gpId + "\"}";
        HttpEntity<String> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(summaryUrl, entity, String.class);
            System.out.println("✅ Summary API Response:\n" + response.getBody());
        } catch (Exception e) {
            System.out.println("⚠️ Skipping Summary API (optional): " + e.getMessage());
        }
    }

    private String callInitiateAPI(String gpId, String initiateUrl) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ObjectMapper mapper = new ObjectMapper();

        String request = "{\"gpId\":\"" + gpId + "\"}";
        HttpEntity<String> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(initiateUrl, entity, String.class);
            System.out.println("✅ Initiate API Response:\n" + response.getBody());

            JsonNode root = mapper.readTree(response.getBody());
            JsonNode nested = root.path("data").path("data");
            String orderId = nested.path("orderId").asText();

            if (orderId == null || orderId.isEmpty()) {
                System.out.println("❌ orderId not found in Initiate API response.");
                return null;
            }

            System.out.println("✅ Extracted orderId: " + orderId);
            return orderId;

        } catch (Exception e) {
            System.out.println("❌ Error calling Initiate API: " + e.getMessage());
            return null;
        }
    }

    private void pollStatusAPI(String orderId, String statusUrl) throws InterruptedException {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ObjectMapper mapper = new ObjectMapper();

        String request = "{\"orderId\":\"" + orderId + "\"}";
        HttpEntity<String> entity = new HttpEntity<>(request, headers);
        String status = "";

        do {
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(statusUrl, entity, String.class);
                System.out.println("✅ Withdraw Status API Response:\n" + response.getBody());

                JsonNode root = mapper.readTree(response.getBody());
                status = root.path("data").path("data").path("status").asText();

                if ("INITIATED".equalsIgnoreCase(status)) {
                    System.out.println("⏳ Withdrawal status is INITIATED. Waiting 12 seconds before retry...");
                    Thread.sleep(12000);
                }

            } catch (Exception e) {
                System.out.println("❌ Error calling Withdraw Status API: " + e.getMessage());
                break;
            }
        } while ("INITIATED".equalsIgnoreCase(status));

        System.out.println("🎯 Final Withdraw Status: " + status);
    }


    public void processTransactions() {
        System.out.println("🔍 Starting verification for wallet: " + gpId);

        // Creating search pattern for LIKE query
        String searchPattern = "%" + gpId + "%";
        String sql = "SELECT * FROM insurance_wallet.transaction WHERE account_id LIKE ? ORDER BY transaction_id ASC";

        // Fetching transactions with parameterized query
        List<Map<String, Object>> transactions = jdbcTemplate.queryForList(sql, searchPattern);

        System.out.println("📄 Fetched " + transactions.size() + " transactions.");

        // Debugging: Print column names
        if (!transactions.isEmpty()) {
            Map<String, Object> firstTransaction = transactions.get(0);
            System.out.println("Available column names: " + firstTransaction.keySet());
        }

        double originalCredit = 0.0;
        double totalReversed = 0.0;
        double totalAddedBack = 0.0;
        double[] bankWithdrawalCredit = new double[1]; // use array for reference
        double[] tdsWithdrawalCredit = new double[1];
        double canceledAmount = 0.0;  // Track canceled amounts separately for final summary

        // Iterating over the fetched transactions
        for (Map<String, Object> txn : transactions) {
            System.out.println("Fetched transaction: " + txn); // Print the full transaction data for debugging

            // Check if "transaction_id" exists and is not null
            Object txnIdObject = txn.get("transaction_id");
            if (txnIdObject == null) {
                System.out.println("⚠️ Skipping transaction with null transaction_id.");
                continue;  // Skip this iteration if transaction_id is null
            }

            String txnId = txnIdObject.toString();
            System.out.println("Transaction ID: " + txnId);

            // Handle null values for other fields safely
            String type = getSafeString(txn.get("transaction_type"));  // Ensure the column name is correct
            String status = getSafeString(txn.get("status"));
            String eventType = getSafeString(txn.get("event_type"));
            String accountId = getSafeString(txn.get("account_id"));
            double amount = getSafeAmount(txn.get("transaction_amount"));

            // Print each field for debugging
            System.out.println("Transaction type: " + type);
            System.out.println("Transaction status: " + status);
            System.out.println("Event type: " + eventType);
            System.out.println("Account ID: " + accountId);
            System.out.println("Transaction amount: ₹" + amount);

            // Only proceed if 'type' is not empty
            if (type.isEmpty()) {
                System.out.println("⚠️ Skipping transaction with empty 'type'. Transaction ID: " + txnId);
                continue;  // Skip this iteration if 'type' is empty
            }

            // Process different transaction types
            if ((eventType.equals("sale2w") || eventType.equals("sale4w")) &&
                    type.equals("credit") && status.equals("success")) {
                originalCredit += amount;
                System.out.println("✅ Original Credit added: ₹" + amount);

            } else if (eventType.equals("reversal") &&
                    type.equals("debit") && status.equals("success")) {
                totalReversed += amount;
                System.out.println("🔁 Reversed amount: ₹" + amount);

            } else if (eventType.equals("addition") &&
                    type.equals("credit") && status.equals("success")) {
                totalAddedBack += amount;
                System.out.println("➕ Added back amount: ₹" + amount);

            } else if (eventType.equals("withdrawal") &&
                    type.equals("debit") && status.equals("success")) {
                fetchWithdrawalCreditsForTxn(txnId, bankWithdrawalCredit, tdsWithdrawalCredit);

            } else if ((eventType.equals("cancel4w") || eventType.equals("cancel2w")) && type.equals("debit") && status.equals("success")) {
                // Handle cancellation for both 2w and 4w
                canceledAmount += amount;  // Accumulate canceled amount
                System.out.println("❌ Canceled amount (treated as reversal): ₹" + amount);
            } else {
                System.out.printf("⚠️ Ignored transaction (type: %s, status: %s)%n", type, status);
            }
        }

        // Recalculate the final effective credit after considering cancellations
        double finalEffectiveCredit = originalCredit - totalReversed + totalAddedBack;

        // Print the final summary with updated calculation
        System.out.println("\n📊 Final Summary:");
        System.out.println("  - Original Credit (sale2w/sale4w): ₹" + originalCredit);
        System.out.println("  - Total Reversed: ₹" + totalReversed);
        System.out.println("  - Total Added Back: ₹" + totalAddedBack);
        System.out.println("  - Canceled Amount (treated as reversal): ₹" + canceledAmount);
        System.out.println("  - Bank Withdrawal Credit: ₹" + bankWithdrawalCredit[0]);
        System.out.println("  - TDS Withdrawal Credit: ₹" + tdsWithdrawalCredit[0]);
        System.out.println("  - Final Effective Credit: ₹" + finalEffectiveCredit);
    }


    // Helper method to safely handle null for String fields
    private String getSafeString(Object value) {
        return (value == null) ? "" : value.toString().toLowerCase(); // Return empty string if value is null
    }

    // Helper method to safely handle null for transaction amount (which should be a double)
    private double getSafeAmount(Object value) {
        return (value == null) ? 0.0 : Double.parseDouble(value.toString()); // Return 0.0 if value is null
    }

    private void fetchWithdrawalCreditsForTxn(String withdrawalRefId, double[] bankCredit, double[] tdsCredit) {
        String sql = "SELECT * FROM transaction " +
                "WHERE event_type = 'withdrawal' AND transaction_type = 'credit' AND status = 'success' " +
                "AND (account_id LIKE '%_bank_%' OR account_id LIKE '%_tds_%') " +
                "AND transaction_id = ?";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, withdrawalRefId);
        for (Map<String, Object> row : rows) {
            String accId = getSafeString(row.get("account_id"));
            double amt = getSafeAmount(row.get("transaction_amount"));

            if (accId.contains("_bank_")) {
                bankCredit[0] += amt;
                System.out.println("🏦 Bank Withdrawal Credit (from linked txn): ₹" + amt);
            } else if (accId.contains("_tds_")) {
                tdsCredit[0] += amt;
                System.out.println("💼 TDS Withdrawal Credit (from linked txn): ₹" + amt);
            }
        }
    }



    public void fetchTransactionHistory(String gpId) {
        String baseUrl = jsonConfig.get("API_URL").asText();
        String endpoint = jsonConfig.get("transactionHistory").asText();
        String fullUrl = baseUrl + endpoint;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("gpuid", gpId);
        requestBody.put("pageNo", 1);
        requestBody.put("filters", new HashMap<>());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        RestTemplate restTemplate = new RestTemplate();

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(fullUrl, requestEntity, String.class);
            String responseBody = response.getBody();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseBody);

            JsonNode transactions = root.path("data").path("transactions");
            if (transactions.isArray()) {
                for (JsonNode txn : transactions) {
                    System.out.println("Transactions for the lead:" +lead);
                    System.out.println("📄 Transaction:");
                    System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(txn));
                }
            } else {
                System.out.println("⚠️ No transactions found.");
            }

        } catch (Exception e) {
            System.out.println("❌ Error fetching transaction history: " + e.getMessage());
        }
    }



}

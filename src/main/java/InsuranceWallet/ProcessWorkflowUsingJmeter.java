package InsuranceWallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ProcessWorkflowUsingJmeter {
    private static String globalGpGbWallet;
    private static String leadId;
    private static String baseUrl;
    private static String processApiUrl;
    private static String reversalApiUrl;
    private static JsonNode jsonConfig;
    private BigDecimal matchedTransactionAmount;

    private static String matchedTransactionId;

    private static String gpId = "IOP12221";
    private static String lead;
    private static final boolean USE_RANDOM_GP_ID = false;

    public static String merchantAccountId = "merchant_gromo_gromo-insure_cancel_bank_bfrsnx4apv529";

    private final JdbcTemplate jdbcTemplate;


    public ProcessWorkflowUsingJmeter(JdbcTemplate jdbcTemplate) {
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

//        processWorkflow.triggerCancelWorkflow();
//        processWorkflow.callBalanceAPI(gpId);
//        processWorkflow.triggerReversalAPI();
//        processWorkflow.callBalanceAPI(gpId);
//        processWorkflow.triggerPaymentAddition();
//        processWorkflow.callBalanceAPI(gpId);
//        processWorkflow.triggerCancelWorkflow();
//        processWorkflow.callBalanceAPI(gpId);
    }


    // Entry point to start JMeter test with API URL
    public void runJMeterTestWithApiUrl(int numThreads, String gpId) {
        String jmeterPath = "/Users/mukesh.ali/Desktop/apache-jmeter-5.6.3/bin/jmeter"; // JMeter path
        String jmxFile = "/Users/mukesh.ali/Desktop/Insurance Wallet Jmeter API's/Get Balance.jmx"; // JMX file path
        String resultsPath = System.getProperty("java.io.tmpdir") + "jmeter_result_" + UUID.randomUUID() + ".jtl"; // Results file path
        String apiHost = "localhost";
        String apiPort = "3000";
        String apiPath = "/api/v1/insurance-wallet/balance?gpId=" + gpId;
        String threads = String.valueOf(numThreads);  // Threads will be passed dynamically
        String loopCount = "1";
        String rampUp = "1";

        // Create and start the threads
        List<Thread> threadList = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            String threadName = "Thread-" + (i + 1);  // Unique thread name

            Runnable task = new Runnable() {
                @Override
                public void run() {
                    try {
                        // Start JMeter process for each thread
                        ProcessBuilder processBuilder = new ProcessBuilder(
                                jmeterPath,
                                "-n",
                                "-t", jmxFile,
                                "-l", resultsPath,
                                "-JapiHost=" + apiHost,
                                "-JapiPort=" + apiPort,
                                "-JapiPath=" + apiPath,
                                "-Jthreads=" + threads,
                                "-Jloopcount=" + loopCount,
                                "-Jrampup=" + rampUp
                        );

                        processBuilder.redirectErrorStream(true);
                        Process process = processBuilder.start();

                        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        String line;
                        System.out.println("\n[JMETER OUTPUT]");
                        while ((line = reader.readLine()) != null) {
                            System.out.println(line);
                        }

                        int exitCode = process.waitFor();
                        if (exitCode == 0) {
                            System.out.println("✅ JMeter test executed successfully for " + threadName);
                            parseJMeterResults(resultsPath);  // Call parser after success
                        } else {
                            System.out.println("❌ JMeter test failed for " + threadName + ". Exit code: " + exitCode);
                        }

                    } catch (Exception e) {
                        System.err.println("❌ Error running JMeter for " + threadName + ": " + e.getMessage());
                    }
                }
            };

            // Create and start the thread
            Thread thread = new Thread(task, threadName);
            thread.start();
            threadList.add(thread);
        }

        // Wait for all threads to finish
        for (Thread thread : threadList) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("🧮 Total Threads Run: " + numThreads);
    }

    private void parseJMeterResults(String resultsPath) {
        try {
            // Read the file content
            String fileContent = Files.readString(Paths.get(resultsPath));

            // Check if the result is in XML format
            if (fileContent.trim().startsWith("<")) {
                // If the content looks like XML, parse as XML
                parseJMeterXMLResults(fileContent);
            } else {
                // Otherwise, treat it as CSV format
                parseJMeterCSVResults(resultsPath);
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to parse JMeter results: " + e.getMessage());
        } finally {
            // Clean up the temporary file after parsing
            File tempFile = new File(resultsPath);
            if (tempFile.exists()) {
                tempFile.delete();
                System.out.println("🧹 Temp results file deleted for " + resultsPath);
            }
        }
    }

    private void parseJMeterXMLResults(String xmlContent) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            InputSource inputSource = new InputSource(new StringReader(xmlContent));
            Document doc = db.parse(inputSource);
            doc.getDocumentElement().normalize();

            NodeList samples = doc.getElementsByTagName("httpSample");
            Set<String> threads = new HashSet<>(); // Store unique thread names

            System.out.println("\n[📄 JMeter XML Results]");

            // Iterate over the samples (test results)
            for (int i = 0; i < samples.getLength(); i++) {
                Element sample = (Element) samples.item(i);

                String elapsed = sample.getAttribute("t");
                String label = sample.getAttribute("lb");
                String responseCode = sample.getAttribute("rc");
                String responseMessage = sample.getAttribute("rm");
                String threadName = sample.getAttribute("tn");
                String success = sample.getAttribute("s");

                // Add the thread name to the set (unique thread names)
                threads.add(threadName);

                // Get the responseData (use extractJsonFromResponseData)
                String responseData = "";
                NodeList responseNodes = sample.getElementsByTagName("responseData");
                if (responseNodes.getLength() > 0) {
                    responseData = responseNodes.item(0).getTextContent();
                    responseData = decodeHtmlEntities(responseData);  // Decode HTML entities
                    responseData = extractJsonFromResponseData(responseData); // Extract the JSON part
                }

                // Display the results
                System.out.println("🧵 Thread: " + threadName);
                System.out.println("📦 Label: " + label);
                System.out.println("✅ Status: " + success + ", Code: " + responseCode + " (" + responseMessage + ")");
                System.out.println("⏱️ Elapsed: " + elapsed + " ms");
                System.out.println("📨 Response Body: " + responseData);

                if (isValidJson(responseData)) {
                    JSONObject jsonResponse = new JSONObject(responseData);
                    System.out.println("🧩 Parsed JSON:");
                    System.out.println(jsonResponse.toString(4));
                } else {
                    System.out.println("⚠️  Response is not valid JSON");
                }

                System.out.println("--------------------------------------------------");
            }

            // Print the total number of unique threads that ran during the test
            System.out.println("🧮 Total No of Threads Run: " + threads.size());

        } catch (Exception e) {
            System.err.println("❌ Failed to parse XML results: " + e.getMessage());
        }
    }

    private void parseJMeterCSVResults(String resultsPath) {
        try (BufferedReader br = new BufferedReader(new FileReader(resultsPath))) {
            String line;
            System.out.println("\n[📄 JMeter CSV Results]");
            while ((line = br.readLine()) != null) {
                String[] fields = line.split(",");

                // Skip lines with insufficient fields or invalid data
                if (fields.length < 10) {
                    continue; // Skip incomplete or malformed lines
                }

                try {
                    String timestamp = fields[0]; // Timestamp of the request
                    String threadName = fields[2]; // Thread name
                    String responseCode = fields[3]; // Response code (e.g., 200)
                    String responseMessage = fields[4]; // Response message (e.g., OK)
                    String requestLabel = fields[1]; // Request label (e.g., API path)
                    String elapsedTime = fields[6]; // Elapsed time (response time in ms)
                    String successStatus = fields[7]; // Success status (true/false)
                    String responseData = fields[9]; // Response data (e.g., result value)

                    // Handle non-numeric elapsed time (text or invalid data)
                    long elapsedTimeMs = -1;
                    try {
                        elapsedTimeMs = Long.parseLong(elapsedTime); // Parse elapsed time as long
                    } catch (NumberFormatException e) {
                        System.err.println("⚠️ Invalid elapsed time: " + elapsedTime + " for request: " + requestLabel);
                    }

                    // Only print valid data (non-negative elapsed time)
                    if (elapsedTimeMs != -1) {
                        System.out.println("Timestamp: " + timestamp);
                        System.out.println("Thread: " + threadName);
                        System.out.println("Response Code: " + responseCode);
                        System.out.println("Response Message: " + responseMessage);
                        System.out.println("Request Label: " + requestLabel);
                        System.out.println("Elapsed Time (ms): " + elapsedTimeMs);
                        System.out.println("Success Status: " + successStatus);
                        System.out.println("Response Data: " + responseData);
                        System.out.println("--------------------------------------------------");

                        // If the response data is JSON, parse and print it
                        if (isValidJson(responseData)) {
                            JSONObject jsonResponse = new JSONObject(responseData);
                            System.out.println("Parsed JSON Response:");
                            System.out.println(jsonResponse.toString(4)); // Pretty print JSON
                        }
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ Error processing line: " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Error reading CSV results: " + e.getMessage());
        }
    }




    private boolean isValidJson(String testString) {
        try {
            new JSONObject(testString); // Tries to parse as JSON object
            return true;
        } catch (Exception e) {
            return false; // Not a valid JSON
        }
    }




    private String decodeHtmlEntities(String input) {
        if (input == null) {
            return "";
        }
        // Handle common HTML entities
        input = input.replaceAll("&quot;", "\"")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&apos;", "'")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&copy;", "©")
                .replaceAll("&reg;", "®")
                .replaceAll("&euro;", "€")
                .replaceAll("&yen;", "¥")
                .replaceAll("&pound;", "£");
        return input;
    }


    private String extractJsonFromResponseData(String responseData) {
        // Use a regular expression to extract the JSON string (ignoring the surrounding XML tags)
        String regex = "<responseData class=\"java.lang.String\">(.*)</responseData>";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(responseData);

        if (matcher.find()) {
            String jsonString = matcher.group(1); // Get the JSON string inside the tags
            return decodeHtmlEntities(jsonString); // Decode HTML entities in the JSON string
        }

        return null; // Return null if no JSON string is found
    }


    private static void loadJsonConfig() throws Exception {
        InputStream inputStream = new ClassPathResource("insuranceWallet.json").getInputStream();
        ObjectMapper objectMapper = new ObjectMapper();
        jsonConfig = objectMapper.readTree(inputStream);

        baseUrl = jsonConfig.get("API_URL").asText();
        processApiUrl = baseUrl + jsonConfig.get("workflow").asText();
        leadId = jsonConfig.get("leadId").asText();
        reversalApiUrl = baseUrl+jsonConfig.get("reversalApiUrl").asText();

        if (USE_RANDOM_GP_ID) {
            gpId = generateRandomGpId();
            System.out.println("🔁 Using new generated gpId: " + gpId);
        } else {
//            gpId = gpuId;
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

        lead = leadId + generateRandomGpId();

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

        System.out.println("generated lead Id: " + lead);
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
//        matchedTransactionId = validateTransactions(gpId, payoutAmount);
//        matchedTransactionAmount = payoutAmount;
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
                "WHERE account_id IN (?, ?) order by id desc limit 5";

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
        String eventType = "cancel4w"; // or "cancel2w" based on flow


        Map<String, Object> eventDetails = new LinkedHashMap<>();
        eventDetails.put("eventId", lead);
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

        System.out.println("📤 Cancellation API called with eventId: " + lead);
        System.out.println("📨 API Response:");
        System.out.println(response.getBody());
    }

    public void triggerPaymentAddition() {


        int additionAmount = jsonConfig.get("additionAmount").asInt();
        String additionReason = "Customer compensation for underpayment";
        String eventType = "payment_addition";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", lead);
        payload.put("additionReason", additionReason);
        payload.put("additionAmount", additionAmount);

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

        System.out.println("📤 Payment Addition API called.");
        System.out.println("🧾 Transaction ID: " + lead);
        System.out.println("💸 Addition Amount: ₹" + additionAmount);
        System.out.println("📝 Reason: " + additionReason);
        System.out.println("📨 API Response:");
        System.out.println(response.getBody());
    }
    public void triggerReversalAPI() {


        int reversalAmount = jsonConfig.get("reversalAmount").asInt();
        String reversalReason = "Customer dispute resolution - underpayment";


        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("event_id", lead);
        requestBody.put("reversalReason", reversalReason);
        requestBody.put("reversalAmount", reversalAmount);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        RestTemplate restTemplate = new RestTemplate();


        ResponseEntity<String> response = restTemplate.exchange(
                reversalApiUrl, HttpMethod.POST, entity, String.class
        );

        System.out.println("📤 Reversal API called.");
        System.out.println("🧾 event ID: " +lead );
        System.out.println("💸 Reversal Amount: ₹" + reversalAmount);
        System.out.println("📝 Reason: " + reversalReason);
        System.out.println("📨 API Response:");
        System.out.println(response.getBody());
    }




}
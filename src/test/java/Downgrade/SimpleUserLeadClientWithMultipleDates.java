//package Downgrade;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import io.qameta.allure.Allure;
//import io.qameta.allure.testng.AllureTestNg;
//import org.json.JSONException;
//import org.json.JSONObject;
//import org.springframework.core.io.ClassPathResource;
//import org.springframework.http.*;
//import org.springframework.util.LinkedMultiValueMap;
//import org.springframework.util.MultiValueMap;
//import org.springframework.web.client.RestTemplate;
//import org.testng.TestNG;
//import org.testng.annotations.Listeners;
//import org.testng.annotations.Test;
//
//import java.io.IOException;
//import java.io.InputStream;
//import java.sql.*;
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//import java.util.*;
//
//@Listeners(AllureTestNg.class)
//public class SimpleUserLeadClientWithMultipleDates
//{
//    private static final String SIGNUP_URL = "https://auth-stg.gromo.in/oauth/otp";
//    private static final String TOKEN_URL = "https://auth-stg.gromo.in/oauth/token";
//    private static final String USER_URL = "https://api-stg.gromo.in/api/v3/users";
//    private static final String LEAD_URL = "https://api-stg.gromo.in/api/v2/miscellaneousLeads?allowSelfLead=false";
//    private static final String LEAD_UPDATE_URL = "https://api-stg.gromo.in/api/v2/miscellaneousLeadUpdated";
//
//    private static final String AUTH_HEADER = "Basic QjZCRWV2OUppSGcxaDZLQ3U2UHlCVXZ1OmU2U1lwNDBxTzZWcndmRTVuOXJhYTB4RG1oNWlGc0xQRjY4U2tqWHhCSFRUTVBWSw==";
//
//    private final RestTemplate restTemplate = new RestTemplate();
//    private static String accessToken;
//    private static String gpuid;
//    private String stagingDbUrl;
//    private String stagingDbUser;
//    private String stagingDbPass;
//    private String username;
//    private String password;
//    private String leadId;
//    private String payin = "23700";
//    private String payout ;
//    private static String today;
//
//    // Add these for GPU ID persistence and run tracking
//    private static String persistentGpuid = null;
//    private static String persistentAccessToken = null;
//    private static boolean isFirstRun = true;
//
//    private static Map<String, String> getDateWisePayoutMap() {
//        Map<String, String> payoutMap = new LinkedHashMap<>();
//        payoutMap.put("2025-04-20", "1000");
//        payoutMap.put("2025-05-21", "10000");
//        payoutMap.put("2025-06-22", "8000");
//        payoutMap.put("2025-07-23", "8000");
//        payoutMap.put("2025-08-24", "1200");
//        payoutMap.put("2025-09-25", "1000");
//        payoutMap.put("2025-10-26", "1200");
//        payoutMap.put("2025-11-27", "1500");
//        payoutMap.put("2025-12-28", "1400");
//        payoutMap.put("2026-01-28", "800");
//        payoutMap.put("2026-02-28", "700");
//        payoutMap.put("2026-03-28", "600");
//        return payoutMap;
//    }
//
//
//    public static void main(String[] args) {
//        TestNG testng = new TestNG();
//        testng.setTestClasses(new Class[] { SimpleUserLeadClientWithMultipleDates.class });
//        testng.run();
//    }
//
//
//    @Test
//    public void runLeadTest()
//    {
//        System.setProperty("allure.results.directory", "allure-results");
//        try {
//            Map<String, String> payoutMap = getDateWisePayoutMap();
//
//            for (Map.Entry<String, String> entry : payoutMap.entrySet()) {
//                String currentDate = entry.getKey();
//                String currentPayout = entry.getValue();
//
//                System.out.println("\n=== Starting execution for date: " + currentDate + " with payout: " + currentPayout + " ===");
//
//                SimpleUserLeadClientWithMultipleDates service = new SimpleUserLeadClientWithMultipleDates();
//                today = currentDate;
//                service.payout = currentPayout;  // Assign the payout for this date
//
//                // Use existing GPU ID and access token if available
//                if (persistentGpuid != null && persistentAccessToken != null) {
//                    service.gpuid = persistentGpuid;
//                    service.accessToken = persistentAccessToken;
//                    isFirstRun = false;
//                    Allure.step("Reusing Existing Gpu Id: " + service.gpuid);
//                    System.out.println("Reusing existing GPU ID: " + service.gpuid);
//                    Allure.step("Skipping registration flow for subsequent run");
//                    System.out.println("Skipping registration flow for subsequent run");
//                }
//
//                if (isFirstRun) {
//                    System.out.println("First run - performing full registration...");
//
//                    service.username = "54321" + generateRandomDigits();
//
//                    Allure.step("Registering new user...");
//                    {
//                        System.out.println("Registering new user...");
//                        String password = service.getPassword();
//                        System.out.println("Password fetched: " + password);
//                    }
//                    Allure.step("Fetching token...");
//                    {
//                        System.out.println("Fetching token...");
//                        String token = service.getToken();
//                        System.out.println("Token fetched: " + token);
//                    }
//
//                    Allure.step("Submitting user details...");
//                    {
//                        String firstName = generateRandomName();
//                        System.out.println("Submitting user details...");
//                        service.submitUser(firstName);
//                    }
//
//                    Allure.step("updating user profile...");
//                    {
//                        System.out.println("Updating user profile...");
//                        service.updateUserProfile();
//                    }
//
//                    Allure.step("Fetching user details...");
//                    {
//                        System.out.println("Waiting before fetching user details...");
//                        Thread.sleep(5000);
//                        System.out.println("Fetching user details...");
//                        service.fetchUserDetails();
//                    }
//
//                    Allure.step("stored gpuid for future runs");
//                    {
//                        persistentGpuid = gpuid;
//                        persistentAccessToken = accessToken;
//                        System.out.println("Stored credentials for future runs");
//                    }
//                }
//
//                // Common operations
//                Allure.step("creating lead...");
//                {
//                    System.out.println("Creating lead...");
//                    Thread.sleep(5000);
//                    service.createLead("AutoUser");
//                }
//
//                Allure.step("updating lead...");
//                {
//                    System.out.println("Updating lead...");
//                    service.updateLead();
//                }
//
//                Allure.step("Downgrade date...");
//                {
//                    Thread.sleep(5000);
//                    service.getDowngradeDate();
//                    System.out.println("Downgrade date for the gpuid...... " + service.gpuid);
//                }
//
//                Allure.step("loading db and printing downgrade details");
//                {
//                    Thread.sleep(3000);
//                    service.loadDbConfig();
//                    service.printUpcomingDowngradeInfo();
//
//                    if (!isFirstRun) {
//                        Thread.sleep(3000);
//                        System.out.println("Checking memento schedule...");
//                        service.printMementoSchedule();
//                    } else {
//                        System.out.println("Skipping memento schedule check for first run");
//                        isFirstRun = false;
//                    }
//                } }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            System.out.println("Error during execution: " + e.getMessage());
//        }
//    }
//
//    private String getPassword() throws Exception {
//        HttpHeaders headers = new HttpHeaders();
//        headers.set("Authorization", AUTH_HEADER);
//        headers.setContentType(MediaType.APPLICATION_JSON);
//
//        String url = SIGNUP_URL + "?username=" + username + "&gaid=6ce84844-e74d-4d26-be29-db515aa9f4b5";
//        HttpEntity<Void> entity = new HttpEntity<>(headers);
//
//        System.out.println("Request to get password:");
//        System.out.println("URL: " + url);
//        System.out.println("Headers: " + headers);
//
//        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
//        System.out.println("Response: " + response.getBody());
//
//        JsonNode json = new ObjectMapper().readTree(response.getBody());
//        gpuid = json.path("gpuid").asText();
//        password = json.path("password").asText();  // Ensure password is set here
//        System.out.println("Password fetched: " + password);
//        return password;  // Return password
//    }
//    private String getToken() throws Exception {
//        if (password == null || password.isEmpty()) {
//            throw new Exception("Password is not set.");
//        }
//
//        HttpHeaders headers = new HttpHeaders();
//        headers.set("Authorization", AUTH_HEADER);
//        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
//
//        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
//        form.add("grant_type", "password");
//        form.add("username", username);
//        form.add("password", password);  // Ensure password is included here
//        form.add("scope", "profile");
//
//        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
//
//        System.out.println("Request to get token:");
//        System.out.println("URL: " + TOKEN_URL);
//        System.out.println("Headers: " + headers);
//        System.out.println("Request Body: " + form);
//
//        ResponseEntity<String> response = restTemplate.postForEntity(TOKEN_URL, entity, String.class);
//        System.out.println("Response: " + response.getBody());
//
//        if (response.getStatusCode() == HttpStatus.OK) {
//            JsonNode jsonNode = new ObjectMapper().readTree(response.getBody());
//            accessToken = jsonNode.path("access_token").asText();  // Set the accessToken here
//            System.out.println("Access Token fetched: " + accessToken);
//            return accessToken;  // Return accessToken
//        } else {
//            throw new Exception("Failed to obtain access token. Response: " + response.getBody());
//        }
//    }
//
//
//    private void submitUser(String firstName) {
//        HttpHeaders headers = new HttpHeaders();
//        headers.setBearerAuth(accessToken);
//        headers.setContentType(MediaType.APPLICATION_JSON);
//
//        String body = "{ \"user\": { " +
//                "\"firstName\": \"" + firstName + "\", " +
//                "\"lastName\": \"Test\", " +
//                "\"email\": \"test@gromo.in\", " +
//                "\"gpuid\": \"" + gpuid + "\", " +
//                "\"language\": \"en\", \"isGp\": true } }";
//
//        System.out.println("Request to submit user:");
//        System.out.println("URL: " + USER_URL);
//        System.out.println("Headers: " + headers);
//        System.out.println("Request Body: " + body);
//
//        restTemplate.exchange(USER_URL, HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
//        System.out.println("User submitted with firstName: " + firstName);
//    }
//
//
//    private ResponseEntity<String> updateUserProfile() {
//        System.out.println("Executing: updateUserProfile()");
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);
//        headers.set("Authorization", "Bearer " + accessToken);
//        headers.set("Language", "en");
//        headers.set("versioncode", "372");
//
//        String requestBody = "{ \"user\": {\"isGp\": true, \"fromLocal\": false, \"success\": false}, \"fromLocal\": false, \"success\": false }";
//
//        System.out.println("Request to update user profile:");
//        System.out.println("Headers: " + headers);
//        System.out.println("Request Body: " + requestBody);
//
//        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);
//        ResponseEntity<String> response = restTemplate.exchange(USER_URL, HttpMethod.PUT, requestEntity, String.class);
//        System.out.println("Response: " + response.getBody());
//        return response;
//    }
//
//
//    private ResponseEntity<String> fetchUserDetails() {
//        System.out.println("Executing: fetchUserDetails()");
//        HttpHeaders headers = new HttpHeaders();
//        headers.set("Authorization", "Bearer " + accessToken);
//        headers.set("Language", "en");
//        headers.set("versioncode", "382");
//
//        System.out.println("Request to fetch user details:");
//        System.out.println("Headers: " + headers);
//
//        HttpEntity<String> entity = new HttpEntity<>(headers);
//        ResponseEntity<String> response = restTemplate.exchange(USER_URL, HttpMethod.GET, entity, String.class);
//        System.out.println("Response: " + response.getBody());
//        return response;
//    }
//
//
//    private void createLead(String firstName) throws JSONException {
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);
//
//        String phone = generateRandomPhoneNumber();
//        String body = "{ \"lead\": { " +
//                "\"gromoPartner\": \"" + gpuid + "\", " +
//                "\"firstName\": \"" + firstName + "\", " +
//                "\"lastName\": \"Auto\", " +
//                "\"phone\": \"" + phone + "\", " +
//                "\"email\": \"lead@gromo.in\", " +
//                "\"productTypeId\": \"344\", " +
//                "\"productTypeName\": \"Swiggy HDFC Bank Credit Card\", " +
//                "\"whatsappConsent\": true, \"pincode\": \"122003\" } }";
//
//        System.out.println("Request to create lead:");
//        System.out.println("URL: " + LEAD_URL);
//        System.out.println("Headers: " + headers);
//        System.out.println("Request Body: " + body);
//
//        ResponseEntity<String> response = restTemplate.exchange(LEAD_URL, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
//        System.out.println("Response: " + response.getBody());
//
//        leadId = new JSONObject(response.getBody()).optString("leadId");
//        System.out.println("Lead ID fetched: " + leadId);
//    }
//
//    private void updateLead() {
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);
//
//        String body = "{ \"leadId\": \"" + leadId + "\", " +
//                "\"leadStatus\": \"Success\", \"subStatus\": \"Success\", " +
//                "\"kpi1Payin\": \"" + payin + "\", \"kpi1Payout\": \"" + payout + "\", " +
//                "\"kpi1SuccessDate\": \"" + today + "\", " +
//                "\"dateOfSale\": \"" + today + "\", " +
//                "\"dateOfRevenue\": \"" + today + "\" }";
//
//        System.out.println("Request to update lead:");
//        System.out.println("URL: " + LEAD_UPDATE_URL);
//        System.out.println("Headers: " + headers);
//        System.out.println("Request Body: " + body);
//
//        restTemplate.exchange(LEAD_UPDATE_URL, HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
//        System.out.println("Lead updated with leadId: " + leadId);
//    }
//
//    private static String generateRandomDigits() {
//        return String.valueOf(10000 + new Random().nextInt(90000));
//    }
//
//    private static String generateRandomName() {
//        String alphabet = "abcdefghijklmnopqrstuvwxyz";
//        Random rand = new Random();
//        StringBuilder name = new StringBuilder();
//        for (int i = 0; i < 6; i++) name.append(alphabet.charAt(rand.nextInt(alphabet.length())));
//        return name.toString();
//    }
//
//    private static String generateRandomPhoneNumber() {
//        Random rand = new Random();
//        int[] firstDigits = {6, 7, 8, 9};
//        StringBuilder number = new StringBuilder();
//        number.append(firstDigits[rand.nextInt(firstDigits.length)]);
//        for (int i = 1; i < 10; i++) number.append(rand.nextInt(10));
//        return number.toString();
//    }
//
//    private void getDowngradeDate() {
//        try {
//            HttpHeaders headers = new HttpHeaders();
//            headers.setContentType(MediaType.APPLICATION_JSON);
//            headers.set("Authorization", "Bearer " + accessToken);
//
//            String url = "https://api-stg.gromo.in/api/v1/tier/downgradeDate?gpuid=" + gpuid;
//
//            System.out.println("Request to get downgrade date:");
//            System.out.println("URL: " + url);
//            System.out.println("Headers: " + headers);
//
//            HttpEntity<String> entity = new HttpEntity<>(headers);
//            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
//
//            System.out.println("Response: " + response.getBody());
//
//            // You can process the response here if needed
//        } catch (Exception e) {
//            e.printStackTrace();
//            System.out.println("Error during downgrade date request: " + e.getMessage());
//        }
//    }
//
//    private void loadDbConfig() {
//        try (InputStream input = new ClassPathResource("application.properties").getInputStream()) {
//            Properties prop = new Properties();
//            prop.load(input);
//
//            stagingDbUser = prop.getProperty("staging.datasource.username");
//            stagingDbPass = prop.getProperty("staging.datasource.password");
//            stagingDbUrl = String.format("jdbc:mysql://%s:%s/%s?useSSL=false&serverTimezone=UTC",
//                    prop.getProperty("staging.datasource.host"),
//                    prop.getProperty("staging.datasource.port"),
//                    prop.getProperty("staging.datasource.wallet"));
//
//            System.out.println("✅ Loaded staging DB config");
//        } catch (IOException e) {
//            throw new RuntimeException("❌ Failed to load DB config", e);
//        }
//    }
//
//    private int printUpcomingDowngradeInfo() {
//        String query = "SELECT tierId, shouldDowngrade, downgradeDate " +
//                "FROM gromo_payout.user_tier_mappings " +
//                "WHERE userId = ? " +
//                "ORDER BY downgradeDate DESC LIMIT 1";
//        try (Connection conn = DriverManager.getConnection(stagingDbUrl, stagingDbUser, stagingDbPass);
//             PreparedStatement ps = conn.prepareStatement(query)) {
//
//            ps.setString(1, gpuid);
//
//            try (ResultSet rs = ps.executeQuery()) {
//                if (rs.next()) {
//                    int tierId = rs.getInt("tierId");
//                    int shouldDowngrade = rs.getInt("shouldDowngrade");
//                    Timestamp downgradeDateTs = rs.getTimestamp("downgradeDate");
//                    LocalDateTime downgradeDate = downgradeDateTs != null ? downgradeDateTs.toLocalDateTime() : null;
//
//                    System.out.println("📋 User Downgrade Info:");
//                    System.out.println("🔹 userId          : " + gpuid);
//                    System.out.println("🔹 tierId          : " + tierId);
//                    System.out.println("🔹 shouldDowngrade : " + shouldDowngrade);
//                    System.out.println("🔹 downgradeDate   : " + (downgradeDate != null ? downgradeDate : "N/A"));
//
//                    return tierId;
//                } else {
//                    System.out.println("⚠️ No user_tier_mapping entry found for gpuid: " + gpuid);
//                }
//            }
//        } catch (SQLException e) {
//            System.err.println("❌ Error fetching downgrade info: " + e.getMessage());
//        }
//
//        return -1;
//    }
//
//    private int printMementoSchedule() {
//        String query = "SELECT definitionName, data, destinationURL, startOnDateTime, createdAt, idempotencyKey " +
//                "FROM memento.schedules " +
//                "WHERE idempotencyKey = ? " +
//                "ORDER BY id DESC LIMIT 1";
//
//        try (Connection conn = DriverManager.getConnection(stagingDbUrl, stagingDbUser, stagingDbPass);
//             PreparedStatement ps = conn.prepareStatement(query)) {
//
//            ps.setString(1, "gab_downgrade_" + gpuid);
//
//            try (ResultSet rs = ps.executeQuery()) {
//                if (rs.next()) {
//                    String definitionName = rs.getString("definitionName");
//                    String dataJson = rs.getString("data");
//                    String destinationURL = rs.getString("destinationURL");
//                    Timestamp startOn = rs.getTimestamp("startOnDateTime");
//                    Timestamp createdAt = rs.getTimestamp("createdAt");
//                    String idempotencyKey = rs.getString("idempotencyKey");
//
//                    // Parse data JSON
//                    ObjectMapper mapper = new ObjectMapper();
//                    JsonNode dataNode = mapper.readTree(dataJson);
//
//                    int tierId = dataNode.path("tierId").asInt();
//                    int shouldDowngrade = dataNode.path("shouldDowngrade").asInt();
//                    String downgradeDateStr = dataNode.path("downgradeDate").asText(null);
//                    LocalDateTime downgradeDate = downgradeDateStr != null ? LocalDateTime.parse(downgradeDateStr) : null;
//
//                    System.out.println("📋 Memento Schedule Info:");
//                    System.out.println("🔹 Definition Name  : " + definitionName);
//                    System.out.println("🔹 Destination URL  : " + destinationURL);
//                    System.out.println("🔹 Start On         : " + startOn);
//                    System.out.println("🔹 Created At       : " + createdAt);
//                    System.out.println("🔹 Idempotency Key  : " + idempotencyKey);
//                    System.out.println("🔹 tierId           : " + tierId);
//                    System.out.println("🔹 shouldDowngrade  : " + shouldDowngrade);
//                    System.out.println("🔹 downgradeDate    : " + (downgradeDate != null ? downgradeDate : "N/A"));
//
//                    return tierId;
//                } else {
//                    System.out.println("⚠️ No memento schedule entry found for gpuid: " + gpuid);
//                }
//            }
//        } catch (SQLException | IOException e) {
//            System.err.println("❌ Error fetching memento schedule info: " + e.getMessage());
//        }
//
//        return -1;
//    }
//
//}

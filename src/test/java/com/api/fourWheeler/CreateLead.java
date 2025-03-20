package com.api.fourWheeler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class CreateLead {
    private static final RestTemplate restTemplate = new RestTemplate();
    private static final String CONFIG_FILE = "src/main/resources/config.json";
    private static String sessionToken = null;  // Store the session token globally

    public static void main(String[] args) {
        try {
            sessionToken = getSessionToken();
            if (sessionToken == null) {
                System.err.println("❌ Failed to retrieve a valid session token!");
                return;
            }

            boolean allLeadsCreated = true;
            for (String policyType : new String[]{"comprehensive", "tp", "saod"}) {
                String leadId = createLead(sessionToken, policyType);
                if (leadId != null) {
                    pageDataForVehicleDetails(sessionToken, leadId);
                } else {
                    allLeadsCreated = false;
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String getSessionTokenStatic() {
        return sessionToken;  // Allow other classes to access the session token
    }

    private static String getSessionToken() throws IOException {
        if (sessionToken != null && !sessionToken.isEmpty()) {
            return sessionToken;  // Return cached token if available
        }

        if (ApiCaller.getSessionToken() == null || ApiCaller.getSessionToken().isEmpty()) {
            System.out.println("🔄 No session token found. Logging in...");
            ApiCaller.main(null);
        }
        String token = ApiCaller.getSessionToken();
        if (token != null) {
            sessionToken = URLDecoder.decode(token, StandardCharsets.UTF_8);
            ApiCaller.setSessionToken(sessionToken);
            System.out.println("✅ Stored Session Token: " + sessionToken);
            return sessionToken;
        }
        return null;
    }

    private static String createLead(String authToken, String policyType) throws IOException {
        String url = getConfigValue("mvBaseUrl") + getConfigValue("leadCreateEndpoint");
        System.out.println("📌 Create Lead API URL: " + url);

        String registrationNumber = generateRegistrationNumber(policyType);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("registrationNumber", registrationNumber);
        requestBody.put("leadSource", "rm");
        requestBody.put("businessType", "car");
        requestBody.put("insurancePolicySubType", "rollover");

        return sendPostRequest(url, requestBody, authToken, "Lead");
    }

    private static void pageDataForVehicleDetails(String authToken, String leadId) throws IOException {
        String url = getConfigValue("mvBaseUrl") + getConfigValue("pageData");
        System.out.println("📌 Page Data API URL: " + url);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("leadId", leadId);
        requestBody.put("pageType", "prequote-vehicle-detail");

        sendPostRequest(url, requestBody, authToken, "Page Data");
    }

    private static String getConfigValue(String key) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> config = objectMapper.readValue(new File(CONFIG_FILE), Map.class);
        return config.containsKey(key) ? config.get(key).toString() : null;
    }

    private static String sendPostRequest(String url, Map<String, Object> requestBody, String authToken, String requestType) {
        int retryCount = 0;
        while (retryCount < 2) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("authorizationtoken", authToken);

                HttpEntity<String> requestEntity = new HttpEntity<>(new ObjectMapper().writeValueAsString(requestBody), headers);
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

                System.out.println("🔍 " + requestType + " Response Status: " + response.getStatusCodeValue());
                System.out.println("🔍 Raw Response: " + response.getBody());

                if (response.getStatusCode() == HttpStatus.OK) {
                    JsonNode responseBody = new ObjectMapper().readTree(response.getBody());
                    if (requestType.equals("Lead") && responseBody.has("success") && responseBody.get("success").asBoolean()) {
                        JsonNode data = responseBody.get("data");
                        if (data != null && data.has("leadId")) {
                            return data.get("leadId").asText();
                        }
                    }
                } else if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                    System.out.println("⚠️ Unauthorized (401) - Refreshing token...");
                    sessionToken = getSessionToken();
                    retryCount++;
                    continue;
                }

                return null;
            } catch (Exception e) {
                System.err.println("❌ Error in " + requestType + " request: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }
        return null;
    }

    private static String generateRegistrationNumber(String policyType) {
        Random random = new Random();
        int randomNumber = random.nextInt(9000) + 1000;
        String letters = "" + (char) (random.nextInt(26) + 'A') + (char) (random.nextInt(26) + 'A');

        String stateCode;
        switch (policyType) {
            case "comprehensive": stateCode = "DL1"; break;
            case "tp": stateCode = "MH01"; break;
            case "saod": stateCode = "PB10"; break;
            default: throw new IllegalArgumentException("❌ Invalid policy type: " + policyType);
        }
        return stateCode + letters + randomNumber;
    }
}
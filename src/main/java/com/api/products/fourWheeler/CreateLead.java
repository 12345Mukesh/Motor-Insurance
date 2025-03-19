package com.api.products.fourWheeler;

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

    public static void main(String[] args) {
        try {
            // Ensure session token is available
            if (ApiCaller.sessionToken == null || ApiCaller.sessionToken.isEmpty()) {
                System.out.println("🔄 No session token found. Logging in...");
                ApiCaller.main(null); // Calls ApiCaller to fetch sessionToken
            }

            // Decode session token if necessary
            String authToken = URLDecoder.decode(ApiCaller.sessionToken, StandardCharsets.UTF_8);
            System.out.println("🔑 Decoded Session Token: " + authToken);

            // Create leads and update vehicle details
            createLead(authToken, "comprehensive");
            createLead(authToken, "tp");
            createLead(authToken, "saod");

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Generates a vehicle registration number based on policy type.
     */
    private static String generateRegistrationNumber(String policyType) {
        Random random = new Random();
        int randomNumber = random.nextInt(9000) + 1000; // Generate 4-digit number
        String letters = "" + (char) (random.nextInt(26) + 'A') + (char) (random.nextInt(26) + 'A'); // Random letters

        String stateCode;
        switch (policyType) {
            case "comprehensive":
                stateCode = "DL1";
                break;
            case "tp":
                stateCode = "MH01";
                break;
            case "saod":
                stateCode = "PB10";
                break;
            default:
                throw new IllegalArgumentException("❌ Invalid policy type: " + policyType);
        }
        return stateCode + letters + randomNumber;
    }

    /**
     * Reads a value from `config.json`.
     */
    private static String getConfigValue(String key) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> config = objectMapper.readValue(new File(CONFIG_FILE), Map.class);
        return config.containsKey(key) ? config.get(key).toString() : null;
    }

    /**
     * Creates a lead and updates vehicle details.
     */
    private static void createLead(String authToken, String policyType) throws IOException {
        String url = getConfigValue("mvBaseUrl") + getConfigValue("leadCreateEndpoint");

        String registrationNumber = generateRegistrationNumber(policyType);
        System.out.println("🚗 Generated Registration Number for " + policyType.toUpperCase() + ": " + registrationNumber);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("registrationNumber", registrationNumber);
        requestBody.put("leadSource", "rm");
        requestBody.put("businessType", "car");
        requestBody.put("insurancePolicySubType", "rollover");

        ObjectMapper objectMapper = new ObjectMapper();
        String jsonRequestBody = objectMapper.writeValueAsString(requestBody);
        System.out.println("📤 Request Body (" + policyType.toUpperCase() + "): " + jsonRequestBody);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorizationtoken", authToken);

        HttpEntity<String> requestEntity = new HttpEntity<>(jsonRequestBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

        String leadId = validateLeadResponse(response, policyType, registrationNumber);

        if (leadId != null) {
            updateVehicleDetails(authToken, leadId, policyType);
        } else {
            System.err.println("❌ Skipping vehicle details update: Lead ID is null for " + policyType.toUpperCase());
        }
    }

    /**
     * Validates the API response and returns `leadId`.
     */
    private static String validateLeadResponse(ResponseEntity<String> response, String policyType, String registrationNumber) throws IOException {
        if (response.getStatusCode() == HttpStatus.OK) {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode responseBody = objectMapper.readTree(response.getBody());

            if (responseBody.has("success") && responseBody.get("success").asBoolean()) {
                JsonNode data = responseBody.get("data");

                if (data != null && data.has("leadId") && data.has("insuranceLeadId") && data.has("registrationNumber")) {
                    System.out.println("✅ Lead Created Successfully for " + policyType.toUpperCase() + ":");
                    System.out.println(responseBody.toPrettyString());

                    if (!data.get("registrationNumber").asText().equals(registrationNumber)) {
                        System.err.println("⚠️ Warning: Mismatch in registration number!");
                    }

                    return data.get("leadId").asText(); // Return leadId for updating vehicle details
                } else {
                    System.err.println("❌ Invalid Response Structure: Missing required fields.");
                }
            } else {
                System.err.println("❌ API Response indicates failure: " + response.getBody());
            }
        } else {
            System.err.println("❌ Failed to create lead for " + policyType.toUpperCase() +
                    ". HTTP Status: " + response.getStatusCode() + ", Response: " + response.getBody());
        }
        return null;
    }

    /**
     * Sends a POST request to update vehicle details using the leadId.
     */
    private static void updateVehicleDetails(String authToken, String leadId, String policyType) throws IOException {
        if (leadId == null) {
            System.err.println("❌ Cannot update vehicle details: leadId is null for " + policyType.toUpperCase());
            return;
        }

        String url = getConfigValue("mvBaseUrl") + getConfigValue("pageData");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("leadId", leadId);
        requestBody.put("pageType", "prequote-vehicle-detail");

        ObjectMapper objectMapper = new ObjectMapper();
        String jsonRequestBody = objectMapper.writeValueAsString(requestBody);
        System.out.println("📤 Updating Vehicle Details (" + policyType.toUpperCase() + "): " + jsonRequestBody);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorizationtoken", authToken);

        HttpEntity<String> requestEntity = new HttpEntity<>(jsonRequestBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            System.out.println("✅ Vehicle Details Updated Successfully for " + policyType.toUpperCase() + ":\n" + response.getBody());
        } else {
            System.err.println("❌ Failed to update vehicle details for " + policyType.toUpperCase() +
                    ". HTTP Status: " + response.getStatusCode() + ", Response: " + response.getBody());
        }
    }
    private static void matchVehicleUpdateResponse(ResponseEntity<String> response, String policyType) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode responseBody = objectMapper.readTree(response.getBody());

            if (!responseBody.has("success") || !responseBody.get("success").asBoolean()) {
                System.err.println("❌ Vehicle update failed for " + policyType.toUpperCase() + ": " + response.getBody());
                return;
            }

            System.out.println("✅ Vehicle Details Updated Successfully for " + policyType.toUpperCase());
            System.out.println(responseBody.toPrettyString());

        } catch (IOException e) {
            System.err.println("❌ Error parsing response: " + e.getMessage());
            e.printStackTrace();
        }
    }







}

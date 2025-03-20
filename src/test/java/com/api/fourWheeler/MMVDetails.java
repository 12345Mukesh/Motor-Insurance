package com.api.fourWheeler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MMVDetails {
    private static final RestTemplate restTemplate = new RestTemplate();
    private static final String CONFIG_FILE = "src/main/resources/config.json";
    private static final int MAKE_ID = 236; // Fixed makeId

    public static void main(String[] args) {
        try {
            System.out.println("🚀 Running CreateLead before MMVDetails...");
            CreateLead.main(null);

            // Retrieve session token
            String authToken = getSessionToken();
            if (authToken == null || authToken.isEmpty()) {
                System.err.println("❌ Failed to retrieve valid session token!");
                return;
            }
            System.out.println("🔑 Using Stored Session Token: [" + authToken + "]");

            getManufacturerList(authToken);
            Thread.sleep(3000);
            fetchModelAndCallVariantAPI(authToken);
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the session token from CreateLead and ensures it is properly formatted.
     */
    private static String getSessionToken() throws IOException {
        String token = CreateLead.getSessionTokenStatic();
        if (token == null || token.isBlank()) {
            System.out.println("🔄 No session token found. Logging in...");
            CreateLead.main(null);
            token = CreateLead.getSessionTokenStatic();
        }

        if (token != null && !token.isBlank()) {
            String decodedToken = URLDecoder.decode(token.trim(), StandardCharsets.UTF_8);
            decodedToken = decodedToken.replaceAll("\\s+", ""); // Remove spaces/new lines
            return decodedToken;
        }
        return null;
    }

    /**
     * Fetches the manufacturer list using the session token.
     */
    public static void getManufacturerList(String authToken) throws IOException {
        System.out.println("🚀 Fetching Manufacturer List...");
        String baseUrl = getConfigValue("mvBaseUrl");
        String makeEndpoint = getConfigValue("make");

        if (baseUrl == null || makeEndpoint == null) {
            System.err.println("❌ Missing base URL or make endpoint in config.json");
            return;
        }
        String url = baseUrl + makeEndpoint;
        sendPostRequest(url, new HashMap<>(), authToken, "Manufacturer List");
    }

    /**
     * Fetches the model list and calls the variant API if model ID 1456 is found.
     */
    public static void fetchModelAndCallVariantAPI(String authToken) throws IOException {
        System.out.println("🚀 Fetching Model List for Make ID: " + MAKE_ID);
        String baseUrl = getConfigValue("mvBaseUrl");
        String modelEndpoint = getConfigValue("model");

        if (baseUrl == null || modelEndpoint == null) {
            System.err.println("❌ Missing base URL or model endpoint in config.json");
            return;
        }
        String url = baseUrl + modelEndpoint;
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("makeId", MAKE_ID);

        String modelListResponse = sendPostRequest(url, requestBody, authToken, "Model List");
        List<Integer> modelIds = extractModelIds(modelListResponse);

        if (modelIds.contains(1456)) {
            fetchVariantList(authToken, MAKE_ID, 1456);
        } else {
            System.err.println("❌ Model ID 1456 not found in extracted list: " + modelIds);
        }
    }

    /**
     * Extracts model IDs from the API response.
     */
    private static List<Integer> extractModelIds(String responseBody) throws IOException {
        List<Integer> modelIds = new ArrayList<>();
        if (responseBody == null) return modelIds;

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseJson = objectMapper.readTree(responseBody);
        if (!responseJson.has("success") || !responseJson.get("success").asBoolean()) {
            return modelIds;
        }
        if (!responseJson.has("data") || !responseJson.get("data").has("modelList")) {
            return modelIds;
        }
        for (JsonNode model : responseJson.get("data").get("modelList")) {
            modelIds.add(model.get("modelId").asInt());
        }
        return modelIds;
    }

    /**
     * Fetches the variant list for a given make ID and model ID.
     */
    public static void fetchVariantList(String authToken, int makeId, int modelId) throws IOException {
        System.out.println("🚀 Fetching Variant List for Make ID: " + makeId + ", Model ID: " + modelId);
        String baseUrl = getConfigValue("mvBaseUrl");
        String variantEndpoint = getConfigValue("variant");
        if (baseUrl == null || variantEndpoint == null) return;
        String url = baseUrl + variantEndpoint;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("makeId", makeId);
        requestBody.put("modelId", modelId);
        sendPostRequest(url, requestBody, authToken, "Variant List");
    }

    /**
     * Sends a POST request and handles authorization errors.
     */
    private static String sendPostRequest(String url, Map<String, Object> requestBody, String authToken, String requestType) {
        try {
            if (authToken == null || authToken.isBlank()) {
                System.err.println("❌ No auth token for " + requestType);
                return null;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", authToken); // No "Bearer " prefix

            System.out.println("📩 Headers: " + headers);
            System.out.println("📨 Sending request to: " + url);
            System.out.println("📜 Request Body: " + new ObjectMapper().writeValueAsString(requestBody));

            HttpEntity<String> requestEntity = new HttpEntity<>(new ObjectMapper().writeValueAsString(requestBody), headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                System.err.println("⚠️ 401 Unauthorized! Refreshing session token...");
                authToken = getSessionToken();
                if (authToken != null) {
                    headers.set("Authorization", authToken);
                    requestEntity = new HttpEntity<>(new ObjectMapper().writeValueAsString(requestBody), headers);
                    response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
                }
            }

            System.out.println("✅ Response: " + response.getBody());
            return response.getBody();
        } catch (Exception e) {
            System.err.println("❌ Error in " + requestType + " request: " + e.getMessage());
            return null;
        }
    }

    /**
     * Reads configuration values from config.json.
     */
    private static String getConfigValue(String key) throws IOException {
        return new ObjectMapper().readTree(new File(CONFIG_FILE)).path(key).asText(null);
    }
}
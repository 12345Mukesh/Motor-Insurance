package com.api.fourWheeler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MMVDetails {
    private static final RestTemplate restTemplate = new RestTemplate();
    private static final String CONFIG_FILE = "src/main/resources/config.json";
    private static final int MAKE_ID = 236; // Fixed makeId
    private static final int MODEL_ID = 1456; // Fixed modelId

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

    private static String getSessionToken() throws IOException {
        String token = CreateLead.getSessionTokenStatic();

        if (token == null || token.isBlank()) {
            System.out.println("🔄 No session token found. Fetching a new one...");
            CreateLead.main(null);
            token = CreateLead.getSessionTokenStatic();
        }

        return (token != null && !token.isBlank()) ? token.trim() : null;
    }

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

        if (isModelPresent(modelListResponse, MODEL_ID)) {
            System.out.println("✅ Model ID " + MODEL_ID + " found! Fetching variants...");
            fetchVariantList(authToken, MAKE_ID, MODEL_ID);
        } else {
            System.err.println("❌ Model ID " + MODEL_ID + " not found. Skipping Variant API call.");
        }
    }

    private static boolean isModelPresent(String responseBody, int modelId) throws IOException {
        if (responseBody == null) return false;

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseJson = objectMapper.readTree(responseBody);

        if (!responseJson.has("success") || !responseJson.get("success").asBoolean()) {
            System.err.println("⚠️ API response indicates failure.");
            return false;
        }

        JsonNode dataNode = responseJson.path("data").path("modelList");
        if (!dataNode.isArray()) {
            System.err.println("⚠️ modelList is missing or not an array.");
            return false;
        }

        for (JsonNode model : dataNode) {
            if (model.path("modelId").asInt() == modelId) {
                return true;
            }
        }
        return false;
    }

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

    private static String sendPostRequest(String url, Map<String, Object> requestBody, String authToken, String requestType) {
        try {
            if (authToken == null || authToken.isBlank()) {
                System.err.println("❌ No auth token for " + requestType);
                return null;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorizationtoken", authToken);

            System.out.println("📩 Headers: " + headers);
            System.out.println("📨 Sending request to: " + url);
            System.out.println("📜 Request Body: " + new ObjectMapper().writeValueAsString(requestBody));

            HttpEntity<String> requestEntity = new HttpEntity<>(new ObjectMapper().writeValueAsString(requestBody), headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                System.err.println("⚠️ 401 Unauthorized! Refreshing session token...");
                authToken = getSessionToken();
                if (authToken != null) {
                    headers.set("Authorizationtoken", authToken);
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

    private static String getConfigValue(String key) throws IOException {
        return new ObjectMapper().readTree(new File(CONFIG_FILE)).path(key).asText(null);
    }
}
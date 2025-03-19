package com.api.products.fourWheeler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ApiCaller {
    private static final RestTemplate restTemplate = new RestTemplate();
    private static final String CONFIG_FILE = "src/main/resources/config.json";

    // ✅ Store session token as a static variable
    public static String sessionToken;

    public static void main(String[] args) {
        try {
            System.out.println("🔄 Requesting OTP Token...");
            String otpToken = requestOtp();

            System.out.println("🔄 Logging in...");
            String loginResponse = login(otpToken);  // ✅ Store the full login response

            System.out.println("\n✅ FULL LOGIN RESPONSE:");
            System.out.println(loginResponse);  // ✅ Print the complete login response

            System.out.println("\n🔑 Extracted Session Token: " + sessionToken);  // ✅ Print the extracted session token
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
        }
    }

    // 🔹 Step 1: Request OTP API
    public static String requestOtp() throws IOException {
        String url = getConfigValue("baseUrl") + getConfigValue("sendOtpEndpoint");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("phone", getConfigValue("phone"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Map.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            Map<String, Object> responseData = (Map<String, Object>) response.getBody().get("data");
            return responseData.get("otpToken").toString();
        }

        throw new RuntimeException("❌ Failed to request OTP.");
    }

    // 🔹 Step 2: Login API and Store Session Token
    public static String login(String otpToken) throws IOException {
        String url = getConfigValue("baseUrl") + getConfigValue("loginEndpoint");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("otp", Integer.parseInt(getConfigValue("otp")));
        requestBody.put("phone", Long.parseLong(getConfigValue("phone")));
        requestBody.put("communicationConsent", Boolean.parseBoolean(getConfigValue("communicationConsent")));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", otpToken);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Map.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            Map<String, Object> responseBody = response.getBody();

            // ✅ Print the full login response
            String fullResponse = responseBody.toString();

            // ✅ Extract and store the session token
            if (responseBody.containsKey("data")) {
                Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                sessionToken = (String) data.get("sessionToken");
            }

            return fullResponse;  // ✅ Return full response
        } else {
            throw new RuntimeException("❌ Failed to login.");
        }
    }

    // 🔹 Pretty Print JSON Response
    public static void printFormattedResponse(Map<String, Object> response) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            // Convert the response Map into a formatted JSON string
            String formattedJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);

            System.out.println("✅ FORMATTED JSON RESPONSE:\n" + formattedJson);
        } catch (Exception e) {
            System.err.println("❌ Error formatting response: " + e.getMessage());
        }
    }

    // 🔹 Step 4: Read Config Values from `config.json`
    public static String getConfigValue(String key) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> config = objectMapper.readValue(new File(CONFIG_FILE), Map.class);

        Object value = config.get(key);
        return value != null ? value.toString() : null;
    }
}

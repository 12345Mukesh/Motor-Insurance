package SignUp;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Random;

@Service
public class SignUpWithReferralWithMultipleUsers {
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String SIGNUP_URL = "https://auth-stg.gromo.in/oauth/otp";
    private static final String TOKEN_URL = "https://auth-stg.gromo.in/oauth/token";
    private static final String USER_URL = "https://api-stg.gromo.in/api/v3/users";
    private static final String AUTH_HEADER = "Basic QjZCRWV2OUppSGcxaDZLQ3U2UHlCVXZ1OmU2U1lwNDBxTzZWcndmRTVuOXJhYTB4RG1oNWlGc0xQRjY4U2tqWHhCSFRUTVBWSw==";
    private static final String USERNAME_PREFIX = "54321";

    private String username = USERNAME_PREFIX + generateRandomDigits();
    private String firstName = generateRandomFirstName();
    private String gpuid;
    private String accessToken;
    private String password;
    private static String referrer = "E7JO3899";
    private final String gaid = "6ce84844-e74d-4d26-be29-db515aa9f4b5"; // Ensure gaid is initialized

    private static String[] gpuids;

    private static String generateRandomDigits() {
        return String.valueOf(10000 + new Random().nextInt(90000));
    }

    private static String generateRandomFirstName() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        int nameLength = 5 + random.nextInt(6); // Name length between 5 and 10 characters
        for (int i = 0; i < nameLength; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    public String getToken() {  // Remove gaid parameter
        System.out.println("Executing: getToken()");
        password = getPasswordFromSignup(this.gaid);  // Use the class variable directly
        if (password == null) {
            throw new RuntimeException("Failed to get password");
        }
        return getAccessToken();
    }
    private String getPasswordFromSignup(String gaid) {
        System.out.println("Executing: getPasswordFromSignup()");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", AUTH_HEADER);

        // Construct the signup request URL correctly
        String url = SIGNUP_URL + "?username=" + username + "&gaid=" + gaid;

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        System.out.println("Signup API Response Status: " + response.getStatusCode());

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(response.getBody());

                // Print full response for debugging
                System.out.println("Full Signup API Response Body:");
                System.out.println(jsonNode.toPrettyString());

                // Debug: Check if `gpuid` exists at different levels
                if (jsonNode.has("gpuid")) {
                    this.gpuid = jsonNode.get("gpuid").asText();
                } else if (jsonNode.has("data") && jsonNode.get("data").has("gpuid")) {
                    this.gpuid = jsonNode.get("data").get("gpuid").asText();
                } else if (jsonNode.has("data") && jsonNode.get("data").has("user") && jsonNode.get("data").get("user").has("gpuid")) {
                    this.gpuid = jsonNode.get("data").get("user").get("gpuid").asText();
                } else {
                    System.out.println("Warning: GPUID field not found in response!");
                    this.gpuid = null;
                }


                this.password = jsonNode.has("password") ? jsonNode.get("password").asText() : null;

                if (this.password == null || this.gpuid == null) {
                    System.out.println("Error: Missing password or GPUID in response!");
                    throw new RuntimeException("Missing password or GPUID in response");
                }

                // Print extracted values
                System.out.println("Generated Username: " + username);
                System.out.println("Generated First Name: " + firstName);
                System.out.println("Extracted GPUID: " + this.gpuid);
                System.out.println("Extracted Password: " + this.password);

                return this.password;
            } catch (Exception e) {
                System.out.println("Exception while parsing JSON response: " + e.getMessage());
                throw new RuntimeException("Failed to parse password and gpuid", e);
            }
        } else {
            System.out.println("Error: Signup API request failed with status: " + response.getStatusCode());
            System.out.println("Response Body: " + response.getBody());  // Print response for debugging
        }
        return null;
    }




    private String getAccessToken() {
        System.out.println("Executing: getAccessToken()");
        if (password == null) {
            throw new RuntimeException("Password is null. Signup might have failed.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", AUTH_HEADER);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("username", username);
        formData.add("password", password);
        formData.add("scope", "profile");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(TOKEN_URL, request, String.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                accessToken = jsonNode.get("access_token").asText();

                // Print access token
                System.out.println("Generated Access Token: " + accessToken);

                return accessToken;
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse access token", e);
            }
        }
        return null;
    }

    public String getGpuid() {
        return gpuid;
    }

    public ResponseEntity<String> submitUserDetails() {
        System.out.println("Executing: submitUserDetails()");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("versionCode", "372");

        String requestBody = "{ \"user\": { " +
                "\"firstName\": \"" + firstName + "\", " +
                "\"lastName\": \"Test\", " +
                "\"annualIncome\": \"5–10L\", " +
                "\"communicationAddress\": { " +
                "\"city\": \"Gurgaon\", \"pincode\": 122003, \"state\": \"HARYANA\" }, " +
                "\"dateOfBirth\": \"1999-01-16\", " +
                "\"email\": \"abcd@gmail.com\", " +
                "\"gpuid\": \"" + gpuid + "\", " +
                "\"isGp\": true, " +
                "\"language\": \"en\", " +
                "\"qualification\": \"Graduate\", " +
                "\"refferedBy\": \"" + referrer + "\", " +
                "\"whatsAppConsent\": true " +
                "}, \"fromLocal\": false, \"success\": false }";

        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

        // Execute API call and store response
        ResponseEntity<String> response = restTemplate.exchange(USER_URL, HttpMethod.PUT, requestEntity, String.class);

        System.out.println("=== Submit User Details Request ===");
        System.out.println("Request URL: " + USER_URL);
        System.out.println("Headers: " + headers);
        System.out.println("Request Body: " + requestBody);

        System.out.println("\n=== Submit User Details Response ===");
        System.out.println("Response Status: " + response.getStatusCode());
        System.out.println("Response Body: " + response.getBody());

        return response; // ✅ Added missing return statement
    }

    public ResponseEntity<String> updateUserProfile()
    {
        System.out.println("Executing: updateUserProfile()");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Language", "en");
        headers.set("versioncode", "372");

        String requestBody = "{ \"user\": {\"isGp\": true, \"fromLocal\": false, \"success\": false}, \"fromLocal\": false, \"success\": false }";
        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);
        return restTemplate.exchange(USER_URL, HttpMethod.PUT, requestEntity, String.class);
    }

    public ResponseEntity<String> fetchUserDetails() {
        System.out.println("Executing: fetchUserDetails()");
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Language", "en");
        headers.set("versioncode", "382");
        HttpEntity<String> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(USER_URL, HttpMethod.GET, entity, String.class);
    }

    public static void main(String[] args) {
        System.out.println("Executing: main()");

        int numUsers = 10;
        gpuids = new String[numUsers];

        for (int i = 0; i < numUsers; i++) {
            int userIndex = i + 1; // Ensuring unique user number
            try {
                System.out.println("User " + userIndex + " is being created.");
                SignUpWithReferralWithMultipleUsers authService = new SignUpWithReferralWithMultipleUsers();

                // Step 1: Get Access Token
                String token = authService.getToken();
                System.out.println("User " + userIndex + " Access Token: " + token);
                System.out.println("-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");

                // ✅ Store extracted GPUID into array
                gpuids[i] = authService.getGpuid();

                // ✅ Debugging: Print stored GPUID immediately
                System.out.println("Stored GPUID for User " + userIndex + ": " + gpuids[i]);

                // Step 2: Submit User Details
                ResponseEntity<String> submitDetailsResponse = authService.submitUserDetails();
                System.out.println("User " + userIndex + " Submit User Details Response: " + submitDetailsResponse.getBody());
                System.out.println("-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");

                // Step 3: Update User Profile
                ResponseEntity<String> profileResponse = authService.updateUserProfile();
                System.out.println("User " + userIndex + " Update Profile Response: " + profileResponse.getBody());
                System.out.println("-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");

                // Step 4: Wait for 5 seconds before fetching user details
                System.out.println("User " + userIndex + " waiting for 5 seconds before fetching details...");
                Thread.sleep(5000);  // Ensure delay is part of sequential execution
                System.out.println("-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");

                // Step 5: Fetch User Details
                ResponseEntity<String> userDetailsResponse = authService.fetchUserDetails();
                System.out.println("User " + userIndex + " Fetch User Details Response: " + userDetailsResponse.getBody());
                System.out.println("************************************************************************************************************************************************************************************************************************************");

            } catch (Exception e) {
                System.err.println("Error during signup for User " + userIndex + ": " + e.getMessage());
            }
        }

        // ✅ Print GPU IDs in the required format
        System.out.println("\nAll Generated GPU IDs:");
        System.out.print("String[] gpuidList = {");
        for (int i = 0; i < numUsers; i++) {
            System.out.print("\"" + gpuids[i] + "\"");
            if (i < numUsers - 1) {
                System.out.print(", ");
            }
        }
        System.out.println("};");
    }


}

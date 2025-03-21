package com.api.fourWheeler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.time.LocalDate;



public class MMVDetails {
    private static final RestTemplate restTemplate = new RestTemplate();
    private static final String CONFIG_FILE = "src/main/resources/config.json";
    private static final int MAKE_ID = 236;
    private static final int MODEL_ID = 1456;
    private static final int VARIANT_ID = 4924;

    private static String comprehensiveLead;
    private static String tpLead;
    private static String saodLead;

    public static void main(String[] args) {
        try {
            System.out.println("🚀 Running CreateLead before MMVDetails...");
            CreateLead.main(null);

            comprehensiveLead = CreateLead.getComprehensiveLead();
            tpLead = CreateLead.getTpLead();
            saodLead = CreateLead.getSaodLead();

            if (comprehensiveLead == null || tpLead == null || saodLead == null) {
                System.err.println("❌ Failed to retrieve all required Lead IDs! Aborting...");
                return;
            }

            String authToken = getSessionToken();
            if (authToken == null) {
                System.err.println("❌ Failed to retrieve valid session token!");
                return;
            }
            System.out.println("🔑 Using Stored Session Token: [" + authToken + "]");

            getManufacturerList(authToken);
            Thread.sleep(3000);
            fetchModelAndCallVariantAPI(authToken);
        } catch (Exception e) {
            System.err.println("❌ Error in MMVDetails execution: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String getSessionToken() throws IOException {
        return CreateLead.getSessionTokenStatic();
    }

    public static void getManufacturerList(String authToken) throws IOException {
        String url = getConfigValue("mvBaseUrl") + getConfigValue("make");
        String response = sendPostRequest(url, new HashMap<>(), authToken, "Manufacturer List");
        System.out.println("🔵 Full Manufacturer List Response:\n" + response);
    }

    public static void fetchModelAndCallVariantAPI(String authToken) throws IOException {
        String url = getConfigValue("mvBaseUrl") + getConfigValue("model");
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("makeId", MAKE_ID);

        String modelListResponse = sendPostRequest(url, requestBody, authToken, "Model List");
        System.out.println("🟢 Full Model List Response:\n" + modelListResponse);

        if (isModelPresent(modelListResponse, MODEL_ID)) {
            fetchVariantList(authToken);
        } else {
            System.err.println("❌ Model ID not found!");
        }
    }

    private static boolean isModelPresent(String responseBody, int modelId) throws IOException {
        if (responseBody == null) return false;
        JsonNode dataNode = new ObjectMapper().readTree(responseBody).path("data").path("modelList");
        for (JsonNode model : dataNode) {
            if (model.path("modelId").asInt() == modelId) {
                return true;
            }
        }
        return false;
    }

    public static void fetchVariantList(String authToken) throws IOException {
        String url = getConfigValue("mvBaseUrl") + getConfigValue("variant");
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("makeId", MAKE_ID);
        requestBody.put("modelId", MODEL_ID);

        // 🔹 Send API request
        String variantListResponse = sendPostRequest(url, requestBody, authToken, "Variant List");

        if (variantListResponse == null || variantListResponse.isBlank()) {
            System.err.println("❌ Empty or null response received from API!");
            return;
        }

        System.out.println("🟠 Full Variant List Response:\n" + variantListResponse);

//        // 🔹 Parse JSON response
//        ObjectMapper objectMapper = new ObjectMapper();
//        JsonNode rootNode;
//        try {
//            rootNode = objectMapper.readTree(variantListResponse);
//        } catch (IOException e) {
//            System.err.println("❌ Failed to parse API response: " + e.getMessage());
//            return;
//        }
//
//        JsonNode variantListNode = rootNode.path("data").path("variantList");
//        if (!variantListNode.isArray() || variantListNode.isEmpty()) {
//            System.err.println("❌ 'variantList' is missing or empty in response!");
//            return;
//        }
//
//        // 🔍 Debugging: Print all received variant IDs
//        System.out.print("🔍 Available Variant IDs: ");
//        boolean variantFound = false;
//
//        for (JsonNode variant : variantListNode) {
//            int foundVariantId = variant.path("variantId").asInt(-1);
//            if (foundVariantId == -1) continue;
//
//            System.out.print(foundVariantId + " ");
//            if (foundVariantId == VARIANT_ID) {  // 🔹 Checking hardcoded variant ID
//                variantFound = true;
//            }
//        }
//        System.out.println();

        // ✅ Variant Check Output
//        if (variantFound) {
//            System.out.println("✅ Variant ID "+ VARIANT_ID + "  found!");
//        } else {
//            System.err.println("❌ Variant ID "+VARIANT_ID +" not found!");
//        }

        // 🔹 Directly trigger `updateVehicleDetails`, independent of the variant check
        updateVehicleDetails(authToken);
    }


    public static void updateVehicleDetails(String authToken) throws IOException {
        String url = getConfigValue("mvBaseUrl") + getConfigValue("updateLeadDetails");

        if (comprehensiveLead == null || tpLead == null || saodLead == null) {
            System.err.println("❌ Cannot update lead details - Missing Lead IDs!");
            return;
        }

        // 🔹 Define details for each policy type
        Map<String, String> leadMap = Map.of(
                "Comprehensive", comprehensiveLead,
                "TP", tpLead,
                "SAOD", saodLead
        );

        Map<String, Integer> registrationYears = Map.of(
                "Comprehensive", 2018,
                "TP", 2015,
                "SAOD", 2024
        );

        Map<String, Integer> daysToAdd = Map.of(
                "Comprehensive", 15,
                "TP", 2,
                "SAOD", 5
        );

        Map<String, String> customerNames = Map.of(
                "Comprehensive", "Alice",
                "TP", "Bob",
                "SAOD", "Charlie"
        );

        Map<String, String> customerPhones = Map.of(
                "Comprehensive", "9876543210",
                "TP", "9123456789",
                "SAOD", "9988776655"
        );

        boolean updateSuccessful = false;

        for (Map.Entry<String, String> entry : leadMap.entrySet()) {
            String policyType = entry.getKey();
            String leadId = entry.getValue();

            // Generate Registration Date based on policy type
            String registrationDate = generateFixedRegistrationDate(registrationYears.get(policyType), daysToAdd.get(policyType));

            // 🔹 Construct request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("leadId", leadId);
            requestBody.put("makeId", MAKE_ID);
            requestBody.put("modelId", MODEL_ID);
            requestBody.put("variantId", VARIANT_ID);
            requestBody.put("fuelType", "petrol");
            requestBody.put("leadSource", null);

            requestBody.put("leadDetails", Map.of(
                    "cubicCapacity", 1197,
                    "fuelType", "petrol",
                    "registrationDate", registrationDate
            ));

            requestBody.put("leadCustomerDetails", Map.of(
                    "name", customerNames.get(policyType),
                    "mobileNumber", customerPhones.get(policyType)
            ));

            // 🖨 **Print Full Request Before Sending**
            System.out.println("\n📝 Request Data for " + policyType.toUpperCase() + ":");
            System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(requestBody));

            // 🔹 Send API Request
            String response = sendPostRequest(url, requestBody, authToken, "Update Lead");

            // ✅ Print Success Response Based on Policy Type
            if (response != null && !response.isBlank()) {
                System.out.println("✅ Update Lead Success for " + policyType.toUpperCase() + "!");
                System.out.println("🔍 Full Response: " + response);
                updateSuccessful = true;  // Mark as successful
            } else {
                System.err.println("❌ Update Lead Failed for " + policyType.toUpperCase() + "!");
            }
        }

        // 🔹 If at least one lead update is successful, trigger `bundledLead`
        if (updateSuccessful) {
            System.out.println("\n🚀 Triggering Bundled Lead after successful updates...");
            bundledLead(authToken);
        }
    }


    /**
     * Generates a registration date based on today's date + X days, with a fixed year.
     */
    private static String generateFixedRegistrationDate(int year, int daysToAdd) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, daysToAdd); // Add custom days per policy
        calendar.set(Calendar.YEAR, year); // Set the specified year
        return new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime());
    }

    public static void bundledLead(String authToken) throws IOException {

        String url = getConfigValue("mvBaseUrl") + getConfigValue("leadCreateEndpoint");
        // 🔹 Generate Today's Date (ISO Format)
        String registrationDate = LocalDate.now().toString();
        System.out.println("📅 Today's Date: " + registrationDate);

        // 🔹 Hardcoded Values
        String registrationNumber = "MH01";
        String businessType = "car";
        String policySubType = "new";
        String customerName = "";
        String mobileNumber = "9876543210";

        // 🔹 Construct Lead Request Body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("registrationNumber", registrationNumber);
        requestBody.put("businessType", businessType);
        requestBody.put("insurancePolicySubType", policySubType);
        requestBody.put("registrationDate", registrationDate);
        requestBody.put("makeId", MAKE_ID);
        requestBody.put("modelId", MODEL_ID);
        requestBody.put("variantId", VARIANT_ID);
        requestBody.put("leadSource", "rm");

        requestBody.put("leadCustomerDetails", Map.of(
                "name", customerName,
                "mobileNumber", mobileNumber
        ));

        // 🖨 **Print Full Request Before Sending**
        System.out.println("\n📝 Bundled Lead Creation Request:");
        System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(requestBody));

        // 🔹 Send API Request
        String response = sendPostRequest(url, requestBody, authToken, "Bundled Lead Creation");

        // ✅ Print Success or Failure Response
        if (response != null && !response.isBlank()) {
            System.out.println("✅ Bundled Lead Creation Success!");
            System.out.println("🔍 Full Response: " + response);
        } else {
            System.err.println("❌ Bundled Lead Creation Failed!");
        }
    }

    private static String sendPostRequest(String url, Map<String, Object> requestBody, String authToken, String requestType) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorizationtoken", authToken);

            HttpEntity<String> requestEntity = new HttpEntity<>(new ObjectMapper().writeValueAsString(requestBody), headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody(); // ✅ Return response without printing
            } else {
                System.err.println("❌ " + requestType + " Failed! HTTP Status: " + response.getStatusCode());
                return null;
            }
        } catch (Exception e) {
            System.err.println("❌ Error in " + requestType + " request: " + e.getMessage());
            return null;
        }
    }



    private static String getConfigValue(String key) throws IOException {
        return new ObjectMapper().readTree(new File(CONFIG_FILE)).path(key).asText(null);
    }


}

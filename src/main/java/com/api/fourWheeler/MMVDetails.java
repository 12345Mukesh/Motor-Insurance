package com.api.fourWheeler;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;



import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;



public class MMVDetails {
    private static final RestTemplate restTemplate = new RestTemplate();
    private static final String CONFIG_FILE = "src/main/resources/config.json";
    private static final int MAKE_ID = 236;
    private static final int MODEL_ID = 1456;
    private static final int VARIANT_ID = 4924;

    private static String comprehensiveLead;
    private static String tpLead;
    private static String saodLead;
    private static String bundledLead;
    private static String authToken;


    private static String selectedPolicy;

    static {
        try {
            selectedPolicy = getConfigValue("selectedPolicy");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final List<Integer> insurerIds = new ArrayList<>();
    private static final List<Integer> ncbIds = new ArrayList<>();

    private static int insurerIndex = 10;
    private static int ncbIndex = 2;

    public static void main(String[] args) {
        try {
            System.out.println("🚀 Running CreateLead before MMVDetails...");
            CreateLead.main(null); // Run CreateLead to generate leads

            // Fetch individual lead IDs
            comprehensiveLead = CreateLead.getComprehensiveLead();
            tpLead = CreateLead.getTpLead();
            saodLead = CreateLead.getSaodLead();

            authToken = CreateLead.getSessionTokenStatic();
            if (authToken == null) {
                System.err.println("❌ Failed to retrieve session token!");
                return;
            }
            System.out.println("🔑 Using Session Token: " + authToken);

            if (selectedPolicy == null || selectedPolicy.isEmpty()) {
                System.err.println("❌ selectedPolicy is missing in config.json!");
                return;
            }

            selectedPolicy = selectedPolicy.toLowerCase();
            // Convert "all" to a list of all policies
            List<String> policiesToRun = getPoliciesToRun(selectedPolicy);

            if (policiesToRun.isEmpty()) {
                System.err.println("❌ No policies selected! Exiting...");
                return;
            }

            // Fetch manufacturer, model, and variant details
            getManufacturerList(authToken);
            Thread.sleep(3000);
            fetchModelAndCallVariantAPI(authToken);

            // If "bundled" is needed, create the lead
            if (policiesToRun.contains("bundled")) {
                System.out.println("\n🚀 Creating Bundled Lead...");
                bundledLead = bundledLead(authToken);
                if (bundledLead == null) {
                    System.err.println("❌ Bundled Lead Creation Failed!");
                }
            }

            // ✅ Process each policy dynamically
            for (String policy : policiesToRun) {
                String leadId = getLeadIdForPolicy(policy);
                if (leadId != null) {
                    processPolicy(authToken, policy, leadId); // Centralized logic
                } else {
                    System.err.println("⚠️ Skipping policy " + policy + " due to missing leadId.");
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Error in MMVDetails execution: " + e.getMessage());
            e.printStackTrace();
        }
    }



    public static List<String> getPoliciesToRun(String selectedPolicy) {
        List<String> policies = new ArrayList<>();

        if ("all".equals(selectedPolicy)) {
            policies.addAll(Arrays.asList("comprehensive", "tp", "saod", "bundled"));
        } else {
            policies.add(selectedPolicy);
        }
        return policies;
    }

    private static String getLeadIdForPolicy(String policyType) {
        policyType = policyType.toLowerCase();

        switch (policyType) {
            case "comprehensive":
                return comprehensiveLead;
            case "tp":
                return tpLead;
            case "saod":
                return saodLead;
            case "bundled":
                System.out.println("🚀 Creating Bundled Lead...");
                try {
                    return bundledLead(authToken); // ✅ Handle exception properly
                } catch (IOException e) {
                    System.err.println("❌ Error creating Bundled Lead: " + e.getMessage());
                    return null;
                }
            default:
                System.err.println("❌ Unknown policy type: " + policyType);
                return null;
        }
    }

    private static void processPolicy(String authToken, String policy, String leadId) throws IOException, InterruptedException {
        if (leadId == null || leadId.isEmpty()) {
            System.err.println("⚠️ Skipping " + policy + " due to missing lead ID.");
            return;
        }

        System.out.println("📝 Processing lead: " + leadId + " for policy: " + policy.toUpperCase());

        // If the policy is "bundled", no need to hit the insurer method
        if ("bundled".equalsIgnoreCase(policy)) {
            System.out.println("✅ Bundled policy selected. No further processing required.");
            return;
        }


        // For other policies, update vehicle details, call insurer, and fetch prequote details
        updateVehicleDetails(authToken, policy, leadId);
        insurer(authToken);
        prequoteVehicleDetails(authToken, leadId);
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

    }



    public static void updateVehicleDetails(String authToken, String policy, String leadId) throws IOException {
        String url = getConfigValue("mvBaseUrl") + getConfigValue("updateLeadDetails");

        Map<String, Integer> registrationYears = Map.of(
                "comprehensive", 2018,
                "tp", 2015,
                "saod", 2024
        );

        Map<String, Integer> daysToAdd = Map.of(
                "comprehensive", 15,
                "tp", 2,
                "saod", 5
        );

        Map<String, String> customerNames = Map.of(
                "comprehensive", "Alice",
                "tp", "Bob",
                "saod", "Charlie"
        );

        Map<String, String> customerPhones = Map.of(
                "comprehensive", "9876543210",
                "tp", "9123456789",
                "saod", "9988776655"
        );

        String registrationDate = generateFixedRegistrationDate(
                registrationYears.get(policy),
                daysToAdd.get(policy)
        );

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
                "name", customerNames.get(policy),
                "mobileNumber", customerPhones.get(policy)
        ));

        System.out.println("\n📝 Request Data for " + policy.toUpperCase() + ":");
        System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(requestBody));

        String response = sendPostRequest(url, requestBody, authToken, "Update Lead");

        System.out.println("🔹 Response for " + policy.toUpperCase() + ": " + response);

        if (response != null && !response.isBlank()) {
            System.out.println("✅ Update Lead Success for " + policy.toUpperCase() + "!");
        } else {
            System.err.println("❌ Update Lead Failed for " + policy.toUpperCase() + "!");
        }
    }


    private static String generateFixedRegistrationDate(int year, int daysToAdd) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, daysToAdd);
        calendar.set(Calendar.YEAR, year);
        return new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime());
    }

    public static String bundledLead(String authToken) throws IOException {
        String url = getConfigValue("mvBaseUrl") + getConfigValue("leadCreateEndpoint");
        String registrationDate = LocalDate.now().toString();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("registrationNumber", "MH01");
        requestBody.put("businessType", "car");
        requestBody.put("insurancePolicySubType", "new");
        requestBody.put("registrationDate", registrationDate);
        requestBody.put("makeId", MAKE_ID);
        requestBody.put("modelId", MODEL_ID);
        requestBody.put("variantId", VARIANT_ID);
        requestBody.put("leadSource", "rm");

        requestBody.put("leadCustomerDetails", Map.of(
                "name", "pat",
                "mobileNumber", "9876543210"
        ));

        System.out.println("\n📝 Bundled Lead Creation Request:");
        System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(requestBody));

        String response = sendPostRequest(url, requestBody, authToken, "Bundled Lead Creation");

        if (response != null && !response.isBlank()) {
            System.out.println("✅ Bundled Lead Creation Success!");
            System.out.println("🔍 Full Response: " + response);

            JsonNode responseJson = new ObjectMapper().readTree(response);
            String leadId = responseJson.path("data").path("leadId").asText();
            System.out.println("🆔 Bundled Lead ID: " + leadId);

            if (leadId.isEmpty()) {
                System.err.println("❌ Failed to extract Bundled Lead ID from response!");
                return null;
            }

            return leadId;
        } else {
            System.err.println("❌ Bundled Lead Creation Failed!");
            return null;
        }
    }


    public static void insurer(String authToken) {
        try {
            String url = getConfigValue("mvBaseUrl") + getConfigValue("insurer");

            // Empty JSON Request Body
            String response = sendPostRequest(url, Map.of(), authToken, "Insurer API");

            if (response != null) {
                System.out.println("✅ Insurer API Call Success!");
                System.out.println("🔍 Full Response: " + response);

                // Extract and store all insurer IDs
                List<Integer> extractedInsurerIds = extractInsurerIds(response);
                insurerIds.addAll(extractedInsurerIds);

                // Debug Print: Check if IDs are stored
                System.out.println("📌 Stored Insurer IDs: " + insurerIds);

                // Call NCB API next
                getNCBDetails(authToken);
            } else {
                System.err.println("❌ Insurer API Call Failed!");
            }
        } catch (Exception e) {
            System.err.println("❌ Error in Insurer API Call: " + e.getMessage());
        }
    }

    public static void getNCBDetails(String authToken) {
        try {
            String url = getConfigValue("mvBaseUrl") + getConfigValue("ncb");

            // Empty JSON Request Body
            String response = sendPostRequest(url, Map.of(), authToken, "NCB Details API");

            if (response != null) {
                System.out.println("✅ NCB API Call Success!");
                System.out.println("🔍 Full Response: " + response);

                List<Integer> extractedNCBIds = extractNCBIds(response);
                ncbIds.addAll(extractedNCBIds);

                // Debug Print: Check if IDs are stored
                System.out.println("📌 Stored NCB IDs: " + ncbIds);

            } else {
                System.err.println("❌ NCB API Call Failed!");
            }
        } catch (Exception e) {
            System.err.println("❌ Error in NCB API Call: " + e.getMessage());
        }
    }

    // Extracts all Insurer IDs from API response
    private static List<Integer> extractInsurerIds(String response) {
        List<Integer> ids = new ArrayList<>();
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonResponse = objectMapper.readTree(response);
            JsonNode brandList = jsonResponse.path("data").path("brandList");

            if (brandList.isArray()) {
                for (JsonNode brand : brandList) {
                    int id = brand.path("id").asInt(0);
                    if (id != 0) {
                        ids.add(id);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error extracting Insurer IDs: " + e.getMessage());
        }
        return ids;
    }

    // Extracts all NCB IDs from API response
    private static List<Integer> extractNCBIds(String response) {
        List<Integer> ids = new ArrayList<>();
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonResponse = objectMapper.readTree(response);
            JsonNode ncbList = jsonResponse.path("data").path("ncbList");

            if (ncbList.isArray()) {
                for (JsonNode ncb : ncbList) {
                    int id = ncb.path("ncbId").asInt(0);
                    if (id != 0) {
                        ids.add(id);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error extracting NCB IDs: " + e.getMessage());
        }
        return ids;
    }


    public static void prequoteVehicleDetails(String authToken, String leadId) throws IOException {
        String url = getConfigValue("mvBaseUrl") + getConfigValue("pageData");
        Map<String, Object> requestBody = Map.of(
                "leadId", leadId,
                "pageType", "prequote-previous-policy-detail"
        );
        String response = sendPostRequest(url, requestBody, authToken, "Prequote Vehicle Details");

        if (response != null) {
            System.out.println("✅ Prequote Vehicle Details Success!");
            System.out.println("🔹 Response for Prequote: " + response);

        } else {
            System.err.println("❌ Prequote Vehicle Details Failed!");
        }
        updateLeadForPolicy(authToken);
    }

    private static void updateLeadForPolicy(String authToken) throws IOException {
        try {
            String selectedPolicy = getConfigValue("selectedPolicy").toLowerCase(); // Read from config.json

            // If "all" is selected, process all policies
            if (selectedPolicy.equals("all")) {
                updateForPolicy(authToken, "comprehensive");
                updateForPolicy(authToken, "tp");
                updateForPolicy(authToken, "saod");
                return; // Early return after processing all policies
            }

            // Validate if selectedPolicy is one of the allowed types
            if (!selectedPolicy.equals("comprehensive") &&
                    !selectedPolicy.equals("tp") &&
                    !selectedPolicy.equals("saod")) {
                System.err.println("❌ Invalid or unsupported policy type: " + selectedPolicy);
                return;
            }

            // Process the selected policy
            updateForPolicy(authToken, selectedPolicy);

        } catch (Exception e) {
            System.err.println("❌ Error updating " + getConfigValue("selectedPolicy") + ": " + e.getMessage());
        }
    }

    private static void updateForPolicy(String authToken, String selectedPolicy) throws IOException {
        String leadId = getLeadIdForPolicy(selectedPolicy);

        if (leadId == null || leadId.isEmpty()) {
            System.err.println("❌ No Lead ID found for policy: " + selectedPolicy);
            return;
        }

        if (insurerIds.isEmpty() || ncbIds.isEmpty()) {
            System.err.println("⚠️ Cannot update lead: Insurer or NCB list is empty!");
            return;
        }

        if (insurerIndex < 0 || insurerIndex >= insurerIds.size()) {
            System.err.println("❌ Invalid insurerIndex: " + insurerIndex);
            return;
        }
        if (ncbIndex < 0 || ncbIndex >= ncbIds.size()) {
            System.err.println("❌ Invalid ncbIndex: " + ncbIndex);
            return;
        }

        int selectedInsurerId = insurerIds.get(insurerIndex);
        int selectedNCBId = determineNCBId(selectedPolicy);

        // Construct the request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("leadId", leadId);
        requestBody.put("insurerId", selectedInsurerId);
        requestBody.put("ncbId", selectedNCBId);

        // Date formatting for previous policy details
        LocalDate baseDate = LocalDate.now().plusDays(15);
        String formattedBaseDate = baseDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String formattedTPExpiryDate = selectedPolicy.equals("saod")
                ? baseDate.plusYears(2).format(DateTimeFormatter.ISO_LOCAL_DATE)
                : formattedBaseDate;

        // Lead details
        Map<String, Object> leadDetails = new HashMap<>();
        leadDetails.put("previousODExpiryDate", formattedBaseDate);
        leadDetails.put("previousTPExpiryDate", formattedTPExpiryDate);
        leadDetails.put("hasPreviousPolicy", true);
        leadDetails.put("previousPolicyType", selectedPolicy);
        leadDetails.put("previousInsurerId", selectedInsurerId);
        leadDetails.put("previousNCBId", selectedNCBId);
        leadDetails.put("ownershipTransfer", false);
        leadDetails.put("leadSource", "rm");

        requestBody.put("leadDetails", leadDetails);

        // Fetch and validate config values for the API URL
        String baseUrl = getConfigValue("mvBaseUrl");
        String updateLeadEndpoint = getConfigValue("updateLeadDetails");

        if (baseUrl == null || updateLeadEndpoint == null) {
            System.err.println("❌ mvBaseUrl or updateLead endpoint is not configured properly!");
            return;
        }

        // Form the complete URL
        String url = baseUrl + updateLeadEndpoint;

        // Print the URL and request body being sent
        System.out.println("🔗 Sending request to URL: " + url);
        System.out.println("📤 Request Body: " + requestBody);

        // Sending the POST request
        String response = sendPostRequest(url, requestBody, authToken, "updateLeadDetails");

        // Print the response or failure message
        if (response != null) {
            System.out.println("✅ Lead Updated Successfully for " + selectedPolicy);
            System.out.println("📌 Lead ID: " + leadId + " (Policy: " + selectedPolicy + ")");
            System.out.println("📌 Selected Insurer ID: " + selectedInsurerId + " (Position: " + insurerIndex + ")");
            System.out.println("📌 Selected NCB ID: " + selectedNCBId + " (Position: " + ncbIndex + ")");
            System.out.println("📆 Previous OD Expiry Date: " + formattedBaseDate);
            System.out.println("📆 Previous TP Expiry Date: " + formattedTPExpiryDate);
            System.out.println("🔍 API Response: " + response);
        } else {
            System.err.println("❌ Update Lead API Call Failed for " + selectedPolicy);
        }
    }



    private static int determineNCBId(String policyType) {
        switch (policyType.toLowerCase()) {
            case "comprehensive":
                return ncbIds.get(ncbIndex);
            case "tp":
                return (ncbIndex >= 1) ? ncbIds.get(1) : ncbIds.get(0);
            case "saod":
                return (ncbIndex >= 2) ? ncbIds.get(ncbIndex - 2) : ncbIds.get(0);
            default:
                System.err.println("❌ Unknown policy type: " + policyType);
                return 0;
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

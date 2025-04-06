package Downgrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.sql.*;
import java.util.*;

public class Downgrade {

    private String stagingDbUrl;
    private String stagingDbUser;
    private String stagingDbPass;

    // 🔹 Global gpuid
    private static String gpuid = "F9AC3501";

    public static void main(String[] args) throws Exception {
        new Downgrade().process();
    }

    public void process() throws Exception {
        loadDBConfig();

        ObjectMapper mapper = new ObjectMapper();
        InputStream inputStream = new ClassPathResource("downgrade.json").getInputStream();
        JsonNode config = mapper.readTree(inputStream);

        String baseUrl = config.get("baseUrl").asText();
        String getEndpoint = config.get("get").asText();
        String submitPath = config.get("submit").asText();
        String cancelPath = config.get("cancel").asText();
        String token = config.get("token").asText();
        String idempotencyKey = config.get("idempotencyKey").asText();
        int threshold = config.get("saleThreshold").asInt();

        String fullGetUrl = baseUrl + getEndpoint;

        // 🔹 Call GET API
        Map<String, Object> getBody = Map.of("idempotencyKey", idempotencyKey);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", token);
        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<String> getResponse = restTemplate.exchange(
                fullGetUrl,
                HttpMethod.POST,
                new HttpEntity<>(getBody, headers),
                String.class
        );

        JsonNode responseJson = mapper.readTree(getResponse.getBody());
        double saleEarning = responseJson.path("data").path("saleEarning").asDouble();
//        gpuid = responseJson.path("data").path("gpuid").asText(); // 🔸 Set the global gpuid

        System.out.println("✅ saleEarning: " + saleEarning);
//        System.out.println("✅ gpuid: " + gpuid);

        // ✅ Query wallet data from staging DB
        queryWalletData();

        // ✅ Make decision
        if (saleEarning < threshold) {
            callSubmitAPI(baseUrl + submitPath, token);
        } else {
            callCancelAPI(baseUrl + cancelPath, token, idempotencyKey);
        }
    }

    private void loadDBConfig() {
        try (InputStream input = new ClassPathResource("application.properties").getInputStream()) {
            Properties prop = new Properties();
            prop.load(input);

            String host = prop.getProperty("staging.datasource.host");
            String port = prop.getProperty("staging.datasource.port");
            String wallet = prop.getProperty("staging.datasource.wallet");

            stagingDbUser = prop.getProperty("staging.datasource.username");
            stagingDbPass = prop.getProperty("staging.datasource.password");
            stagingDbUrl = "jdbc:mysql://" + host + ":" + port + "/" + wallet + "?useSSL=false&serverTimezone=UTC";

            System.out.println("✅ Loaded staging DB config");
        } catch (IOException e) {
            throw new RuntimeException("❌ Failed to load DB config from application.properties", e);
        }
    }

    private void callSubmitAPI(String url, String token) {
        try {
            Map<String, Object> data = Map.of("gpuid", gpuid);

            Map<String, Object> body = new HashMap<>();
            body.put("definitionName", "testing");
            body.put("data", data);
            body.put("idempotencyKey", "gab_downgrade_" + gpuid);
            body.put("destinationType", "webhook");
            body.put("destinationURL", "api-stg.gromo.in/api/v2/tier/downgrade");
            body.put("repeatFrequencyType", null);
            body.put("repeatFrequencyUnit", null);
            body.put("repeatFrequency", null);
            body.put("repeatOn", null);
            body.put("repeatOnUnit", null);
            body.put("startTimeInterval", null);
            body.put("endTimeInterval", null);
            body.put("startOnDateTime", "2023-09-19T07:20:54.433Z");
            body.put("endOnDateTime", null);
            body.put("endType", "never");
            body.put("endAfterNumberOfOcurrence", null);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", token);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = new RestTemplate().exchange(url, HttpMethod.POST, request, String.class);
            System.out.println("✅ Submit API response:\n" + response.getBody());

        } catch (Exception e) {
            System.out.println("❌ Error calling Submit API: " + e.getMessage());
        }
    }

    private void callCancelAPI(String url, String token, String idempotencyKey) {
        try {
            Map<String, Object> body = Map.of("idempotencyKey", idempotencyKey);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", token);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = new RestTemplate().exchange(url, HttpMethod.POST, request, String.class);
            System.out.println("✅ Cancel API response:\n" + response.getBody());

        } catch (Exception e) {
            System.out.println("❌ Error calling Cancel API: " + e.getMessage());
        }
    }

    private void queryWalletData() {
        try (Connection conn = DriverManager.getConnection(stagingDbUrl, stagingDbUser, stagingDbPass)) {
            String queryMaster = "SELECT * FROM wallet_master WHERE gpUId = ?";
            try (PreparedStatement ps = conn.prepareStatement(queryMaster)) {
                ps.setString(1, gpuid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String walletUniqueId = rs.getString("wallet_unique_id");
                        System.out.println("🔎 wallet_unique_id: " + walletUniqueId);
                        queryWalletTransactions(conn, walletUniqueId);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("❌ DB Error: " + e.getMessage());
        }
    }

    private void queryWalletTransactions(Connection conn, String walletUniqueId) throws SQLException {
        String txQuery = "SELECT * FROM wallet_transactions WHERE source_wallet_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(txQuery)) {
            ps.setString(1, walletUniqueId);
            try (ResultSet rs = ps.executeQuery()) {
                System.out.println("🧾 Transactions for source_wallet_id = " + walletUniqueId + ":");
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();

                while (rs.next()) {
                    for (int i = 1; i <= cols; i++) {
                        System.out.print(meta.getColumnName(i) + ": " + rs.getObject(i) + " | ");
                    }
                    System.out.println();
                }
            }
        }
    }
}

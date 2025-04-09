package Downgrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public class DowngradeWithMultipleGPs {

    private String stagingDbUrl;
    private String stagingDbUser;
    private String stagingDbPass;

    private String gpuid;
    private int monthsBefore;
    private LocalDate createdAt;

    public static void main(String[] args) {
        List<String> gpuids = List.of("MJ3A0908", "WU3N6709", "NN9Q1118", "4EXA9868", "HC9E3636", "YJ7G2429");

        DowngradeWithMultipleGPs processor = new DowngradeWithMultipleGPs();

        for (String id : gpuids) {
            try {
                System.out.println("\n==============================");
                System.out.println("⚙️  Processing GP: " + id);
                System.out.println("==============================\n");
                processor.setGpuid(id);
                processor.process();
            } catch (Exception e) {
                System.out.println("❌ Error processing gpuid " + id);
                e.printStackTrace();
            }
        }
    }

    public void setGpuid(String gpuid) {
        this.gpuid = gpuid;
    }

    public void process() throws Exception {
        System.out.println("🚀 Starting Downgrade process for: " + gpuid);
        loadDBConfig();

        monthsBefore = calculateMonthsBeforeFromCreatedAt(); // Sets createdAt

        ObjectMapper mapper = new ObjectMapper();
        InputStream inputStream = new ClassPathResource("downgrade.json").getInputStream();
        JsonNode config = mapper.readTree(inputStream);

        String baseUrl = config.get("baseUrl").asText();
        String getPath = config.get("get").asText();
        String submitPath = config.get("submit").asText();
        String cancelPath = config.get("cancel").asText();
        String token = config.get("token").asText();
        int threshold = config.get("saleThreshold").asInt();

        String idempotencyKey = "gab_downgrade_" + gpuid;
        callSubmitAPI(baseUrl + submitPath, token);

        LocalDate today = LocalDate.now();
        int currentMonth = today.getMonthValue();
        int createdMonth = createdAt.getMonthValue();

        LocalDate windowStart = today.minusMonths(monthsBefore).withDayOfMonth(1);
        LocalDate windowEnd = windowStart.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth());
        LocalDate triggerDate = windowEnd.plusMonths(1).withDayOfMonth(10);

        System.out.println("🔔 Initial Trigger Date: " + triggerDate);

        LocalDate updatedAt = getUpdatedAtFromWalletMaster(); // ✅ new

        int loop = 0;
        while (true) {
            System.out.println("\n📅 Evaluation Window: " + windowStart + " to " + windowEnd);
            if (updatedAt.isBefore(windowStart)) {
                System.out.println("🛑 updated_at is before the evaluation window. Stopping loop.");
                break;
            }

            if (updatedAt.isAfter(windowEnd)) {
                System.out.println("🛑 updated_at is after evaluation window end. Stopping loop.");
                break;
            }

            double currentBalanceSum = getCurrentBalanceFromWalletMaster();
            System.out.println("💰 Total Current Balance from Wallet Master: ₹" + currentBalanceSum);

            if (loop > 0 && createdMonth == currentMonth && windowStart.getMonthValue() > currentMonth) {
                System.out.println("🛑 Evaluation window crosses current month. Stopping loop and calling GET API.");
                break;
            }

            if (currentBalanceSum >= threshold) {
                System.out.println("🚫 Threshold met. Calling Cancel API...");
                callCancelAPI(baseUrl, token, cancelPath, idempotencyKey);
                callSubmitAPI(baseUrl + submitPath, token);

                windowStart = windowStart.plusMonths(1);
                windowEnd = windowEnd.plusMonths(1);
                triggerDate = windowEnd.plusMonths(1).withDayOfMonth(10);
                System.out.println("🔔 Next Trigger Date: " + triggerDate);
            } else {
                System.out.println("✅ Threshold not met. Proceeding with downgrade.");
                break;
            }

            loop++;
        }

        System.out.println("🔔 Final Trigger Date: " + triggerDate);
        callGetAPI(baseUrl, token, getPath);
    }

    private int calculateMonthsBeforeFromCreatedAt() {
        try (Connection conn = DriverManager.getConnection(stagingDbUrl, stagingDbUser, stagingDbPass)) {
            String query = "SELECT created_at FROM wallet_master WHERE gpUId = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, gpuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Timestamp createdAtTimestamp = rs.getTimestamp("created_at");
                        if (createdAtTimestamp == null) {
                            throw new RuntimeException("❌ created_at is null for gpuid: " + gpuid);
                        }

                        createdAt = createdAtTimestamp.toLocalDateTime().toLocalDate();
                        int createdMonth = createdAt.getMonthValue();
                        int currentMonth = LocalDate.now().getMonthValue();
                        int diff = currentMonth - createdMonth;
                        if (diff < 0) diff += 12;

                        System.out.println("🗓️ Created At: " + createdAt);
                        System.out.println("📆 Created Month: " + createdMonth);
                        System.out.println("📆 Current Month: " + currentMonth);
                        System.out.println("📊 Calculated monthsBefore: " + diff);

                        return diff;
                    } else {
                        throw new RuntimeException("❌ No wallet_master record found for gpuid: " + gpuid);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("❌ Error calculating monthsBefore for gpuid: " + gpuid, e);
        }
    }

    private LocalDate getUpdatedAtFromWalletMaster() {
        try (Connection conn = DriverManager.getConnection(stagingDbUrl, stagingDbUser, stagingDbPass)) {
            String query = "SELECT updated_at FROM wallet_master WHERE gpUId = ? ORDER BY updated_at DESC LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, gpuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Timestamp updatedAtTs = rs.getTimestamp("updated_at");
                        if (updatedAtTs == null) {
                            throw new RuntimeException("❌ updated_at is null for gpuid: " + gpuid);
                        }
                        LocalDate updatedAt = updatedAtTs.toLocalDateTime().toLocalDate();
                        System.out.println("🕒 Latest Updated At: " + updatedAt);
                        return updatedAt;
                    } else {
                        throw new RuntimeException("❌ No updated_at found for gpuid: " + gpuid);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("❌ Error fetching updated_at for gpuid: " + gpuid, e);
        }
    }

    private double getCurrentBalanceFromWalletMaster() {
        double sum = 0;
        try (Connection conn = DriverManager.getConnection(stagingDbUrl, stagingDbUser, stagingDbPass)) {
            String query = "SELECT current_balance, wallet_unique_id FROM wallet_master WHERE gpUId = ?";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, gpuid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String walletId = rs.getString("wallet_unique_id");
                        if (walletId.startsWith("SC_")) continue;
                        sum += rs.getDouble("current_balance");
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Error fetching current balance: " + e.getMessage());
        }
        return sum;
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
            body.put("startOnDateTime", LocalDateTime.now().plusDays(1) + ".000Z");
            body.put("endType", "never");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", token);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            System.out.println("\n📤 SUBMIT API CALL:");
            System.out.println("URL: " + url);
            System.out.println("Headers: " + headers.toSingleValueMap());
            System.out.println("Body: " + new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(body));

            ResponseEntity<String> response = new RestTemplate().exchange(url, HttpMethod.POST, request, String.class);
            System.out.println("✅ Submit API response:\n" + response.getBody());
        } catch (Exception e) {
            System.out.println("❌ Submit API Error: " + e.getMessage());
        }
    }

    private void callCancelAPI(String baseUrl, String token, String cancelPath, String idempotencyKey) {
        try {
            String url = baseUrl + cancelPath;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", token);

            Map<String, String> body = Map.of("idempotencyKey", idempotencyKey);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            System.out.println("\n📤 CANCEL API CALL:");
            System.out.println("URL: " + url);
            System.out.println("Headers: " + headers.toSingleValueMap());
            System.out.println("Body: " + new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(body));

            ResponseEntity<String> response = new RestTemplate().exchange(url, HttpMethod.POST, request, String.class);
            System.out.println("🚫 Cancel API Response:\n" + response.getBody());
        } catch (Exception e) {
            System.out.println("❌ Cancel API Error: " + e.getMessage());
        }
    }

    private void callGetAPI(String baseUrl, String token, String getPath) {
        try {
            String idempotencyKey = "gab_downgrade_" + gpuid;
            String fullUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
            fullUrl += getPath.endsWith("/") ? getPath : getPath + "/";
            fullUrl += idempotencyKey;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", token);

            HttpEntity<String> request = new HttpEntity<>("", headers);

            System.out.println("\n📤 GET API CALL:");
            System.out.println("URL: " + fullUrl);
            System.out.println("Headers: " + headers.toSingleValueMap());

            ResponseEntity<String> response = new RestTemplate().exchange(fullUrl, HttpMethod.GET, request, String.class);
            System.out.println("🔄 GET API Response:\n" + response.getBody());
        } catch (Exception e) {
            System.out.println("❌ GET API Error: " + e.getMessage());
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
            throw new RuntimeException("❌ Failed to load DB config", e);
        }
    }
}

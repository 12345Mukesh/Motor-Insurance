package Downgrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.sql.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public class Downgrade {

    private String stagingDbUrl;
    private String stagingDbUser;
    private String stagingDbPass;

    private static String gpuid = "OJKI9316";

    public static void main(String[] args) {
        try {
            new Downgrade().process();
        } catch (Exception e) {
            System.out.println("❌ Unhandled Error in main:");
            e.printStackTrace();
        }
    }

    public void process() throws Exception {
        System.out.println("🚀 Starting Downgrade process...");
        loadDBConfig();

        System.out.println("🔍 Reading downgrade.json...");
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

        // 🔁 Compute triggerDate = 10th of month, 3 months from CURRENT month
        LocalDate today = LocalDate.now();
        LocalDate threeMonthsLater = today.withDayOfMonth(10).plusMonths(3);
        LocalDate triggerDate = threeMonthsLater.withDayOfMonth(10);

        System.out.println("⏳ Starting trigger date calculation from: " + triggerDate);

        boolean finalThresholdNotMet = false;

        while (true) {
            // 📅 Window: from 1st of 3 months before triggerDate to last day of month 1 month before triggerDate
            LocalDate windowStart = triggerDate.minusMonths(3).withDayOfMonth(1);
            LocalDate windowEnd = triggerDate.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth());

            System.out.println("📆 Evaluating window: " + windowStart + " to " + windowEnd);

            double[] windowTotals = queryWalletDataForWindow(windowStart, windowEnd);
            double monthlyTotal = windowTotals[0];

            System.out.println("💰 Window Total: ₹" + monthlyTotal);

            if (monthlyTotal >= threshold) {
                System.out.println("🛑 Threshold met, calling Cancel API before continuing...");
                callCancelAPI(baseUrl, token, cancelPath, idempotencyKey);
                // 🔁 Immediately resubmit
                callSubmitAPI(baseUrl + submitPath, token);
                System.out.println("🧮 Rechecking threshold after cancel...");
                triggerDate = triggerDate.plusMonths(1);
                System.out.println("➡️ Moving to next trigger date: " + triggerDate);
            } else {
                System.out.println("✅ Threshold not met. Final trigger date: " + triggerDate);
                printTransactions(windowStart, windowEnd);
                finalThresholdNotMet = true;
                break;
            }
        }

        if (finalThresholdNotMet) {
            System.out.println("🔎 Final check: calling GET API since last window did not meet threshold...");
            callGetAPI(baseUrl, token, getPath);
        }
    }


    private double[] queryWalletDataForWindow(LocalDate startDate, LocalDate endDate) {
        double sum = 0;

        try (Connection conn = DriverManager.getConnection(stagingDbUrl, stagingDbUser, stagingDbPass)) {
            String query = "SELECT wallet_unique_id FROM wallet_master WHERE gpUId = ?";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, gpuid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String walletUniqueId = rs.getString("wallet_unique_id");
                        if (walletUniqueId.startsWith("SC_")) continue;
                        double walletTotal = queryWalletTransactionsInWindow(conn, walletUniqueId, startDate, endDate);
                        sum += walletTotal;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Error querying window: " + e.getMessage());
        }

        return new double[]{sum};
    }

    private double queryWalletTransactionsInWindow(Connection conn, String walletUniqueId, LocalDate start, LocalDate end) throws SQLException {
        double total = 0;

        String txQuery = "SELECT transaction_amount FROM wallet_transactions " +
                "WHERE source_wallet_id = ? AND transaction_direction = 'in' " +
                "AND transaction_date BETWEEN ? AND ?";

        try (PreparedStatement ps = conn.prepareStatement(txQuery)) {
            ps.setString(1, walletUniqueId);
            ps.setTimestamp(2, Timestamp.valueOf(start.atStartOfDay()));
            ps.setTimestamp(3, Timestamp.valueOf(end.atTime(LocalTime.MAX)));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    total += rs.getDouble("transaction_amount");
                }
            }
        }

        return total;
    }

    private void printTransactions(LocalDate start, LocalDate end) {
        System.out.println("📋 Transactions between " + start + " and " + end + ":");

        try (Connection conn = DriverManager.getConnection(stagingDbUrl, stagingDbUser, stagingDbPass)) {
            String walletQuery = "SELECT wallet_unique_id FROM wallet_master WHERE gpUId = ?";
            try (PreparedStatement walletStmt = conn.prepareStatement(walletQuery)) {
                walletStmt.setString(1, gpuid);
                try (ResultSet walletRs = walletStmt.executeQuery()) {
                    while (walletRs.next()) {
                        String walletId = walletRs.getString("wallet_unique_id");
                        if (walletId.startsWith("SC_")) continue;

                        String txQuery = "SELECT * FROM wallet_transactions " +
                                "WHERE source_wallet_id = ? AND transaction_direction = 'in' " +
                                "AND transaction_date BETWEEN ? AND ?";

                        try (PreparedStatement txStmt = conn.prepareStatement(txQuery)) {
                            txStmt.setString(1, walletId);
                            txStmt.setTimestamp(2, Timestamp.valueOf(start.atStartOfDay()));
                            txStmt.setTimestamp(3, Timestamp.valueOf(end.atTime(LocalTime.MAX)));

                            try (ResultSet txRs = txStmt.executeQuery()) {
                                ResultSetMetaData rsmd = txRs.getMetaData();
                                int columnCount = rsmd.getColumnCount();
                                while (txRs.next()) {
                                    StringBuilder sb = new StringBuilder();
                                    for (int i = 1; i <= columnCount; i++) {
                                        sb.append(rsmd.getColumnName(i)).append(": ").append(txRs.getString(i)).append("\t");
                                    }
                                    System.out.println(sb);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Error printing transactions: " + e.getMessage());
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

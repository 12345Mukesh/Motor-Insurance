package Downgrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.IntStream;

public class DowngradeWithPreviousMonths {

    private String stagingDbUrl;
    private String stagingDbUser;
    private String stagingDbPass;

    private static final String gpuid = "MBDQ4734";
    private static final int monthsBefore = 2; // Change this to 1 or 2 based on requirement

    public static void main(String[] args) {
        try {
            new DowngradeWithPreviousMonths().process();
        } catch (Exception e) {
            System.out.println("\u274C Unhandled Error in main:");
            e.printStackTrace();
        }
    }

    public void process() throws Exception {
        System.out.println("\uD83D\uDE80 Starting Downgrade process...");
        loadDBConfig();

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

        // Dynamic window start and end based on monthsBefore
        LocalDate windowStart = today.minusMonths(monthsBefore).withDayOfMonth(1);
        LocalDate windowEnd = windowStart.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth());
        LocalDate triggerDate = windowEnd.plusMonths(1).withDayOfMonth(10);

        System.out.println("\uD83D\uDD14 Initial Trigger Date: " + triggerDate);

        while (true) {
            System.out.println("\n\uD83D\uDCC6 Evaluation Window: " + windowStart + " to " + windowEnd);
            double windowTotal = queryWalletDataForWindow(windowStart, windowEnd);
            System.out.println("\uD83D\uDCB0 Total Amount from Wallet Transactions: ₹" + windowTotal);
            printTransactions(windowStart, windowEnd);

            if (windowTotal >= threshold) {
                System.out.println("\uD83D\uDEAB Threshold met. Calling Cancel API...");
                callCancelAPI(baseUrl, token, cancelPath, idempotencyKey);

                // 🔁 Immediately resubmit
                callSubmitAPI(baseUrl + submitPath, token);

                // Shift window forward
                windowStart = windowStart.plusMonths(1);
                windowEnd = windowEnd.plusMonths(1);
                triggerDate = windowEnd.plusMonths(1).withDayOfMonth(10);

                System.out.println("\uD83D\uDD14 Next Trigger Date: " + triggerDate);
            } else {
                System.out.println("\u2705 Threshold not met. Proceeding with downgrade.");
                break;
            }
        }

        System.out.println("\uD83D\uDD14 Final Trigger Date: " + triggerDate);
        callGetAPI(baseUrl, token, getPath);
    }

    private double queryWalletDataForWindow(LocalDate startDate, LocalDate endDate) {
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
            System.out.println("\u274C Error querying wallet data: " + e.getMessage());
        }
        return sum;
    }

    private double queryWalletTransactionsInWindow(Connection conn, String walletId, LocalDate start, LocalDate end) throws SQLException {
        double total = 0;
        String query = "SELECT transaction_amount FROM wallet_transactions WHERE source_wallet_id = ? AND transaction_direction = 'in' AND transaction_date BETWEEN ? AND ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, walletId);
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
        System.out.println("\uD83D\uDCCB Transactions from " + start + " to " + end + ":");
        try (Connection conn = DriverManager.getConnection(stagingDbUrl, stagingDbUser, stagingDbPass)) {
            String walletQuery = "SELECT wallet_unique_id FROM wallet_master WHERE gpUId = ?";
            try (PreparedStatement walletStmt = conn.prepareStatement(walletQuery)) {
                walletStmt.setString(1, gpuid);
                try (ResultSet walletRs = walletStmt.executeQuery()) {
                    while (walletRs.next()) {
                        String walletId = walletRs.getString("wallet_unique_id");
                        if (walletId.startsWith("SC_")) continue;

                        String txQuery = "SELECT * FROM wallet_transactions WHERE source_wallet_id = ? AND transaction_direction = 'in' AND transaction_date BETWEEN ? AND ?";
                        try (PreparedStatement txStmt = conn.prepareStatement(txQuery)) {
                            txStmt.setString(1, walletId);
                            txStmt.setTimestamp(2, Timestamp.valueOf(start.atStartOfDay()));
                            txStmt.setTimestamp(3, Timestamp.valueOf(end.atTime(LocalTime.MAX)));

                            try (ResultSet txRs = txStmt.executeQuery()) {
                                ResultSetMetaData rsmd = txRs.getMetaData();
                                int colCount = rsmd.getColumnCount();
                                while (txRs.next()) {
                                    IntStream.rangeClosed(1, colCount)
                                            .mapToObj(i -> {
                                                try {
                                                    return txRs.getString(i);
                                                } catch (SQLException e) {
                                                    return "ERROR";
                                                }
                                            })
                                            .forEach(val -> System.out.print(val + "\t"));
                                    System.out.println();
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("\u274C Error printing transactions: " + e.getMessage());
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

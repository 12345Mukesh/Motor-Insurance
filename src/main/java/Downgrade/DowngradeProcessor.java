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

public class DowngradeProcessor {

    private static final String GPU_ID = "GDQW1894";
    private static final String IDP_KEY_PREFIX = "gab_downgrade_";

    private String stagingDbUrl;
    private String stagingDbUser;
    private String stagingDbPass;
    private JsonNode config;
    private final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        try {
            new DowngradeProcessor().run();
        } catch (Exception e) {
            System.err.println("\u274C Unhandled Error in main:");
            e.printStackTrace();
        }
    }

    public void run() throws Exception {
        loadConfigurations();

        LocalDate createdAt = fetchWalletCreationDate();
        if (createdAt == null) {
            System.err.println("❌ Created_at not found for gpuid: " + GPU_ID);
            return;
        }

        LocalDate windowStart = createdAt.withDayOfMonth(1); // e.g., 2025-02-01
        LocalDate triggerDate = windowStart.plusMonths(3).withDayOfMonth(10); // e.g., 2025-05-10
        LocalDate today = LocalDate.now();

        System.out.println("🧾 Created At: " + createdAt);
        System.out.println("🔔 Initial Trigger Date: " + triggerDate);

        // Initial submit
        callSubmitAPI();

        while (true) {
            // Break if current evaluation window is in future
            if (windowStart.isAfter(today.withDayOfMonth(1))) break;

            LocalDate month1 = windowStart;
            LocalDate month2 = windowStart.plusMonths(1);
            LocalDate month3 = windowStart.plusMonths(2);
            LocalDate windowEnd = month3.with(TemporalAdjusters.lastDayOfMonth());

            System.out.println("\n📆 Evaluation Window: " + month1 + " to " + windowEnd);
            double totalAmount = queryWalletTotal(windowStart, windowEnd);
            System.out.println("💰 Total Wallet Transactions: ₹" + totalAmount);

            // Month 1
            System.out.println("📋 Transactions for: " + month1.getMonth());
            printTransactionDetails(month1, month1.with(TemporalAdjusters.lastDayOfMonth()));

            // Month 2 (if not in future)
            if (!month2.isAfter(today)) {
                System.out.println("📋 Transactions for: " + month2.getMonth());
                printTransactionDetails(month2, month2.with(TemporalAdjusters.lastDayOfMonth()));
            }

            // Month 3 (if not in future)
            if (!month3.isAfter(today)) {
                System.out.println("📋 Transactions for: " + month3.getMonth());
                printTransactionDetails(month3, month3.with(TemporalAdjusters.lastDayOfMonth()));
            }

            if (totalAmount >= config.get("saleThreshold").asDouble()) {
                triggerDate = triggerDate.plusMonths(1);
                System.out.println("🚀 Threshold met. Updating trigger date to: " + triggerDate);
                callCancelAPI();
                callSubmitAPI();
            } else {
                System.out.println("✅ Threshold not met for this window.");
            }

            windowStart = windowStart.plusMonths(1);
        }

        System.out.println("\n🔔 Final Trigger Date: " + triggerDate);
        printUpcomingDowngradeInfo(); // 👈 Add this line
        callGetAPI();
    }


    private void printUpcomingDowngradeInfo() {
        String query = "SELECT tierId, downgradeDate FROM gromo_payout.user_tier_mappings WHERE userId = ? ORDER BY downgradeDate DESC LIMIT 1";
        try (Connection conn = DriverManager.getConnection(stagingDbUrl, stagingDbUser, stagingDbPass);
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, GPU_ID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int tierId = rs.getInt("tierId");
                    Timestamp downgradeDateTs = rs.getTimestamp("downgradeDate");
                    LocalDateTime downgradeDate = downgradeDateTs != null ? downgradeDateTs.toLocalDateTime() : null;
                    if (downgradeDate != null) {
                        System.out.printf("📌 Current user is in Tier %d as of today (%s)%n", tierId, LocalDate.now());
                        System.out.printf("📉 User will downgrade to Tier %d on %s%n", tierId - 1, downgradeDate.toLocalDate() + " db date");
                    } else {
                        System.out.println("⚠️ No downgrade date available for user.");
                    }
                } else {
                    System.out.println("⚠️ No user_tier_mapping entry found for gpuid: " + GPU_ID);
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error fetching downgrade info: " + e.getMessage());
        }
    }



    private void loadConfigurations() throws IOException {
        loadDbConfig();
        try (InputStream input = new ClassPathResource("downgrade.json").getInputStream()) {
            this.config = mapper.readTree(input);
        }
    }

    private void loadDbConfig() {
        try (InputStream input = new ClassPathResource("application.properties").getInputStream()) {
            Properties prop = new Properties();
            prop.load(input);

            stagingDbUser = prop.getProperty("staging.datasource.username");
            stagingDbPass = prop.getProperty("staging.datasource.password");
            stagingDbUrl = String.format("jdbc:mysql://%s:%s/%s?useSSL=false&serverTimezone=UTC",
                    prop.getProperty("staging.datasource.host"),
                    prop.getProperty("staging.datasource.port"),
                    prop.getProperty("staging.datasource.wallet"));

            System.out.println("✅ Loaded staging DB config");
        } catch (IOException e) {
            throw new RuntimeException("❌ Failed to load DB config", e);
        }
    }

    private LocalDate fetchWalletCreationDate() {
        String query = "SELECT created_at FROM wallet_master WHERE gpUId = ?";
        try (Connection conn = DriverManager.getConnection(stagingDbUrl, stagingDbUser, stagingDbPass);
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, GPU_ID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getTimestamp("created_at").toLocalDateTime().toLocalDate();
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error fetching created_at: " + e.getMessage());
        }
        return null;
    }

    private void evaluateWindow(LocalDate windowStart) {
        LocalDate windowEnd = windowStart.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth());
        double totalAmount = queryWalletTotal(windowStart, windowEnd);

        System.out.println("\n📆 Evaluation Window: " + windowStart + " to " + windowEnd);
        System.out.println("💰 Total Wallet Transactions: ₹" + totalAmount);
        printTransactionDetails(windowStart, windowEnd);

        if (totalAmount >= config.get("saleThreshold").asDouble()) {
            System.out.println("🚫 Threshold met. Cancelling downgrade and resubmitting...");
            callCancelAPI();
            callSubmitAPI();
        } else {
            System.out.println("✅ Threshold not met for this window.");
        }
    }

    private double queryWalletTotal(LocalDate start, LocalDate end) {
        double total = 0;
        try (Connection conn = DriverManager.getConnection(stagingDbUrl, stagingDbUser, stagingDbPass)) {
            String query = "SELECT wallet_unique_id FROM wallet_master WHERE gpUId = ?";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, GPU_ID);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String walletId = rs.getString("wallet_unique_id");
                        if (!walletId.startsWith("SC_")) {
                            total += sumTransactions(conn, walletId, start, end);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error querying wallet data: " + e.getMessage());
        }
        return total;
    }

    private double sumTransactions(Connection conn, String walletId, LocalDate start, LocalDate end) throws SQLException {
        String query = "SELECT transaction_amount FROM wallet_transactions WHERE source_wallet_id = ? AND transaction_direction = 'in' AND transaction_date BETWEEN ? AND ?";
        double total = 0;
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

    private void printTransactionDetails(LocalDate start, LocalDate end) {
        System.out.println("\uD83D\uDCCB Transactions from " + start + " to " + end + ":");
        try (Connection conn = DriverManager.getConnection(stagingDbUrl, stagingDbUser, stagingDbPass)) {
            String query = "SELECT wallet_unique_id FROM wallet_master WHERE gpUId = ?";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, GPU_ID);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String walletId = rs.getString("wallet_unique_id");
                        if (!walletId.startsWith("SC_")) {
                            printTransactions(conn, walletId, start, end);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error printing transactions: " + e.getMessage());
        }
    }

    private void printTransactions(Connection conn, String walletId, LocalDate start, LocalDate end) throws SQLException {
        String query = "SELECT * FROM wallet_transactions WHERE source_wallet_id = ? AND transaction_direction = 'in' AND transaction_date BETWEEN ? AND ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, walletId);
            ps.setTimestamp(2, Timestamp.valueOf(start.atStartOfDay()));
            ps.setTimestamp(3, Timestamp.valueOf(end.atTime(LocalTime.MAX)));
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                while (rs.next()) {
                    IntStream.rangeClosed(1, columnCount)
                            .mapToObj(i -> safeGet(rs, i))
                            .forEach(val -> System.out.print(val + "\t"));
                    System.out.println();
                }
            }
        }
    }

    private String safeGet(ResultSet rs, int index) {
        try {
            return rs.getString(index);
        } catch (SQLException e) {
            return "ERROR";
        }
    }

    private void callSubmitAPI() {
        String url = config.get("baseUrl").asText() + config.get("submit").asText();
        Map<String, Object> data = Map.of(
                "definitionName", "testing",
                "data", Map.of("gpuid", GPU_ID),
                "idempotencyKey", IDP_KEY_PREFIX + GPU_ID,
                "destinationType", "webhook",
                "destinationURL", "api-stg.gromo.in/api/v2/tier/downgrade",
                "startOnDateTime", LocalDateTime.now().plusDays(1) + ".000Z",
                "endType", "never"
        );
        postJson(url, config.get("token").asText(), data, "SUBMIT");
    }

    private void callCancelAPI() {
        String url = config.get("baseUrl").asText() + config.get("cancel").asText();
        Map<String, Object> data = Map.of("idempotencyKey", IDP_KEY_PREFIX + GPU_ID);
        postJson(url, config.get("token").asText(), data, "CANCEL");
    }

    private void callGetAPI() {
        String url = config.get("baseUrl").asText() + "/" + config.get("get").asText() + "/" + IDP_KEY_PREFIX + GPU_ID;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", config.get("token").asText());

        HttpEntity<String> request = new HttpEntity<>("", headers);
        try {
            ResponseEntity<String> response = new RestTemplate().exchange(url, HttpMethod.GET, request, String.class);
            System.out.println("🔄 GET API Response:\n" + response.getBody());
        } catch (Exception e) {
            System.err.println("❌ GET API Error: " + e.getMessage());
        }
    }

    private void postJson(String url, String token, Map<String, ?> data, String label) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", token);

        HttpEntity<Map<String, ?>> request = new HttpEntity<>(data, headers);

        System.out.println("\n📤 " + label + " API CALL:");
        System.out.println("URL: " + url);
        System.out.println("Headers: " + headers.toSingleValueMap());
        try {
            System.out.println("Body: " + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data));
            ResponseEntity<String> response = new RestTemplate().exchange(url, HttpMethod.POST, request, String.class);
            System.out.println("✅ " + label + " API Response:\n" + response.getBody());
        } catch (Exception e) {
            System.err.println("❌ " + label + " API Error: " + e.getMessage());
        }
    }

    private void printEvaluationHeader(LocalDate windowStart, LocalDate createdAt, LocalDate triggerDate) {
        System.out.printf("📆 Downgrade will evaluate activity in: %s %d, %s %d, %s %d\n",
                windowStart.getMonth(), windowStart.getYear(),
                windowStart.plusMonths(1).getMonth(), windowStart.plusMonths(1).getYear(),
                windowStart.plusMonths(2).getMonth(), windowStart.plusMonths(2).getYear());

        System.out.println("🔔 Initial Trigger Date: " + triggerDate);
        System.out.println("🧾 Created At: " + createdAt);
    }
}

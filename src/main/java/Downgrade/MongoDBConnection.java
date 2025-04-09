package Downgrade;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import org.bson.Document;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

public class MongoDBConnection {

    private static final String MONGO_URI = "mongodb://appBackend:whf6C32BBppq2YHn.@gromo-staging-docdb.cgfbihskxfkk.ap-south-1.docdb.amazonaws.com:27017/gromo-staging?ssl=true&retryWrites=false";
    private static final String DATABASE_NAME = "gromo-staging";
    private static final String COLLECTION_NAME = "users";

    private MongoClient mongoClient;
    private MongoCollection<Document> usersCollection;

    public MongoDBConnection() {
        ConnectionString connString = new ConnectionString(MONGO_URI);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connString)
                .build();
        mongoClient = MongoClients.create(settings);
        MongoDatabase database = mongoClient.getDatabase(DATABASE_NAME);
        usersCollection = database.getCollection(COLLECTION_NAME);
    }

    public void fetchCreatedAtInfo(String gpuid) {
        try {
            Document query = new Document("gpUId", gpuid);
            Document user = usersCollection.find(query).first();

            if (user != null && user.getDate("createdAt") != null) {
                Date createdAtDate = user.getDate("createdAt");
                LocalDate createdAt = createdAtDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

                int year = createdAt.getYear();
                int month = createdAt.getMonthValue();  // store this
                int day = createdAt.getDayOfMonth();

                System.out.println("✅ Created At: " + createdAt);
                System.out.println("📆 Year: " + year);
                System.out.println("📆 Month: " + month);
                System.out.println("📆 Day: " + day);

                // You can now use `month` variable as needed
            } else {
                System.out.println("❌ User not found or createdAt is null");
            }
        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        MongoDBConnection fetcher = new MongoDBConnection();
        String gpuid = "EELW0180";  // Replace with actual gpUId
        fetcher.fetchCreatedAtInfo(gpuid);
    }
}

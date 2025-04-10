package Downgrade;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.Collections;

public class MongoDBConnection {
    public static void main(String[] args) {
        String username = "appBackend";
        String password = "whf6C32BBppq2YHn";
        String host = "gromo-staging-docdb.cgfbihskxfkk.ap-south-1.docdb.amazonaws.com";
        int port = 27017;

        MongoCredential credential = MongoCredential.createCredential(username, "admin", password.toCharArray());

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyToClusterSettings(builder ->
                        builder.hosts(Collections.singletonList(new ServerAddress(host, port))))
                .credential(credential)
                .applyToSslSettings(builder -> builder.enabled(true)) // Required by AWS DocumentDB
                .build();

        try (MongoClient mongoClient = MongoClients.create(settings)) {
            MongoDatabase db = mongoClient.getDatabase("gromo-staging"); // replace with your actual DB name
            MongoCollection<Document> usersCollection = db.getCollection("users");

            String gpuid = "EELW0180"; // Replace with actual gpuid value
            Document query = new Document("gpuid", gpuid);
            Document user = usersCollection.find(query).first();

            if (user != null) {
                System.out.println("User found: " + user.toJson());
            } else {
                System.out.println("No user found with gpuid: " + gpuid);
            }
        }
    }
}

package InsuranceWallet;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

public class DatabaseConfig {
    public static DataSource getDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://localhost:3306/insurance_wallet?useSSL=false&serverTimezone=UTC");
        dataSource.setUsername("root");
        dataSource.setPassword(""); // 🔴 Change if you have a password
        return dataSource;
    }
}
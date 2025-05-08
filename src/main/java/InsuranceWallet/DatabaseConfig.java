package InsuranceWallet;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

public class DatabaseConfig {
    public static DataSource getDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://insurance-mysql-stg.gromoinsure.co.in:3306/insurance_wallet?useSSL=false&serverTimezone=UTC");
        dataSource.setUsername("shaik");
        dataSource.setPassword("shaik@123"); // 🔴 Change if you have a password
        return dataSource;
    }
}
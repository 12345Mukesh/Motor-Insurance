package InsuranceWallet;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public class GpuIdInitialization
{
    public static void main(String[] args) throws InterruptedException {
        // Setup ChromeDriver automatically using WebDriverManager
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");  // Run without GUI
        // Initialize WebDriver
        WebDriver driver = new ChromeDriver(options);


        try {
            // Open Zoho CRM login page
            driver.get("https://accounts.zoho.in/signin?servicename=ZohoCRM");

            // Maximize the browser window
            driver.manage().window().maximize();
            Thread.sleep(2000); // Wait for page to load

            // Enter Username
            WebElement usernameField = driver.findElement(By.id("login_id"));
            usernameField.sendKeys("tech1@gromoinsure.in");

            // Click 'Next' Button
            WebElement nextButton = driver.findElement(By.id("nextbtn"));
            nextButton.click();
            Thread.sleep(2000); // Wait for password field to appear

            // Enter Password
            WebElement passwordField = driver.findElement(By.id("password"));
            passwordField.sendKeys("techrw@desk1234");

            // Click 'Sign In' Button
            WebElement signInButton = driver.findElement(By.id("nextbtn"));
            signInButton.click();
            Thread.sleep(5000); // Wait for login to complete

            System.out.println("Login Successful!");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Close the browser after execution (optional)
            driver.quit();
        }
    }



}

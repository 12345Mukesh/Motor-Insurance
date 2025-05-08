
package InsuranceWallet;

import io.appium.java_client.MobileElement;
import io.appium.java_client.TouchAction;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.touch.offset.PointOption;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

public class GromoPartnerTest {
    public static void main(String[] args) throws MalformedURLException, InterruptedException {
        DesiredCapabilities caps = new DesiredCapabilities();
        caps.setCapability("platformName", "Android");
        //personal avd code
        caps.setCapability("deviceName", "e5b2805c");
        caps.setCapability("udid", "e5b2805c");

//        //testing device avd code
//        caps.setCapability("deviceName", "VKLNGQ7TY9OBJVDI");
//        caps.setCapability("udid", "VKLNGQ7TY9OBJVDI");

        caps.setCapability("appPackage", "com.gromo.partner");
        //Enable only if using from home page, if not disable it
        caps.setCapability("appActivity", "com.gromo.partner.v2.ui.home.HomeActivity");
        caps.setCapability("automationName", "UiAutomator2");
        caps.setCapability("noReset", true);
        caps.setCapability("fullReset", false);

        URL url = new URL("http://127.0.0.1:4723");

        AndroidDriver<MobileElement> driver = new AndroidDriver<>(url, caps);
        WebDriverWait wait = new WebDriverWait(driver, 10);

        /*

       //Mobile number Enter
        MobileElement enter =  (MobileElement) wait.until(
                ExpectedConditions.presenceOfElementLocated(By.id("com.gromo.partner:id/mobileNumber")));
           enter.click();
           enter.sendKeys("5432133333");

           //clicking on next button
           Thread.sleep(5000);
           MobileElement next = (MobileElement) wait.until(
                   ExpectedConditions.presenceOfElementLocated(By.id("com.gromo.partner:id/next")));
               next.click();

               //otp entering
        Thread.sleep(5000);
        MobileElement otp = (MobileElement) wait.until(
                ExpectedConditions.presenceOfElementLocated(By.id("com.gromo.partner:id/otp")));
        otp.sendKeys("433333");

        //clicking on  bottom sheet in home page
        Thread.sleep(5000);
        MobileElement bottomsheet = (MobileElement) wait.until(
                ExpectedConditions.presenceOfElementLocated(By.id("com.gromo.partner:id/button")));
        bottomsheet.click();
    */


        // Step 1: Click on the initial element
        Thread.sleep(5000);
        MobileElement placeholderElement = (MobileElement) wait.until(
                ExpectedConditions.presenceOfElementLocated(By.id("com.gromo.partner:id/tabImageViewPlaceholderRight")));
        placeholderElement.click();
        Thread.sleep(5000);

        // Step 2: Click on the llBalance element
        MobileElement balanceElement = (MobileElement) wait.until(
                ExpectedConditions.presenceOfElementLocated(By.id("com.gromo.partner:id/llBalance")));
        balanceElement.click();
        Thread.sleep(5000);

        // Step 3: Click on btnTransferToBank (first time)
        MobileElement transferButton1 = (MobileElement) wait.until(
                ExpectedConditions.presenceOfElementLocated(By.id("com.gromo.partner:id/btnTransferToBank")));
        transferButton1.click();
        Thread.sleep(5000);


        // Step 4: Click on btnTransferToBank again (second time)
        MobileElement transferButton2 = (MobileElement) wait.until(
                ExpectedConditions.presenceOfElementLocated(By.id("com.gromo.partner:id/btnTransferToBank")));
        transferButton2.click();
        Thread.sleep(5000);

        // Optional: close the driver
        driver.quit();




    }
}


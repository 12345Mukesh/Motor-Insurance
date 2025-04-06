package SignUp;

import org.springframework.http.ResponseEntity;
import java.util.List;

public class MergeMultipleUsers {
    public void mergeUserAndCreateLead() throws InterruptedException {
        // Create signup service
        SignUpWithReferralWithMultipleUsers signUpService = new SignUpWithReferralWithMultipleUsers();

        // Generate token and submit user details
        signUpService.getToken();
        signUpService.submitUserDetails();
        List<String> gpuidList = signUpService.getGpuidList();
        if (gpuidList == null || gpuidList.isEmpty()) {
            System.out.println("Error: GPUID list is null or empty. Cannot proceed.");
            return; // Stop execution if gpuidList is null or empty
        }
        signUpService.updateUserProfile();
        Thread.sleep(5000);
        signUpService.fetchUserDetails();

        // Create lead service
        CreateLeadForMultipleUsers createLead = new CreateLeadForMultipleUsers();

        // Update GPUID list in CreateLeadForMultipleUsers
        createLead.setGpuidList(gpuidList);

        // Static product details
        String[] productTypeIds = {"344", "400", "269", "358", "414", "352", "366", "344", "400", "269"};
        String[] productTypeNames = {"Swiggy HDFC Bank Credit Card", "BlinkX", "KreditBee", "Groww Mutual Funds", "Kotak 811 Saving Account", "Volt Money", "Tata Capital Business Loan", "Swiggy HDFC Bank Credit Card", "BlinkX", "KreditBee"};
        int[] kpi1Payins = {5500, 25000, 77500, 870, 310, 890, 1000, 5600, 26000, 77600};
        int[] kpi1Payouts = {5100, 20100, 75100, 500, 200, 700, 900, 5200, 20200, 75200};

        for (int i = 0; i < gpuidList.size(); i++) {
            System.out.println("Creating lead for GPUID: " + gpuidList.get(i));
            ResponseEntity<String> leadResponse = createLead.createLead(
                    gpuidList.get(i), productTypeIds[i], productTypeNames[i], kpi1Payins[i], kpi1Payouts[i]
            );

            // Wait for 10 seconds after lead creation
            Thread.sleep(10000);

            if (leadResponse.getStatusCode().is2xxSuccessful()) {
                createLead.updateLead(kpi1Payins[i], kpi1Payouts[i]);
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        MergeMultipleUsers mergeUser = new MergeMultipleUsers();
        mergeUser.mergeUserAndCreateLead();
    }
}

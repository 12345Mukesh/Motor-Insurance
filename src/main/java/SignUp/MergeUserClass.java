package SignUp;

import org.springframework.http.ResponseEntity;

public class MergeUserClass {
    public void mergeUserAndCreateLead() throws InterruptedException
    {
        // Create signup service
        SignUpWithReferralSingleUser signUpService = new SignUpWithReferralSingleUser();

        // Generate token and submit user details
        signUpService.getToken();
        signUpService.submitUserDetails();

        // Get GPUID
        String gpuid = signUpService.getGpuid();

        signUpService.updateUserProfile();

        Thread.sleep(5000);

        signUpService.fetchUserDetails();

        // Create lead service
        CreateLead createLead = new CreateLead();

        // Update GPUID in CreateLead (modify the class to expose a setter)
        CreateLead.setStaticGpuid(gpuid);

        // Create lead and wait
        ResponseEntity<String> leadResponse = createLead.createLead();

        // Additional wait to ensure lead is properly created
        Thread.sleep(5000);

        // Update lead
        ResponseEntity<String> updateResponse = createLead.updateLead();

        // Print responses with null checks
        System.out.println("Lead Creation Response: " + (leadResponse != null ? leadResponse.getBody() : "No response"));
        System.out.println("Lead Update Response: " + (updateResponse != null ? updateResponse.getBody() : "No response"));    }

    public static void main(String[] args) throws InterruptedException {
        MergeUserClass mergeUser = new MergeUserClass();
        mergeUser.mergeUserAndCreateLead();
    }
}



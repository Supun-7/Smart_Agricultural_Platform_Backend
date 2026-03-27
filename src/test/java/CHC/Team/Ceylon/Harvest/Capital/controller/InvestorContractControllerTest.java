package CHC.Team.Ceylon.Harvest.Capital.controller;

import CHC.Team.Ceylon.Harvest.Capital.service.BlockchainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
public class InvestorContractControllerTest {

    @Autowired
    private BlockchainService blockchainService;

    @PostMapping("/deploy-contract")
    public String deployContract(@RequestParam String investorName, @RequestParam String projectId) {
        try {
            // Using dummy values for the remaining parameters required by the updated BlockchainService
            String contractAddress = blockchainService.deployInvestmentContract(
                    investorName,
                    "investor@example.com", // investorEmail
                    "Test Farmer",          // farmerName
                    "Test Location",        // farmLocation
                    "Test Crop",            // cropTypes
                    100000L,                // investmentAmountLKR
                    15L,                    // expectedReturnPercent
                    System.currentTimeMillis() / 1000L // harvestDateTimestamp
            );
            return "Contract deployed at: " + contractAddress;
        } catch (Exception e) {
            e.printStackTrace();
            return "Error deploying contract: " + e.getMessage();
        }
    }
}
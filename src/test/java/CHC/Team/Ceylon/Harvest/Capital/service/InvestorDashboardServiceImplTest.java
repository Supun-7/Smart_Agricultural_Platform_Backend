package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.entity.Investment;
import CHC.Team.Ceylon.Harvest.Capital.entity.Land;
import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.entity.Wallet;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.exception.ResourceNotFoundException;
import CHC.Team.Ceylon.Harvest.Capital.repository.InvestmentRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.KycSubmissionRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.LandRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.repository.WalletRepository;
import CHC.Team.Ceylon.Harvest.Capital.service.impl.InvestorDashboardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class InvestorDashboardServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private InvestmentRepository investmentRepository;

    @Mock
    private KycSubmissionRepository kycSubmissionRepository;

    @Mock
    private LandRepository landRepository;

    @InjectMocks
    private InvestorDashboardServiceImpl investorDashboardService;

    private User user;
    private Wallet wallet;
    private Land firstLand;
    private Land secondLand;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUserId(42L);
        user.setFullName("Investor QA");
        user.setEmail("investor.qa@test.com");
        user.setRole(Role.INVESTOR);

        wallet = new Wallet();
        wallet.setWalletId(900L);
        wallet.setUser(user);
        wallet.setBalance(BigDecimal.valueOf(325000.00));
        wallet.setCurrency("LKR");

        firstLand = new Land();
        firstLand.setLandId(1001L);
        firstLand.setProjectName("Pepper Estate Phase 1");
        firstLand.setLocation("Kandy");
        firstLand.setTotalValue(BigDecimal.valueOf(600000.00));
        firstLand.setMinimumInvestment(BigDecimal.valueOf(25000.00));
        firstLand.setProgressPercentage(65);
        firstLand.setIsActive(true);

        secondLand = new Land();
        secondLand.setLandId(1002L);
        secondLand.setProjectName("Tea Revival Project");
        secondLand.setLocation("Nuwara Eliya");
        secondLand.setTotalValue(BigDecimal.valueOf(300000.00));
        secondLand.setMinimumInvestment(BigDecimal.valueOf(15000.00));
        secondLand.setProgressPercentage(40);
        secondLand.setIsActive(true);
    }

    @Test
    void getDashboard_shouldReturnWalletInvestedLandsAndAccurateBreakdownFromDatabase() {
        Long userId = 42L;
        Investment activeInvestment = buildInvestment(
                501L,
                BigDecimal.valueOf(150000.00),
                Investment.InvestmentStatus.ACTIVE,
                LocalDateTime.of(2026, 3, 1, 10, 15),
                firstLand);
        Investment pendingInvestment = buildInvestment(
                502L,
                BigDecimal.valueOf(50000.00),
                Investment.InvestmentStatus.PENDING,
                LocalDateTime.of(2026, 3, 5, 9, 0),
                secondLand);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(walletRepository.findByUserUserId(userId)).willReturn(Optional.of(wallet));
        given(kycSubmissionRepository.findTopByUserUserIdOrderBySubmittedAtDesc(userId)).willReturn(Optional.empty());
        given(investmentRepository.findAllByUserIdWithLand(userId))
                .willReturn(List.of(activeInvestment, pendingInvestment));
        given(investmentRepository.sumTotalByUserId(userId)).willReturn(BigDecimal.valueOf(200000.00));
        given(investmentRepository.sumByUserIdAndStatus(userId, Investment.InvestmentStatus.ACTIVE))
                .willReturn(BigDecimal.valueOf(150000.00));
        given(investmentRepository.sumByUserIdAndStatus(userId, Investment.InvestmentStatus.PENDING))
                .willReturn(BigDecimal.valueOf(50000.00));
        given(investmentRepository.sumByUserIdAndStatus(userId, Investment.InvestmentStatus.COMPLETED))
                .willReturn(BigDecimal.ZERO);
        given(investmentRepository.countDistinctLandsByUserId(userId)).willReturn(2L);
        given(investmentRepository.countActiveLandsByUserId(userId)).willReturn(1L);

        Map<String, Object> result = investorDashboardService.getDashboard(userId);

        assertEquals(42L, result.get("investorId"));
        assertEquals("Investor QA", result.get("investorName"));
        assertEquals("investor.qa@test.com", result.get("email"));
        assertEquals("NOT_SUBMITTED", result.get("kycStatus"));
        assertEquals(BigDecimal.valueOf(325000.00), result.get("walletBalance"));
        assertEquals("LKR", result.get("currency"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> investedLands = (List<Map<String, Object>>) result.get("investedLands");
        assertEquals(2, investedLands.size());
        assertEquals("Pepper Estate Phase 1", investedLands.get(0).get("projectName"));
        assertEquals(BigDecimal.valueOf(150000.00), investedLands.get(0).get("amountInvested"));
        assertEquals(65, investedLands.get(0).get("progressPercentage"));
        assertEquals("PENDING", investedLands.get(1).get("status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> breakdown = (Map<String, Object>) result.get("investmentBreakdown");
        assertEquals(BigDecimal.valueOf(200000.00), breakdown.get("totalInvested"));
        assertEquals(BigDecimal.valueOf(150000.00), breakdown.get("activeInvestments"));
        assertEquals(BigDecimal.valueOf(50000.00), breakdown.get("pendingInvestments"));
        assertEquals(BigDecimal.ZERO, breakdown.get("completedInvestments"));
        assertEquals(2L, breakdown.get("totalLandCount"));
        assertEquals(1L, breakdown.get("activeLandCount"));
    }

    @Test
    void getDashboard_shouldThrowWhenWalletDoesNotExist() {
        Long userId = 42L;
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(walletRepository.findByUserUserId(userId)).willReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> investorDashboardService.getDashboard(userId));

        assertEquals("Wallet not found for investor: 42", exception.getMessage());
    }

    private Investment buildInvestment(
            Long id,
            BigDecimal amount,
            Investment.InvestmentStatus status,
            LocalDateTime investmentDate,
            Land land) {
        Investment investment = new Investment();
        investment.setInvestmentId(id);
        investment.setAmountInvested(amount);
        investment.setStatus(status);
        investment.setInvestmentDate(investmentDate);
        investment.setInvestor(user);
        investment.setLand(land);
        return investment;
    }
}

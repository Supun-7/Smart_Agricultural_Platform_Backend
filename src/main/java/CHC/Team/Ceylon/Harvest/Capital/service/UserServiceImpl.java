package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import CHC.Team.Ceylon.Harvest.Capital.enums.AccountStatus;
import CHC.Team.Ceylon.Harvest.Capital.enums.Role;
import CHC.Team.Ceylon.Harvest.Capital.enums.VerificationStatus;
import CHC.Team.Ceylon.Harvest.Capital.repository.UserRepository;
import CHC.Team.Ceylon.Harvest.Capital.security.JwtUtil;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository        userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${admin.default.email}")
    private String defaultAdminEmail;

    @Value("${admin.default.password}")
    private String defaultAdminPassword;

    @Value("${admin.default.fullName}")
    private String defaultAdminFullName;

    public UserServiceImpl(
            UserRepository userRepository,
            BCryptPasswordEncoder passwordEncoder) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void createDefaultAdminIfNotExists() {
        Optional<User> existing = userRepository.findByEmail(defaultAdminEmail);
        if (existing.isEmpty()) {
            User admin = new User();
            admin.setFullName(defaultAdminFullName);
            admin.setEmail(defaultAdminEmail);
            admin.setPasswordHash(passwordEncoder.encode(defaultAdminPassword));
            admin.setRole(Role.SYSTEM_ADMIN);
            admin.setVerificationStatus(VerificationStatus.NOT_SUBMITTED);
            admin.setAccountStatus(AccountStatus.ACTIVE);
            userRepository.save(admin);
            System.out.println("========================================");
            System.out.println("Default system admin created: " + defaultAdminEmail);
            System.out.println("========================================");
        } else {
            User existingUser = existing.get();
            if (existingUser.getRole() != Role.SYSTEM_ADMIN) {
                existingUser.setRole(Role.SYSTEM_ADMIN);
                userRepository.save(existingUser);
                System.out.println("========================================");
                System.out.println("Default admin upgraded to SYSTEM_ADMIN: " + defaultAdminEmail);
                System.out.println("========================================");
            }
        }
    }

    @Override
    public User registerUser(User user) {
        String hashedPassword = passwordEncoder.encode(user.getPasswordHash());
        user.setPasswordHash(hashedPassword);
        // New accounts are always ACTIVE
        user.setAccountStatus(AccountStatus.ACTIVE);
        return userRepository.save(user);
    }

    /**
     * AC-4: Returns empty if the account is SUSPENDED so the login
     * controller can send a clear "Account suspended" error message.
     */
    @Override
    public Optional<User> login(String email, String password) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (passwordEncoder.matches(password, user.getPasswordHash())) {
                // AC-4 — suspended users cannot log in
                if (user.getAccountStatus() == AccountStatus.SUSPENDED) {
                    // Throw a specific exception the controller can catch
                    throw new AccountSuspendedException("Your account has been suspended. Please contact the platform administrator.");
                }
                return Optional.of(user);
            }
        }
        return Optional.empty();
    }

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    // Google users never use password login
    @Override
    public User createGoogleUser(String name, String email, String role) {
        User user = new User();
        user.setFullName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(
                java.util.UUID.randomUUID().toString()));
        try {
            user.setRole(Role.valueOf(role.toUpperCase()));
        } catch (IllegalArgumentException e) {
            user.setRole(Role.INVESTOR);
        }
        user.setVerificationStatus(VerificationStatus.NOT_SUBMITTED);
        user.setAccountStatus(AccountStatus.ACTIVE);
        return userRepository.save(user);
    }

    @Override
    public String generateJwt(User user) {
        return jwtUtil.generateToken(
                user.getUserId(),
                user.getRole().name(),
                user.getVerificationStatus().name()
        );
    }

    // ── Inner exception for suspended accounts ────────────────────────────────
    public static class AccountSuspendedException extends RuntimeException {
        public AccountSuspendedException(String message) {
            super(message);
        }
    }
}

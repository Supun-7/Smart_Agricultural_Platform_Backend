package CHC.Team.Ceylon.Harvest.Capital.service;

import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import java.util.Optional;

public interface UserService {
    User registerUser(User user);
    Optional<User> login(String email, String password);

    User findByEmail(String email);
    User createGoogleUser(String name, String email, String role);
    String generateJwt(User user);
}
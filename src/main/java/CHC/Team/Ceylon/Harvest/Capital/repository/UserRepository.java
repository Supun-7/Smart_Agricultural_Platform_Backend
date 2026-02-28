package CHC.Team.Ceylon.Harvest.Capital.repository;

import CHC.Team.Ceylon.Harvest.Capital.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

}
package CHC.Team.Ceylon.Harvest.Capital.repository;
 
import CHC.Team.Ceylon.Harvest.Capital.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
 
import java.util.Optional;
 
@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {
 
    Optional<Wallet> findByUserUserId(Long userId);
}

package CHC.Team.Ceylon.Harvest.Capital.repository;
 
import CHC.Team.Ceylon.Harvest.Capital.entity.Land;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
 
import java.util.List;
 
@Repository
public interface LandRepository extends JpaRepository<Land, Long> {
 
    List<Land> findAllByIsActiveTrue();
    
    List<Land> findByIsActiveTrueOrderByCreatedAtDesc();
}

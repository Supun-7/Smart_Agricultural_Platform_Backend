package CHC.Team.Ceylon.Harvest.Capital.repository;

import CHC.Team.Ceylon.Harvest.Capital.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByInvestorId(Long investorId);
}
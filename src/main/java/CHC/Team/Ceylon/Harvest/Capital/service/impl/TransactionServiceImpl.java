package CHC.Team.Ceylon.Harvest.Capital.service.impl;

import CHC.Team.Ceylon.Harvest.Capital.entity.Transaction;
import CHC.Team.Ceylon.Harvest.Capital.repository.TransactionRepository;
import CHC.Team.Ceylon.Harvest.Capital.service.TransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionServiceImpl(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public void createTransaction(Long investorId, Long amount, String type) {
        Transaction tx = new Transaction();
        tx.setInvestorId(investorId);
        tx.setAmount(amount);
        tx.setType(type);
        tx.setCreatedAt(Instant.now());

        transactionRepository.save(tx);
    }
}
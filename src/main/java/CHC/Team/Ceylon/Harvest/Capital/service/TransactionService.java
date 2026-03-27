package CHC.Team.Ceylon.Harvest.Capital.service;

public interface TransactionService {

    void createTransaction(Long investorId, Long amount, String type);
}
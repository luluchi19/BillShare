package vn.backend.backend.service;

import vn.backend.backend.model.BalanceEntity;
import vn.backend.backend.model.ExpenseEntity;
import vn.backend.backend.model.ExpenseParticipantEntity;
import vn.backend.backend.model.PaymentEntity;
import vn.backend.backend.controller.response.BalanceResponse;
import java.math.BigDecimal;
import java.util.List;

public interface BalanceService {
    BalanceEntity createBalance(Long groupId, Long userId1, Long userId2, BigDecimal amount);
    BalanceEntity updateBalance(Long balanceId, BigDecimal newAmount);
    void updateBalancesForExpense(ExpenseEntity expense, List<ExpenseParticipantEntity> participants);
    void updateBalancesAfterExpenseDeletion(Long groupId, Long payerID, List<ExpenseParticipantEntity> participants);
    List<BalanceEntity> getBalancesByGroupId(Long groupId);
    BalanceEntity getBalanceBetweenUsers(Long groupId, Long userId1, Long userId2);
    void rollBackBalance(ExpenseEntity expense,Long oldPayerId,List<ExpenseParticipantEntity>oldParticipants);
    BalanceResponse getUserBalanceResponse(Long groupId, Long userId);
    BalanceResponse getSimplifiedUserBalanceResponse(Long groupId, Long userId);
    void updateBalancesForPayment(PaymentEntity payment);
    void updateBalancesAfterPaymentDeletion(PaymentEntity payment);
    void settleGroupDebts(Long groupId);
}

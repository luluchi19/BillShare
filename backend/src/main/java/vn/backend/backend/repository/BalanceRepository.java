package vn.backend.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.backend.backend.model.BalanceEntity;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

@Repository
public interface BalanceRepository extends JpaRepository<BalanceEntity, Long> {
    List<BalanceEntity> findAllByGroupGroupId(Long groupId);
    Optional<BalanceEntity> findByGroupGroupIdAndUser1UserIdAndUser2UserId(Long groupId, Long userId1, Long userId2);
    void deleteByGroup_GroupIdAndUser1_UserIdOrGroup_GroupIdAndUser2_UserId(Long groupId1, Long userId1, Long groupId2, Long userId2);
    void deleteByGroup_GroupId(Long groupId);
    List<BalanceEntity> findAllByGroup_GroupIdAndUser1_UserIdOrGroup_GroupIdAndUser2_UserId(
            Long groupId1, Long userId1, Long groupId2, Long userId2
    );

    boolean existsByGroupGroupIdAndBalanceNot(Long groupId, BigDecimal balance);
    @Modifying
    @Transactional
    @Query("UPDATE BalanceEntity b SET b.balance = 0 WHERE b.group.groupId = :groupId")
    void setAllBalancesToZeroByGroupId(@Param("groupId") Long groupId);
}

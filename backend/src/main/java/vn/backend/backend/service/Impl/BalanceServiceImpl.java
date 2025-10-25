package vn.backend.backend.service.Impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.backend.backend.model.BalanceEntity;
import vn.backend.backend.model.ExpenseEntity;
import vn.backend.backend.model.ExpenseParticipantEntity;
import vn.backend.backend.model.PaymentEntity;
import vn.backend.backend.repository.BalanceRepository;
import vn.backend.backend.repository.ExpenseParticipantRepository;
import vn.backend.backend.repository.GroupRepository;
import vn.backend.backend.repository.UserRepository;
import vn.backend.backend.repository.GroupMembersRepository;
import vn.backend.backend.controller.response.BalanceResponse;
import vn.backend.backend.service.BalanceService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BalanceServiceImpl implements BalanceService {
    private final BalanceRepository balanceRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupMembersRepository groupMembersRepository;
    private final ExpenseParticipantRepository expenseParticipantRepository;

    @Override
    public BalanceEntity createBalance(Long groupId, Long userId1, Long userId2, BigDecimal amount) {
        var group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
        var user1 = userRepository.findById(userId1)
                .orElseThrow(() -> new RuntimeException("User 1 not found"));
        var user2 = userRepository.findById(userId2)
                .orElseThrow(() -> new RuntimeException("User 2 not found"));

        return balanceRepository.save(BalanceEntity.builder()
                .group(group)
                .user1(user1)
                .user2(user2)
                .balance(amount)
                .build());
    }

    private void updateOrCreateBalance(Long groupId, Long userId1, Long userId2, BigDecimal amount) {
        // Đảm bảo user_id_1 < user_id_2 (LEAST/GREATEST)
        Long smallerId = Math.min(userId1, userId2);
        Long largerId = Math.max(userId1, userId2);

        // Tìm balance với thứ tự đúng
        BalanceEntity balance = balanceRepository
                .findByGroupGroupIdAndUser1UserIdAndUser2UserId(groupId, smallerId, largerId)
                .orElse(null);

        if (balance != null) {
            // Cập nhật balance hiện có
            BigDecimal newBalance;

            if (userId1.equals(smallerId)) {
                // userId1 là user nhỏ hơn (user_id_1)
                // userId1 nợ userId2 → balance tăng
                newBalance = balance.getBalance().add(amount);
            } else {
                // userId1 là user lớn hơn (user_id_2)
                // userId2 nợ userId1 → balance giảm
                newBalance = balance.getBalance().subtract(amount);
            }

            balance.setBalance(newBalance);
            balanceRepository.save(balance);
        } else {
            // Tạo balance mới
            createNewBalance(groupId, smallerId, largerId, userId1, amount);
        }
    }
    @Override
    public void rollBackBalance(ExpenseEntity expense,Long oldPayerId,List<ExpenseParticipantEntity>oldParticipants) {
        Long groupId=expense.getGroup().getGroupId();
        for(var participant:oldParticipants) {
            if (participant.getUser().getUserId().equals(oldPayerId)) {
                continue;
            }
            Long participantUserId = participant.getUser().getUserId();
            BigDecimal shareAmount = participant.getShareAmount();
            Long smallerId = Math.min(participantUserId, oldPayerId);
            Long largerId = Math.max(participantUserId, oldPayerId);
            BalanceEntity balance = balanceRepository
                    .findByGroupGroupIdAndUser1UserIdAndUser2UserId(groupId, smallerId, largerId)
                    .orElseThrow(() -> new IllegalStateException("Balance record không tồn tại giữa user " + participantUserId
                            + " và payer " + oldPayerId + " trong nhóm " + groupId));
            if (balance.getUser1().getUserId().equals(participantUserId)) {
                balance.setBalance(balance.getBalance().subtract(shareAmount));
                balanceRepository.save(balance);
            } else {
                balance.setBalance(balance.getBalance().add(shareAmount));
                balanceRepository.save(balance);
            }
        }
    }

    @Override
    @Transactional
    public void updateBalancesForExpense(ExpenseEntity expense, List<ExpenseParticipantEntity> participants) {
        Long payerId = expense.getPayer().getUserId();
        Long groupId = expense.getGroup().getGroupId();

        for (ExpenseParticipantEntity participant : participants) {
            Long participantUserId = participant.getUser().getUserId();
            BigDecimal shareAmount = participant.getShareAmount();

            // Chỉ cập nhật balance cho những người không phải payer
            if (!participantUserId.equals(payerId)) {
                updateOrCreateBalance(groupId, participantUserId, payerId, shareAmount);
            }
        }
    }

    // In BalanceServiceImpl.java
    @Override
    @Transactional
    public void updateBalancesAfterExpenseDeletion(Long groupId, Long payerId, List<ExpenseParticipantEntity> participants) {

        for (ExpenseParticipantEntity participant : participants) {
            Long participantUserId = participant.getUser().getUserId();
            BigDecimal shareAmount = participant.getShareAmount();

            if (!participantUserId.equals(payerId)) {
                // Reverse the balance update: subtract shareAmount trừ ngược lại
                updateOrCreateBalance(groupId, participantUserId, payerId, shareAmount.negate());
            }
        }
    }


    private void createNewBalance(Long groupId, Long smallerId, Long largerId, Long debtorId, BigDecimal amount) {
        BalanceEntity newBalance = new BalanceEntity();
        newBalance.setGroup(groupRepository.getReferenceById(groupId));
        newBalance.setUser1(userRepository.getReferenceById(smallerId));
        newBalance.setUser2(userRepository.getReferenceById(largerId));

        // Xác định dấu của balance
        if (debtorId.equals(smallerId)) {
            // smallerId nợ largerId → balance dương
            newBalance.setBalance(amount);
        } else {
            // largerId nợ smallerId → balance âm
            newBalance.setBalance(amount.negate());
        }

        balanceRepository.save(newBalance);
    }
    @Override
    public BalanceEntity updateBalance(Long balanceId, BigDecimal newAmount) {
        var balance = balanceRepository.findById(balanceId)
                .orElseThrow(() -> new RuntimeException("Balance not found"));
        balance.setBalance(newAmount);
        return balanceRepository.save(balance);
    }

    @Override
    public List<BalanceEntity> getBalancesByGroupId(Long groupId) {
        return balanceRepository.findAllByGroupGroupId(groupId);
    }

    @Override
    public BalanceEntity getBalanceBetweenUsers(Long groupId, Long userId1, Long userId2) {
        return balanceRepository.findByGroupGroupIdAndUser1UserIdAndUser2UserId(groupId, userId1, userId2)
                .orElseThrow(() -> new RuntimeException("Balance not found"));
    }

    @Override
    public BalanceResponse getUserBalanceResponse(Long groupId, Long userId) {
        var group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        // Kiểm tra xem nhóm có bật tính năng tối ưu hóa nợ không
        if (Boolean.TRUE.equals(group.getSimplifyDebtOn())) {
            // Nếu bật simplify, gọi phương thức tối ưu hóa
            return getSimplifiedUserBalanceResponse(groupId, userId);
        }
        // Lấy toàn bộ balance trong group liên quan đến user
        List<BalanceEntity> balances = balanceRepository.findAllByGroupGroupId(groupId)
                .stream()
                .filter(b -> b.getUser1().getUserId().equals(userId) || b.getUser2().getUserId().equals(userId))
                .toList();

        List<BalanceResponse.UserBalanceDetail> details = balances.stream()
                .map(balance -> {
                    Long otherUserId;
                    String otherUserName;
                    BigDecimal amount;
                    Boolean isOwed;

                    // Xác định hướng nợ
                    if (balance.getUser1().getUserId().equals(userId)) {
                        // userId là user1
                        otherUserId = balance.getUser2().getUserId();
                        otherUserName = balance.getUser2().getFullName();
                        amount = balance.getBalance().abs();
                        isOwed = balance.getBalance().compareTo(BigDecimal.ZERO) < 0;
                    } else {
                        // userId là user2
                        otherUserId = balance.getUser1().getUserId();
                        otherUserName = balance.getUser1().getFullName();
                        amount = balance.getBalance().abs();
                        isOwed = balance.getBalance().compareTo(BigDecimal.ZERO) > 0;
                    }

                    return BalanceResponse.UserBalanceDetail.builder()
                            .userId(otherUserId)
                            .userName(otherUserName)
                            .amount(amount)
                            .isOwed(isOwed)
                            .build();
                })
                .toList();

        return BalanceResponse.builder()
                .userId(user.getUserId())
                .userName(user.getFullName())
                .groupId(group.getGroupId())
                .groupName(group.getGroupName())
                .balances(details)
                .build();
    }


    public boolean checkGroupNetDebt(Long groupId) {
        // === Bước 1: Lấy tất cả các quan hệ nợ của nhóm ===
        List<BalanceEntity> balances = balanceRepository.findAllByGroupGroupId(groupId);

        // Nếu không có quan hệ nợ nào, thì chắc chắn là đã hết nợ
        if (balances.isEmpty()) {
            return false; // Không còn nợ
        }

        // === Bước 2: Tính toán Net Balance cho từng thành viên ===
        Map<Long, BigDecimal> netBalances = groupMembersRepository.findAllById_GroupId(groupId)
                .stream()
                .collect(Collectors.toMap(
                        gm -> gm.getId().getUserId(),
                        gm -> BigDecimal.ZERO
                ));


        // Duyệt qua từng bản ghi balance để cộng/trừ vào netBalance
        for (BalanceEntity balance : balances) {
            Long user1Id = balance.getUser1().getUserId();
            Long user2Id = balance.getUser2().getUserId();
            BigDecimal amount = balance.getBalance();

            // Cập nhật cho user1
            netBalances.put(user1Id,
                    netBalances.get(user1Id).add(amount));

            // Cập nhật cho user2
            netBalances.put(user2Id,
                    netBalances.get(user2Id).subtract(amount));
        }

        // === Bước 3: Kiểm tra xem có ai có Net Balance khác 0 không ===
        final BigDecimal TOLERANCE = new BigDecimal("0.01");

        boolean hasDebt = netBalances.values().stream()
                .anyMatch(netBalance -> netBalance.abs().compareTo(TOLERANCE) > 0);

        return hasDebt;
    }
    @Override
    @Transactional // Quan trọng: Đảm bảo tất cả các cập nhật đều thành công
    public void settleGroupDebts(Long groupId) {
        // 1. Kiểm tra xem nhóm có thực sự hết nợ ròng không
        //    Hàm này trả về 'true' nếu CÒN NỢ, 'false' nếu HẾT NỢ
        boolean stillHasNetDebt = checkGroupNetDebt(groupId);

        if (stillHasNetDebt) {
            // Nếu vẫn còn nợ ròng, không cho phép tất toán
            throw new IllegalStateException("Không thể tất toán. " +
                    "Vẫn còn số dư ròng chưa thanh toán trong nhóm.");
        }

        // 2. Nếu đã hết nợ ròng (tất cả mọi người = 0),
        //    tiến hành "dọn dẹp" tất cả các bản ghi BalanceEntity về 0.
        balanceRepository.setAllBalancesToZeroByGroupId(groupId);

        // (Bạn cũng có thể thêm logic khác ở đây,
        //  ví dụ: đánh dấu nhóm là "archived" (đã lưu trữ))
    }


    // =========================================================================
    // ==        THUẬT TOÁN TỐI ƯU HÓA NỢ BẰNG MAX-FLOW (EDMONDS-KARP)       ==
    // =========================================================================

    /**
     * Class đại diện cho một cạnh trong đồ thị flow network
     * Mỗi cạnh có:
     * - from, to: điểm đầu và điểm cuối (userId)
     * - capacity: dung lượng tối đa (số tiền có thể chuyển qua cạnh này)
     * - flow: lưu lượng hiện tại đang chảy qua cạnh
     * - reverse: tham chiếu đến cạnh ngược (cần thiết cho thuật toán max-flow)
     */
    // --- CONSTANTS ---
    private static final Long SOURCE = -1L;
    private static final Long SINK = -2L;
    private static final BigDecimal INF = new BigDecimal("1000000000"); // ∞

    // --- EDGE & GRAPH (đã được cải tiến) ---
    private static class Edge {
        final Long from, to;
        BigDecimal capacity;
        BigDecimal flow = BigDecimal.ZERO;
        Edge reverse;

        Edge(Long from, Long to, BigDecimal capacity) {
            this.from = from;
            this.to = to;
            this.capacity = capacity;
        }

        BigDecimal remaining() {
            return capacity.subtract(flow);
        }

        void addFlow(BigDecimal delta) {
            this.flow = this.flow.add(delta);
            this.reverse.flow = this.reverse.flow.subtract(delta);
        }
    }

    /**
     * Class đại diện cho đồ thị flow network
     * Sử dụng danh sách kề (adjacency list) để lưu trữ các cạnh
     * Triển khai thuật toán Edmonds-Karp (BFS-based max flow) với BigDecimal
     */
    private static class FlowGraph {
        final Map<Long, List<Edge>> adj = new HashMap<>();
        final Set<Long> nodes = new HashSet<>();

        void addEdge(Long u, Long v, BigDecimal cap) {
            nodes.add(u);
            nodes.add(v);
            adj.putIfAbsent(u, new ArrayList<>());
            adj.putIfAbsent(v, new ArrayList<>());

            // Kiểm tra trùng cạnh
            for (Edge e : adj.get(u)) {
                if (e.to.equals(v)) {
                    e.capacity = e.capacity.add(cap);
                    return;
                }
            }

            Edge f = new Edge(u, v, cap);
            Edge r = new Edge(v, u, BigDecimal.ZERO);
            f.reverse = r;
            r.reverse = f;
            adj.get(u).add(f);
            adj.get(v).add(r);
        }

        BigDecimal maxFlow(Long source, Long sink) {
            BigDecimal total = BigDecimal.ZERO;

            while (true) {
                Map<Long, Edge> parent = new HashMap<>();
                Queue<Long> q = new LinkedList<>();
                q.add(source);
                Set<Long> seen = new HashSet<>(Set.of(source));

                while (!q.isEmpty() && !seen.contains(sink)) {
                    Long u = q.poll();
                    for (Edge e : adj.getOrDefault(u, List.of())) {
                        if (!seen.contains(e.to) && e.remaining().compareTo(BigDecimal.ZERO) > 0) {
                            parent.put(e.to, e);
                            seen.add(e.to);
                            q.add(e.to);
                        }
                    }
                }

                if (!parent.containsKey(sink)) break;

                BigDecimal bottleneck = INF;
                for (Long v = sink; !v.equals(source); v = parent.get(v).from) {
                    bottleneck = bottleneck.min(parent.get(v).remaining());
                }

                for (Long v = sink; !v.equals(source); v = parent.get(v).from) {
                    parent.get(v).addFlow(bottleneck);
                }

                total = total.add(bottleneck);
            }

            return total;
        }
    }

    /**
     * Record đại diện cho một khoản nợ
     * from: người nợ
     * to: người cho vay
     * amount: số tiền nợ
     */
    private record Debt(Long from, Long to, BigDecimal amount) {}

    /**
     * Xây dựng đồ thị flow network từ danh sách balance hiện tại
     *
     * Quy tắc chuyển đổi:
     * - Xét TẤT CẢ các cặp userId có trong bảng balance (kể cả balance = 0)
     * - Nếu balance > 0: user1 nợ user2 -> tạo cạnh user1 -> user2
     * - Nếu balance < 0: user2 nợ user1 -> tạo cạnh user2 -> user1
     * - Nếu balance = 0: tạo cạnh 2 chiều với capacity = 0 (để thuật toán có thể tìm đường đi qua)
     * - Capacity của cạnh = |balance|
     *
     * LƯU Ý: Balance = 0 vẫn được thêm vào đồ thị vì:
     * - Đây là các cặp đã từng có quan hệ nợ (tồn tại trong DB)
     * - Có thể tạo ra đường đi gián tiếp để tối ưu hóa
     * - Chỉ loại bỏ các cặp hoàn toàn không tồn tại trong bảng balance
     *
     * @param balances Danh sách balance entities (bao gồm cả balance = 0)
     * @return FlowGraph đại diện cho mạng nợ
     */
    private FlowGraph buildOptimizationGraph(List<BalanceEntity> balances, Map<Long, BigDecimal> netBalance) {
        FlowGraph g = new FlowGraph();

        // 1. Thêm cạnh từ SOURCE / đến SINK
        for (Map.Entry<Long, BigDecimal> e : netBalance.entrySet()) {
            Long user = e.getKey();
            BigDecimal amount = e.getValue();
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                g.addEdge(SOURCE, user, amount);
            } else if (amount.compareTo(BigDecimal.ZERO) < 0) {
                g.addEdge(user, SINK, amount.abs());
            }
        }

        // 2. Thêm cạnh 2 chiều giữa các cặp đã từng có nợ (capacity = INF)
        for (BalanceEntity b : balances) {
            Long u1 = b.getUser1().getUserId();
            Long u2 = b.getUser2().getUserId();
            g.addEdge(u1, u2, INF);
            g.addEdge(u2, u1, INF); // addEdge đã chống trùng
        }

        return g;
    }

    /**
     * PHƯƠNG THỨC CHÍNH: Tối ưu hóa nợ bằng thuật toán Max-Flow với ràng buộc cạnh
     *
     * Ý TƯỞNG CHÍNH:
     * - Với mỗi cạnh nợ trực tiếp A -> B, tìm các đường đi gián tiếp A -> ... -> B
     * - Chuyển nợ qua đường gián tiếp này để giảm số lượng giao dịch
     * - Đảm bảo không tạo quan hệ nợ mới giữa những người không liên quan
     *
     * THUẬT TOÁN:
     * 1. Xây dựng đồ thị flow từ các balance hiện tại
     * 2. Với mỗi cạnh trực tiếp (u->v):
     *    a. Tạo bản sao đồ thị, đặt capacity cạnh u->v = 0
     *    b. Tìm max flow từ u đến v qua các đường khác
     *    c. Lượng flow tìm được sẽ được cộng vào cạnh trực tiếp u->v
     *    d. Trừ flow này khỏi các cạnh trung gian đã sử dụng
     * 3. Thu thập các cạnh còn lại (capacity > 0) làm kết quả
     *
     * ĐẢM BẢO:
     * - Không tạo quan hệ nợ mới giữa người không liên quan
     * - Tổng nợ của mỗi người không thay đổi
     * - Giảm số lượng giao dịch cần thiết
     *
     * @param groupId ID của nhóm
     * @param userId ID của user yêu cầu xem balance
     * @return BalanceResponse với danh sách nợ đã tối ưu
     */
    @Override
    public BalanceResponse getSimplifiedUserBalanceResponse(Long groupId, Long userId) {
        // === 1. Validate ===
        var group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NoSuchElementException("Group not found with id " + groupId));
        var requestingUser = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found with id " + userId));

        List<BalanceEntity> allBalances = balanceRepository.findAllByGroupGroupId(groupId);
        if (allBalances.isEmpty()) {
            return BalanceResponse.builder()
                    .userId(userId)
                    .userName(requestingUser.getFullName())
                    .groupId(groupId)
                    .groupName(group.getGroupName())
                    .balances(Collections.emptyList())
                    .build();
        }

        // === 2. Tính net balance cho mỗi user ===
        Map<Long, BigDecimal> netBalance = new HashMap<>();
        for (BalanceEntity b : allBalances) {
            netBalance.merge(b.getUser1().getUserId(), b.getBalance(), BigDecimal::add);
            netBalance.merge(b.getUser2().getUserId(), b.getBalance().negate(), BigDecimal::add);
        }

        // === 3. Lấy tên user ===
        Map<Long, String> userNames = new HashMap<>();
        for (BalanceEntity b : allBalances) {
            userNames.put(b.getUser1().getUserId(), b.getUser1().getFullName());
            userNames.put(b.getUser2().getUserId(), b.getUser2().getFullName());
        }

        // === 4. Xây dựng đồ thị tối ưu với Super-Source & Super-Sink ===
        FlowGraph graph = buildOptimizationGraph(allBalances, netBalance);

        // === 5. Chạy Max-Flow ===
        BigDecimal totalFlow = graph.maxFlow(SOURCE, SINK);

        // === 6. Lấy các giao dịch từ flow > 0 (không tính SOURCE/SINK) ===
        List<Debt> transactions = new ArrayList<>();
        for (Map.Entry<Long, List<Edge>> entry : graph.adj.entrySet()) {
            Long from = entry.getKey();
            if (from.equals(SOURCE) || from.equals(SINK)) continue;

            for (Edge edge : entry.getValue()) {
                if (edge.to.equals(SOURCE) || edge.to.equals(SINK)) continue;
                if (edge.flow.compareTo(BigDecimal.ZERO) > 0) {
                    transactions.add(new Debt(from, edge.to, edge.flow));
                }
            }
        }

        // === 7. Lọc chỉ các khoản liên quan đến userId ===
        List<BalanceResponse.UserBalanceDetail> details = transactions.stream()
                .filter(d -> d.from().equals(userId) || d.to().equals(userId))
                .map(d -> {
                    boolean isOwed = d.to().equals(userId); // userId được nhận
                    Long otherId = isOwed ? d.from() : d.to();
                    String otherName = userNames.getOrDefault(otherId, "Unknown");
                    BigDecimal amount = d.amount().setScale(2, RoundingMode.HALF_UP);

                    return BalanceResponse.UserBalanceDetail.builder()
                            .userId(otherId)
                            .userName(otherName)
                            .amount(amount)
                            .isOwed(isOwed)
                            .build();
                })
//                .sorted((a, b) -> {
//                    int cmp = a.isOwed() == b.isOwed() ? 0 : (a.isOwed() ? 1 : -1);
//                    if (cmp != 0) return cmp;
//                    return a.userName().compareToIgnoreCase(b.userName());
//                })
                .collect(Collectors.toList());

        // === 8. Trả về ===
        return BalanceResponse.builder()
                .userId(requestingUser.getUserId())
                .userName(requestingUser.getFullName())
                .groupId(group.getGroupId())
                .groupName(group.getGroupName())
                .balances(details)
                .build();
    }

    @Override
    @Transactional
    public void updateBalancesForPayment(PaymentEntity payment) {
        Long payerId = payment.getPayer().getUserId();
        Long payeeId = payment.getPayee().getUserId();
        Long groupId = payment.getGroup().getGroupId();
        BigDecimal amount = payment.getAmount();

        // Payment giảm nợ: payer trả tiền cho payee
        // Cần trừ số tiền khỏi balance giữa 2 người
        // Nếu payer đang nợ payee -> giảm nợ
        // Nếu payee đang nợ payer -> tăng nợ ngược lại (payee nợ ít hơn)
        updateOrCreateBalance(groupId, payerId, payeeId, amount.negate());
    }

    @Override
    @Transactional
    public void updateBalancesAfterPaymentDeletion(PaymentEntity payment) {
        Long payerId = payment.getPayer().getUserId();
        Long payeeId = payment.getPayee().getUserId();
        Long groupId = payment.getGroup().getGroupId();
        BigDecimal amount = payment.getAmount();

        // Rollback payment: hoàn tác việc trả nợ
        // Cộng lại số tiền vào balance
        updateOrCreateBalance(groupId, payerId, payeeId, amount);
    }
}

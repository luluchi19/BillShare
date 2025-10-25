// java
package vn.backend.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.backend.backend.controller.response.BalanceResponse;
import vn.backend.backend.service.BalanceService;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class BalanceController {
    private final BalanceService balanceService;

    @GetMapping("/{groupId}/users/{userId}/balances")
    public ResponseEntity<BalanceResponse> getUserBalances(
            @PathVariable Long groupId,
            @PathVariable Long userId) {
        BalanceResponse response = balanceService.getUserBalanceResponse(groupId, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{groupId}/settle")
    public ResponseEntity<?> settleGroupDebts(@PathVariable Long groupId) {
        // Gọi service để thực hiện logic tất toán
        balanceService.settleGroupDebts(groupId);

        // Trả về một thông báo thành công đơn giản
        return ResponseEntity.ok(Map.of("message", "Group debts settled successfully."));
    }
}

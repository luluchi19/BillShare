package vn.backend.backend.controller.response;

import lombok.*;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupDetailResponse implements Serializable {
    private Long groupId;
    private String groupName;
    private String description;
    private Long createdBy;
    private String defaultCurrency;
    private Date createdAt;
    private Boolean isActive;
    private List<UserDetailResponse> members;
    private int totalMembers;
    private String avatar;
    private Boolean simplifyDebtOn;
}

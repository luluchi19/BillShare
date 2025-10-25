package vn.backend.backend.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "groups")
public class GroupEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_id")
    private Long groupId;
    
    @Column( name = "group_name",nullable = false)
    private String groupName;

    @Column( name = "description")
    private String description;

    @Column( name = "created_by",nullable = false)
    private Long createdBy;

    @Column( name = "avatar_url")
    private String avatarUrl;

    @OneToMany(mappedBy = "group", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<GroupMembersEntity> groupMembers;

    @Builder.Default
    @Column( name = "default_currency")
    private String defaultCurrency="VND";

    @Column(name = "created_at",nullable = false )
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private Date createdAt;

    @Column(name = "updated_at",nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @UpdateTimestamp
    private Date updatedAt;

    @Builder.Default
    @Column( name = "is_active")
    private Boolean isActive=true;

    @Builder.Default
    @Column( name = "simplify_debt_on")
    private Boolean simplifyDebtOn=false;
}

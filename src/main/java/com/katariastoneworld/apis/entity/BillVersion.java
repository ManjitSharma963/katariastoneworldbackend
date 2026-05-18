package com.katariastoneworld.apis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bill_versions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_id", nullable = false)
    private Long billId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "action_type", length = 50)
    private String actionType;

    @Column(name = "previous_version_id")
    private Long previousVersionId;

    @Column(name = "snapshot_json", columnDefinition = "JSON")
    private String snapshotJson;

    @Column(name = "change_summary", columnDefinition = "JSON")
    private String changeSummary;

    @Column(name = "edit_reason", length = 500)
    private String editReason;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Revision head marker: {@code ACTIVE} for the open snapshot row; {@code SUPERSEDED} once a newer {@code bill_versions}
     * row exists for the same bill. Legacy rows may be null until backfilled.
     */
    @Column(name = "lifecycle_status", length = 20)
    private String lifecycleStatus;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}


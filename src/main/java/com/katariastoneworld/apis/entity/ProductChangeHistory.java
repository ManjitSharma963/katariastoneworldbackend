package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_change_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductChangeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "previous_snapshot", columnDefinition = "LONGTEXT")
    private String previousSnapshotJson;

    @Column(name = "new_snapshot", columnDefinition = "LONGTEXT")
    private String newSnapshotJson;

    @Column(length = 512)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

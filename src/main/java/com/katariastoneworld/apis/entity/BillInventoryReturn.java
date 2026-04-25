package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bill_inventory_returns", indexes = {
        @Index(name = "idx_bir_bill", columnList = "bill_kind,bill_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillInventoryReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "bill_kind", nullable = false, length = 16)
    private BillKind billKind;

    @Column(name = "bill_id", nullable = false)
    private Long billId;

    @Column(length = 50)
    private String location;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @OneToMany(mappedBy = "header", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BillInventoryReturnLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void addLine(BillInventoryReturnLine line) {
        lines.add(line);
        line.setHeader(this);
    }
}

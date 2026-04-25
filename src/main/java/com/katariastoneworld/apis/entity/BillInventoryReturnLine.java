package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "bill_inventory_return_lines", indexes = {
        @Index(name = "idx_birl_return", columnList = "return_id"),
        @Index(name = "idx_birl_item", columnList = "bill_item_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillInventoryReturnLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "return_id", nullable = false)
    private BillInventoryReturn header;

    @Column(name = "bill_item_id", nullable = false)
    private Long billItemId;

    @Column(name = "quantity_returned", nullable = false, precision = 14, scale = 2)
    private BigDecimal quantityReturned;
}

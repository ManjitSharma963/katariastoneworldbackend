package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "loan_lenders", uniqueConstraints = {
        @UniqueConstraint(name = "uk_loan_lender_loc_key", columnNames = {"location", "name_key"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoanLender {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String location;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "name_key", nullable = false, length = 200)
    private String nameKey;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

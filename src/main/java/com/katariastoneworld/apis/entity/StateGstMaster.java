package com.katariastoneworld.apis.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "state_gst_master")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StateGstMaster {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INT")
    private Long id;
    
    @Column(name = "state_name", nullable = false, length = 100)
    private String stateName;
    
    @Column(name = "gst_code", nullable = false, length = 10)
    private String gstCode;
}


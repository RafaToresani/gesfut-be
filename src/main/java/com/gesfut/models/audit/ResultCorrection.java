package com.gesfut.models.audit;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Revision critica #5: registro de cada correccion de resultado (quien, cuando, antes y despues). */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "result_corrections")
public class ResultCorrection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long matchId;
    private String userEmail;
    private LocalDateTime correctedAt;
    @Column(columnDefinition = "TEXT")
    private String oldResult;
    @Column(columnDefinition = "TEXT")
    private String newResult;
}

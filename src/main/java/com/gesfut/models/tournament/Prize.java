package com.gesfut.models.tournament;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "prizes")
public class Prize {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private EPrizeType type;
    private String description;
    private Integer position;

    @ManyToOne()
    @JoinColumn(name ="tournament_id")
    private Tournament tournament;
}

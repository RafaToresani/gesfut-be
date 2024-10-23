package com.gesfut.models.tournament;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "Statistics")
public class Statistics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    Integer points;
    Integer matchesPlayed;
    Integer wins;
    Integer draws;
    Integer losses;
    Integer goalsFor;
    Integer goalsAgainst;
    Integer redCards;
    Integer yellowCards;

    @OneToOne(mappedBy = "statistics")
    private TournamentParticipant participant;
}

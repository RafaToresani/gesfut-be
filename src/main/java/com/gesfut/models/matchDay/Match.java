package com.gesfut.models.matchDay;


import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.tournament.TournamentParticipant;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "matches")

public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "home_team_id")
    private TournamentParticipant homeTeam;

    @ManyToOne
    @JoinColumn(name = "away_team_id")
    private TournamentParticipant awayTeam;

    private Integer goalsHomeTeam;
    private Integer goalsAwayTeam;
    private Boolean isFinished;

    @ManyToOne
    @JoinColumn(name = "match_day_id")
    private MatchDay matchDay;
    
    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Event> events;

    private LocalDateTime date;
    private String description;
}

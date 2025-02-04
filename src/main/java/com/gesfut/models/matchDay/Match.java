package com.gesfut.models.matchDay;


import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.tournament.TournamentParticipant;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

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
    
    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL,fetch =  FetchType.LAZY, orphanRemoval = true)
    private List<Event> events;

    private LocalDateTime date;
    private String description;

    public String formatMatchDate(LocalDateTime date) {
        if (date == null) {
            return null;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE d 'de' MMM yyyy '|' HH:mm 'hs'", new Locale("es", "ES"));
        return date.format(formatter);
    }



}

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

    private String mvpPlayer;

    /** Plan 22/10 #2: fecha libre por equipo dado de baja (el rival queda libre). Mismo trato que "Free": sin resultado ni estadisticas. */
    private Boolean bye;

    /** Plan 22/10 #2: el equipo ficticio "Free" que completa torneos impares (unico lugar donde se reconoce por nombre). */
    public static boolean isFreeParticipant(TournamentParticipant p) {
        return p != null && isFreeParticipantTeam(p.getTeam());
    }

    public static boolean isFreeParticipantTeam(Team team) {
        return team != null && "Free".equals(team.getName());
    }

    /** Plan 22/10 #2: participante dado de baja del torneo (null = activo, filas viejas). */
    public static boolean isInactiveParticipant(TournamentParticipant p) {
        return p != null && Boolean.FALSE.equals(p.getIsActive());
    }

    /** Plan 22/10 #2: partido que no se juega: contra "Free" o marcado como libre por una baja. */
    public boolean isByeMatch() {
        return Boolean.TRUE.equals(bye) || isFreeParticipant(homeTeam) || isFreeParticipant(awayTeam);
    }

    /** Equipo que descansa en una fecha libre (el que sigue activo y no es "Free"); null si no aplica. */
    public String restingTeamName() {
        if (!isByeMatch()) return null;
        boolean homeRests = !isFreeParticipant(homeTeam) && !isInactiveParticipant(homeTeam);
        boolean awayRests = !isFreeParticipant(awayTeam) && !isInactiveParticipant(awayTeam);
        if (homeRests && !awayRests) return homeTeam.getTeam().getName();
        if (awayRests && !homeRests) return awayTeam.getTeam().getName();
        return null;
    }

    /** Plan 22/10 #9: el resultado se carga recien el dia del partido (sin fecha: se permite). */
    public boolean resultLoadableToday() {
        return date == null || !date.toLocalDate().isAfter(java.time.LocalDate.now());
    }

    public String formatMatchDate(LocalDateTime date) {
        if (date == null) {
            return null;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE d 'de' MMM yyyy '|' HH:mm 'hs'", new Locale("es", "ES"));
        return date.format(formatter);
    }

    @Column(name = "nextMatch")
    private Long vsMatchIdWhoWin;

    private Long tiebreakWinnerId;

    /** Revision critica #9: resultado de la tanda de penales (null si no hubo). */
    private Integer penaltiesHome;
    private Integer penaltiesAway;


    public Match get() {
        return this;
    }
}

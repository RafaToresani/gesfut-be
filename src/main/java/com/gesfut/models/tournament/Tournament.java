package com.gesfut.models.tournament;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.gesfut.models.matchDay.MatchDay;
import com.gesfut.models.user.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tournaments")
public class Tournament {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID code;
    private String name;
    private LocalDate startDate;
    private Boolean isFinished;
    private Boolean isActive;

    /** Revision critica #4: amarillas acumuladas para una fecha de suspension (null = 5). */
    private Integer yellowCardsToSuspend;
    /** Revision critica #4: fechas de suspension por roja (null = 1). */
    private Integer redCardSuspensionMatches;

    public int yellowLimit() { return yellowCardsToSuspend == null ? 5 : yellowCardsToSuspend; }
    public int redMatches() { return redCardSuspensionMatches == null ? 1 : redCardSuspensionMatches; }

    @ManyToOne()
    @JoinColumn(name = "user_id")
    @JsonBackReference
    private UserEntity user;

    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Set<TournamentParticipant> teams;

    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private Set<MatchDay> matchDays;

    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL, fetch =  FetchType.LAZY, orphanRemoval = true)
    private Set<Prize> prizes;
}



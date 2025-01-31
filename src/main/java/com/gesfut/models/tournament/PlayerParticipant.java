package com.gesfut.models.tournament;


import com.gesfut.models.matchDay.Event;
import com.gesfut.models.team.Player;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "player_participants")
public class PlayerParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Boolean isSuspended;
    private Integer goals;
    private Integer redCards;
    private Integer yellowCards;
    private Integer isMvp;
    private Boolean isActive;
    private Integer consecutiveCards;

    @ManyToOne
    @JoinColumn(name = "player_id")
    private Player player;

    @ManyToOne
    @JoinColumn(name = "tournament_participant_id")
    private TournamentParticipant tournamentParticipant;

    @OneToMany(mappedBy = "playerParticipant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Event> events;
}

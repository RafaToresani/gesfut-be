package com.gesfut.models.matchDay;

import com.gesfut.models.team.Player;
import com.gesfut.models.tournament.PlayerParticipant;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private EEventType type;
    private Integer quantity;

    @ManyToOne
    @JoinColumn(name = "playerParticipant_id")
    private PlayerParticipant playerParticipant;

    @ManyToOne
    @JoinColumn(name = "match_id")
    private Match match;
}

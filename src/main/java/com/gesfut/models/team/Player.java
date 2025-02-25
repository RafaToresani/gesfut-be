package com.gesfut.models.team;


import com.fasterxml.jackson.annotation.JsonBackReference;
import com.gesfut.models.matchDay.Event;
import com.gesfut.models.tournament.PlayerParticipant;
import com.gesfut.models.user.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.Set;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "players")
public class Player {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String lastName;
    private Integer number;
    private Boolean isCaptain;
    private Boolean isGoalKeeper;

    @ManyToOne
    @JoinColumn(name = "team_id")
    @JsonBackReference
    private Team team;

    @OneToMany(mappedBy = "player", cascade = CascadeType.ALL, fetch =  FetchType.LAZY, orphanRemoval = true)
    private Set<PlayerParticipant> playerParticipants;

    private Boolean status;
}

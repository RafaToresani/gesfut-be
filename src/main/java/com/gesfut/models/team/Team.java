package com.gesfut.models.team;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.gesfut.models.tournament.TournamentParticipant;
import com.gesfut.models.user.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.Set;


@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "teams")
public class Team {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String color;
    private Boolean status;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonBackReference
    private UserEntity user;

    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, fetch =  FetchType.LAZY)
    private Set<Player> players;

    @OneToMany(mappedBy = "team", fetch =  FetchType.LAZY)
    private Set<TournamentParticipant> tournaments;

}

package com.gesfut.models.matchDay;

import com.gesfut.models.tournament.Tournament;
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
@Table(name = "match_days")

public class MatchDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Integer numberOfMatchDay;
    private Boolean isFinished;

    @ManyToOne
    @JoinColumn(name = "tournament_id")
    private Tournament tournament;

    @OneToMany(mappedBy = "matchDay", cascade = CascadeType.ALL, fetch =  FetchType.LAZY , orphanRemoval = true)
    private Set<Match> matches;

    private String mvpPlayer;

    @Column (name = "play_off")
    private Boolean isPlayOff;

}

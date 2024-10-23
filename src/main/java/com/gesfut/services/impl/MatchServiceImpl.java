package com.gesfut.services.impl;

import com.gesfut.dtos.requests.EventRequest;
import com.gesfut.dtos.requests.MatchRequest;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.models.matchDay.Event;
import com.gesfut.models.matchDay.Match;
import com.gesfut.models.team.Player;
import com.gesfut.models.tournament.PlayerParticipant;
import com.gesfut.repositories.EventRepository;
import com.gesfut.repositories.MatchRepository;
import com.gesfut.repositories.PlayerParticipantRepository;
import com.gesfut.repositories.PlayerRepository;
import com.gesfut.services.EventService;
import com.gesfut.services.MatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class MatchServiceImpl implements MatchService {

    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerParticipantRepository playerParticipantRepository;

    @Override
    public String loadMatchResult(MatchRequest request) {
        Match match = this.getMatch(request.matchId());

        List<Event> events = new ArrayList<>();

        request.events().forEach(eventRequest -> {
            this.eventRepository.save(createEvent(eventRequest,match));
        });

        return "Partido cargado";
    }


    private Match getMatch(Long matchId){
        Optional<Match> match = this.matchRepository.findById(matchId);
        if(match.isEmpty()) throw new ResourceNotFoundException("El id del partido no existe");
        return match.get();
    }

    private Event createEvent(EventRequest eventRequest, Match match){
        Optional<PlayerParticipant> playerParticipant = this.playerParticipantRepository.findById(eventRequest.playerParticipantId());
        if(playerParticipant.isEmpty()) throw new ResourceNotFoundException("El jugador asociado al evento no existe.");
        return Event
                .builder()
                .match(match)
                .type(eventRequest.type())
                .playerParticipant(playerParticipant.get())
                .quantity(eventRequest.quantity())
                .build();
    }
}

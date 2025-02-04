package com.gesfut.services.impl;

import com.gesfut.dtos.requests.EventRequest;
import com.gesfut.dtos.responses.EventResponse;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.models.matchDay.Event;
import com.gesfut.models.matchDay.Match;
import com.gesfut.models.tournament.PlayerParticipant;
import com.gesfut.models.tournament.TournamentParticipant;
import com.gesfut.repositories.EventRepository;
import com.gesfut.repositories.PlayerParticipantRepository;
import com.gesfut.services.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EventServiceImpl implements EventService {

    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private PlayerParticipantRepository playerParticipantRepository;

    @Override
    public Event createEvent(EventRequest eventRequest, Match match) {
        List<TournamentParticipant> teams = List.of(match.getHomeTeam(), match.getAwayTeam());
        Optional<PlayerParticipant> playerParticipant = playerParticipantRepository.findByIdAndTournamentParticipantIn(eventRequest.playerParticipantId(), teams);

        if (playerParticipant.isEmpty()) {
            throw new ResourceNotFoundException("Uno de los jugadores asociados no existe en ningún equipo del partido.");
        }

        return this.eventRepository.save(Event
                .builder()
                .match(match)
                .type(eventRequest.type())
                .playerParticipant(playerParticipant.get())
                .quantity(eventRequest.quantity())
                .build());
    }

    @Override
    public EventResponse eventToResponse(Event event) {
        return new EventResponse(
                event.getId(),
                event.getQuantity(),
                event.getType(),
                event.getPlayerParticipant().getPlayer().getName() + " " +
                        event.getPlayerParticipant().getPlayer().getLastName(),
                event.getPlayerParticipant().getPlayer().getTeam().getName(),
                event.getPlayerParticipant().getId()
        );
    }



}

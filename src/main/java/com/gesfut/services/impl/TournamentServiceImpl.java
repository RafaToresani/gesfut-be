package com.gesfut.services.impl;

import com.gesfut.config.security.SecurityUtils;
import com.gesfut.dtos.requests.TournamentRequest;
import com.gesfut.dtos.responses.TournamentResponse;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.TournamentRepository;
import com.gesfut.services.TournamentService;
import com.gesfut.services.UserEntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service
public class TournamentServiceImpl implements TournamentService {

    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    private UserEntityService userService;

    @Override
    public void createTournament(TournamentRequest request) {
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());

        Tournament tournament = tournamentRepository.save(
                Tournament
                        .builder()
                        .code(getRandomUUID())
                        .teams(new HashSet<>())
                        .name(request.name())
                        .user(user)
                        .startDate(LocalDate.now())
                        .build()
        );
    }

    @Override
    public List<TournamentResponse> findAllTournaments() {
        UserEntity user = this.userService.findUserByEmail(SecurityUtils.getCurrentUserEmail());
        return this.tournamentRepository.findAllByUser(user).stream().map(tournament -> tournamentToResponse(tournament)).toList();
    }

    @Override
    public TournamentResponse findTournamentByCode(String code) {
        Optional<Tournament> tournament = this.tournamentRepository.findByCode(UUID.fromString(code));
        if(tournament.isEmpty()) throw new ResourceNotFoundException("Torneo no encontrado.");

        return tournamentToResponse(tournament.get());
    }

    @Override
    @Transactional
    public String deleteTournamentByCode(String code) {
        if(!this.tournamentRepository.existsByCode(UUID.fromString(code))) return "El torneo no existe";

        this.tournamentRepository.deleteByCode(UUID.fromString(code));
        return "Torneo eliminado exitosamente";
    }

    public TournamentResponse tournamentToResponse(Tournament tournament){
        return new TournamentResponse(
                tournament.getName(),
                tournament.getCode().toString(),
                tournament.getStartDate(),
                tournament.getUser().getName() + " " + tournament.getUser().getLastname()
        );
    }

    private UUID getRandomUUID(){
       UUID code;
        do{
            code = UUID.randomUUID();
       }while(this.tournamentRepository.existsByCode(code));
        return code;
    }
}

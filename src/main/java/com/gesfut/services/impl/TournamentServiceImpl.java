package com.gesfut.services.impl;

import com.gesfut.config.security.SecurityUtils;
import com.gesfut.dtos.requests.TournamentRequest;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.TournamentRepository;
import com.gesfut.services.TournamentService;
import com.gesfut.services.UserEntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
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

    private UUID getRandomUUID(){
       UUID code;
        do{
            code = UUID.randomUUID();
       }while(this.tournamentRepository.existsByCode(code));
        return code;
    }
}

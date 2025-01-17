package com.gesfut.services.impl;

import com.gesfut.dtos.requests.PrizeRequest;
import com.gesfut.dtos.requests.PrizesRequest;
import com.gesfut.dtos.responses.PrizeResponse;
import com.gesfut.exceptions.ResourceAlreadyExistsException;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.models.tournament.EPrizeType;
import com.gesfut.models.tournament.Prize;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.repositories.PrizeRepository;
import com.gesfut.repositories.TournamentRepository;
import com.gesfut.services.PrizeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PrizeServiceImpl implements PrizeService {
    @Autowired
    private PrizeRepository prizeRepository;
    @Autowired
    private TournamentRepository tournamentRepository;


    @Override
    public void createPrizes(PrizesRequest request) {
        Optional<Tournament> opt = this.tournamentRepository.findByCode(UUID.fromString(request.code()));

        if(opt.isEmpty()) throw new ResourceNotFoundException("El torneo no existe.");

        List<Prize> prizes = new ArrayList<>();
        request.prizes().forEach(prize -> {
            prizes.add(createPrize(prize, opt.get()));
        });
        prizes.forEach(prize -> this.prizeRepository.save(prize));
    }

    private Prize createPrize(PrizeRequest request, Tournament tournament) {
        if (request.type() == null || !isValidEnumValue(request.type()))
            throw new IllegalArgumentException("El tipo de premio no es válido: " + request.type());
        if(prizeRepository.existsByPositionAndTypeAndTournamentId(request.position(), request.type(), tournament.getId()))
            throw new ResourceAlreadyExistsException("Al menos uno de los premios ya existe.");
        return Prize.builder()
                .description(request.description())
                .position(request.position())
                .type(request.type())
                .tournament(tournament)
                .build();
    }

    @Override
    public List<PrizeResponse> findAllPrizes(String code) {
        Optional<Tournament> opt = this.tournamentRepository.findByCode(UUID.fromString(code));
        if(opt.isEmpty()) throw new ResourceNotFoundException("El torneo no existe.");

        List<Prize> prizes = this.prizeRepository.findAllByTournamentId(opt.get().getId());
        return prizes.stream().map(this::prizeToResponse).toList();
    }

    private PrizeResponse prizeToResponse(Prize prize){
        return new PrizeResponse(prize.getPosition(),prize.getType().toString(), prize.getDescription());
    }



    private boolean isValidEnumValue(EPrizeType type) {
        for (EPrizeType value : EPrizeType.values()) {
            if (value == type) {
                return true;
            }
        }
        return false;
    }
}

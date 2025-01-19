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
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    public List<PrizeResponse> findAllPrizesByCategory(String code, String category) {
        if (category == null || !isValidEnumValue(EPrizeType.valueOf(category)))
            throw new IllegalArgumentException("El tipo de premio no es válido: " + category);
        Optional<Tournament> opt = this.tournamentRepository.findByCode(UUID.fromString(code));
        if(opt.isEmpty()) throw new ResourceNotFoundException("El torneo no existe.");

        List<Prize> prizes = this.prizeRepository.findAllByTournamentIdAndType(opt.get().getId(),EPrizeType.valueOf(category));
        return prizes.stream().map(this::prizeToResponse).toList();
    }

    @Override
    @Transactional
    public void deletePrizeByCodeAndCategoryAndPosition(String code, String category, Integer position) {
        if (category == null || !isValidEnumValue(EPrizeType.valueOf(category)))
            throw new IllegalArgumentException("El tipo de premio no es válido: " + category);
        Optional<Tournament> opt = this.tournamentRepository.findByCode(UUID.fromString(code));
        if(opt.isEmpty()) throw new ResourceNotFoundException("El torneo no existe.");

        Optional<Prize> optionalPrize = this.prizeRepository.findByTournamentIdAndTypeAndPosition(opt.get().getId(), EPrizeType.valueOf(category), position);
        if(optionalPrize.isEmpty()) throw new ResourceNotFoundException("El premio no existe.");

        Tournament tournament = optionalPrize.get().getTournament();
        tournament.getPrizes().remove(optionalPrize.get());
        tournamentRepository.save(tournament);
        prizeRepository.deletePrizeById(optionalPrize.get().getId());
        prizeRepository.flush();

        Optional<Prize> checkPrize = this.prizeRepository.findById(optionalPrize.get().getId());
        if (checkPrize.isPresent()) {
            System.out.println("⚠️ El premio sigue existiendo después de eliminarlo.");
        } else {
            System.out.println("✅ Premio eliminado correctamente.");
        }

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

package com.gesfut.controllers;


import com.gesfut.dtos.requests.MatchDateRequest;
import com.gesfut.dtos.requests.MatchDayRequest;
import com.gesfut.dtos.responses.MatchDayResponse;
import com.gesfut.dtos.responses.NewDateResponse;
import com.gesfut.models.matchDay.MatchDay;
import com.gesfut.services.MatchDayService;
import com.gesfut.services.TeamService;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/match-days")
public class MatchDayController {

    @Autowired
    private MatchDayService matchDayService;

    @GetMapping("/{code}")
    @ResponseStatus(HttpStatus.OK)
    public List<MatchDayResponse> getMatchDaysByTournamentCode(@PathVariable String code){
        return this.matchDayService.getMatchDaysByCode(code);
    }

    @GetMapping("/{code}/lastPlayed")
    @ResponseStatus(HttpStatus.OK)
    public MatchDayResponse getLastPlayedMatchDay(@PathVariable String code){
        return this.matchDayService.getLastMatchDayPlayed(code);
    }

    @PutMapping("/close")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public void generateMatchDay(@RequestParam("matchDayId") Long id, @RequestParam("status") Boolean status){
        this.matchDayService.updateStatusMatchDay(id, status);
    }

    //ENDPOITN PARA ACTUALIZAR TODAS LAS FECHAS DE LOS PARTIDOS D EUNA FECHA
    @PatchMapping("/update-date-all-matches/{idMatchDay}/{plusMinutes}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public List<NewDateResponse> updateDateAllMatches(@PathVariable Long idMatchDay, @Valid @RequestBody MatchDateRequest request, @PathVariable Integer plusMinutes){
       return this.matchDayService.updateDateAllMatches(idMatchDay, request, plusMinutes);
    }

}

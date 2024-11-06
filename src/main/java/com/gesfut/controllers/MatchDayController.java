package com.gesfut.controllers;


import com.gesfut.dtos.requests.MatchDayRequest;
import com.gesfut.dtos.responses.MatchDayResponse;
import com.gesfut.dtos.responses.MatchDayShortResponse;
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

    @GetMapping("/short/{code}")
    @ResponseStatus(HttpStatus.OK)
    public List<MatchDayShortResponse> getMatchDaysShortByTournamentCode(@PathVariable String code){
        return this.matchDayService.getMatchDaysShortByCode(code);
    }

    @PutMapping("/close")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public void generateMatchDay(@Param("matchDayId") Long id, @Param("status") Boolean status){
        this.matchDayService.updateStatusMatchDay(id, status);
    }

}

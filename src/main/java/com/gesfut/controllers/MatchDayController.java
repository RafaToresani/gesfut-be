package com.gesfut.controllers;


import com.gesfut.dtos.requests.MatchDayRequest;
import com.gesfut.models.matchDay.MatchDay;
import com.gesfut.services.MatchDayService;
import com.gesfut.services.TeamService;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
@RequestMapping("/api/v1/match-days")
public class MatchDayController {

    @Autowired
    private MatchDayService matchDayService;


//    @PostMapping("/initialize-tournament")
//    @ResponseStatus(HttpStatus.CREATED)
//    @PreAuthorize("hasAnyAuthority('MANAGER')")
//    public void generateMatchDay(@Valid @RequestBody MatchDayRequest request, BindingResult bindingResult)
//            throws BadRequestException {
//        if (bindingResult.hasErrors()){
//            throw new BadRequestException(Objects.requireNonNull(bindingResult.getFieldError()).getDefaultMessage());
//        }
//        this.matchDayService.generateMatchDays(request);
//    }




}

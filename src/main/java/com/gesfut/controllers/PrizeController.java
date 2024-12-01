package com.gesfut.controllers;

import com.gesfut.dtos.requests.PrizeRequest;
import com.gesfut.dtos.requests.PrizesRequest;
import com.gesfut.dtos.responses.PrizeResponse;
import com.gesfut.services.PrizeService;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/prizes")
public class PrizeController {
    @Autowired
    private PrizeService prizeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void createPrizes(@Valid @RequestBody PrizesRequest request, BindingResult bindingResult) throws BadRequestException {
        if(bindingResult.hasErrors()) throw new BadRequestException(bindingResult.getFieldError().getDefaultMessage());
        this.prizeService.createPrizes(request);
    }
    @GetMapping("/{code}")
    @ResponseStatus(HttpStatus.OK)
    public List<PrizeResponse> findAllPrizes(String code){
        return this.prizeService.findAllPrizes(code);
    }
}

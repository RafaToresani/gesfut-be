package com.gesfut.controllers;

import com.gesfut.dtos.requests.PrizeRequest;
import com.gesfut.dtos.requests.PrizesRequest;
import com.gesfut.dtos.responses.PrizeResponse;
import com.gesfut.services.PrizeService;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public void createPrizes(@Valid @RequestBody PrizesRequest request, BindingResult bindingResult) throws BadRequestException {
        if(bindingResult.hasErrors()) throw new BadRequestException(bindingResult.getFieldError().getDefaultMessage());
        this.prizeService.createPrizes(request);
    }
    @GetMapping("/{code}")
    @ResponseStatus(HttpStatus.OK)
    public List<PrizeResponse> findAllPrizes(@PathVariable String code){
        return this.prizeService.findAllPrizes(code);
    }

    @GetMapping("/{code}/{category}")
    @ResponseStatus(HttpStatus.OK)
    public List<PrizeResponse> findAllPrizesByCategory(@PathVariable String code, @PathVariable String category){
        return this.prizeService.findAllPrizesByCategory(code, category);
    }

    @DeleteMapping("/{code}/{category}/{position}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('MANAGER')")
    public void deletePrize(@PathVariable String code, @PathVariable String category, @PathVariable Integer position){
        this.prizeService.deletePrizeByCodeAndCategoryAndPosition(code, category, position);
    }
}

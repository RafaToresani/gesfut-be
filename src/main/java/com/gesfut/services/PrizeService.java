package com.gesfut.services;

import com.gesfut.dtos.requests.PrizesRequest;
import com.gesfut.dtos.responses.PrizeResponse;

import java.util.List;

public interface PrizeService {
    void createPrizes(PrizesRequest request);

    List<PrizeResponse> findAllPrizes(String code);
}
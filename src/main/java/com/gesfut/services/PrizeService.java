package com.gesfut.services;

import com.gesfut.dtos.requests.PrizeRequest;
import com.gesfut.dtos.requests.PrizesRequest;
import com.gesfut.dtos.responses.PrizeResponse;
import jakarta.validation.Valid;

import java.util.List;

public interface PrizeService {
    void createPrizes(PrizesRequest request);

    List<PrizeResponse> findAllPrizes(String code);

    List<PrizeResponse> findAllPrizesByCategory(String code, String category);

    void deletePrizeByCodeAndCategoryAndPosition(String code, String category, Integer position);

    void partiallyUpdatePrize(String code, @Valid PrizeRequest request);
}

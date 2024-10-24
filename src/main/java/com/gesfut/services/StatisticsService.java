package com.gesfut.services;

import com.gesfut.dtos.responses.StatisticsResponse;
import com.gesfut.models.tournament.Statistics;

public interface StatisticsService {
    StatisticsResponse statisticsToResponse(Statistics statistics);
}

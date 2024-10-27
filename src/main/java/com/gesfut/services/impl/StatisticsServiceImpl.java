package com.gesfut.services.impl;

import com.gesfut.dtos.responses.StatisticsResponse;
import com.gesfut.models.tournament.Statistics;
import com.gesfut.repositories.StatisticsRepository;
import com.gesfut.services.StatisticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StatisticsServiceImpl implements StatisticsService {
    @Autowired
    private StatisticsRepository statisticsRepository;


    @Override
    public StatisticsResponse statisticsToResponse(Statistics statistics){
        return new StatisticsResponse(
                statistics.getPoints(),
                statistics.getMatchesPlayed(),
                statistics.getWins(),
                statistics.getDraws(),
                statistics.getLosses(),
                statistics.getGoalsFor(),
                statistics.getGoalsAgainst()
        );
    }
}

package com.gesfut.services.impl;

import com.gesfut.models.matchDay.Match;
import com.gesfut.models.matchDay.MatchDay;
import com.gesfut.models.team.Team;
import com.gesfut.models.tournament.Tournament;
import com.gesfut.models.tournament.TournamentParticipant;
import com.gesfut.repositories.MatchDayRepository;
import com.gesfut.repositories.MatchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Revision critica #11: el fixture alterna local y visitante. */
class FixtureHomeAwayTests {
    @Test void cadaEquipoQuedaBalanceadoYSinRachasLargas() {
        for (int n = 4; n <= 12; n += 2) {
            List<Match> saved = new ArrayList<>();
            MatchRepository matches = mock(MatchRepository.class);
            when(matches.save(any())).thenAnswer(inv -> { Match m = inv.getArgument(0); if (!saved.contains(m)) saved.add(m); return m; });
            MatchDayRepository days = mock(MatchDayRepository.class);
            when(days.save(any())).thenAnswer(inv -> inv.getArgument(0));
            MatchServiceImpl ms = new MatchServiceImpl();
            ReflectionTestUtils.setField(ms, "matchRepository", matches);
            MatchDayServiceImpl mds = new MatchDayServiceImpl();
            ReflectionTestUtils.setField(mds, "matchDayRepository", days);
            ReflectionTestUtils.setField(mds, "matchService", ms);
            Tournament t = Tournament.builder().id(1L).teams(new HashSet<>()).matchDays(new HashSet<>()).build();
            List<TournamentParticipant> teams = new ArrayList<>();
            for (long i = 1; i <= n; i++) teams.add(TournamentParticipant.builder().id(i).tournament(t).team(Team.builder().id(i).name("TEST " + i).build()).build());

            mds.generate(t, new ArrayList<>(teams), n, n - 1, null, null, null);

            assertEquals(n * (n - 1) / 2, saved.size());
            Map<Long, Integer> home = new HashMap<>();
            Map<Long, StringBuilder> seq = new HashMap<>();
            saved.sort(Comparator.comparingInt(m -> m.getMatchDay().getNumberOfMatchDay()));
            for (Match m : saved) {
                home.merge(m.getHomeTeam().getId(), 1, Integer::sum);
                seq.computeIfAbsent(m.getHomeTeam().getId(), k -> new StringBuilder()).append('H');
                seq.computeIfAbsent(m.getAwayTeam().getId(), k -> new StringBuilder()).append('A');
            }
            int max = teams.stream().mapToInt(p -> home.getOrDefault(p.getId(), 0)).max().orElseThrow();
            int min = teams.stream().mapToInt(p -> home.getOrDefault(p.getId(), 0)).min().orElseThrow();
            assertTrue(max - min <= 1, "n=" + n + " localias " + home);
            for (StringBuilder s : seq.values()) assertFalse(s.toString().contains("HHH") || s.toString().contains("AAA"), "n=" + n + " racha en " + s);
        }
    }
}

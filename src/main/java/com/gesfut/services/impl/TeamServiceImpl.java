package com.gesfut.services.impl;

import com.gesfut.dtos.requests.TeamRequest;
import com.gesfut.exceptions.ResourceNotFoundException;
import com.gesfut.models.team.Team;
import com.gesfut.models.user.UserEntity;
import com.gesfut.repositories.TeamRepository;
import com.gesfut.repositories.UserRepository;
import com.gesfut.services.TeamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static com.gesfut.config.security.SecurityUtils.getCurrentUserEmail;

@Service
public class TeamServiceImpl implements TeamService {

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    public void createTeam(TeamRequest request){
        String userEmail = getCurrentUserEmail();
        Optional<UserEntity> user = userRepository.findByEmail(userEmail);
        if(user.isEmpty()) {
            throw new ResourceNotFoundException("Usuario no encontrado");
        }

        Team team = Team.builder()
                .name(request.name())
                .color(request.color())
                .user(user.get())
                .build();

        teamRepository.save(team);

    }

}

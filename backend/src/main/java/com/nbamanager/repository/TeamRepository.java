package com.nbamanager.repository;

import com.nbamanager.domain.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {

    Page<Team> findByNameContainingIgnoreCaseOrCityContainingIgnoreCase(
            String name, String city, Pageable pageable);

    Team findByName(String name);
}

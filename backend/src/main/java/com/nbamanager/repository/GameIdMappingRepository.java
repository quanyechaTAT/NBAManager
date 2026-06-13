package com.nbamanager.repository;

import com.nbamanager.domain.GameIdMapping;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GameIdMappingRepository extends JpaRepository<GameIdMapping, Long> {

    /** 根据ESPN gameId查找 */
    Optional<GameIdMapping> findByEspnGameId(String espnGameId);

    /** 根据NBA gameId查找 */
    Optional<GameIdMapping> findByNbaGameId(String nbaGameId);
}

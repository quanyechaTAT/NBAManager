package com.nbamanager.repository;

import com.nbamanager.domain.SeasonConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeasonConfigRepository extends JpaRepository<SeasonConfig, Integer> {

    /** 查找当前赛季 */
    SeasonConfig findByIsCurrentTrue();

    /** 按赛季查找 */
    Optional<SeasonConfig> findBySeason(String season);

    /** 按赛季倒序查找所有 */
    List<SeasonConfig> findAllByOrderBySeasonDesc();

    /** 按同步状态查找 */
    List<SeasonConfig> findBySyncStatusOrderBySeasonDesc(String syncStatus);

    /** 删除指定赛季 */
    void deleteBySeason(String season);

    /** 检查赛季是否存在 */
    boolean existsBySeason(String season);
}

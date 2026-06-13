package com.nbamanager.service;

import com.nbamanager.domain.DraftPick;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.DraftPickRepository;
import com.nbamanager.web.dto.DraftPickDto;
import com.nbamanager.web.dto.DraftPickRequest;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DraftService {

    private final DraftPickRepository draftPickRepository;

    /** 按年份查询选秀记录 */
    @Transactional(readOnly = true)
    public List<DraftPickDto> listByYear(Integer year) {
        return draftPickRepository.findByYear(year).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** 按球队查询选秀记录 */
    @Transactional(readOnly = true)
    public List<DraftPickDto> listByTeam(String teamName) {
        return draftPickRepository.findByTeamName(teamName).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** 新增选秀记录 */
    @Transactional
    public DraftPickDto create(DraftPickRequest req) {
        DraftPick pick = new DraftPick();
        apply(pick, req);
        return toDto(draftPickRepository.save(pick));
    }

    /** 修改选秀记录 */
    @Transactional
    public DraftPickDto update(Long id, DraftPickRequest req) {
        DraftPick pick = draftPickRepository.findById(id)
                .orElseThrow(() -> notFound(id));
        apply(pick, req);
        return toDto(draftPickRepository.save(pick));
    }

    /** 删除选秀记录 */
    @Transactional
    public void delete(Long id) {
        if (!draftPickRepository.existsById(id)) {
            throw notFound(id);
        }
        draftPickRepository.deleteById(id);
    }

    /**
     * 批量导入选秀记录，跳过重复（按year+round+pickNumber去重）
     *
     * @return 导入成功数量
     */
    @Transactional
    public int importDraftPicks(List<DraftPickDto> picks) {
        int imported = 0;

        for (DraftPickDto dto : picks) {
            // 去重：使用 Repository 方法检查 year+round+pickNumber 是否已存在
            boolean duplicate = draftPickRepository.existsByYearAndRoundAndPickNumber(
                    dto.year(), dto.round(), dto.pickNumber());

            if (duplicate) {
                continue;
            }

            DraftPick pick = new DraftPick();
            pick.setYear(dto.year());
            pick.setRound(dto.round());
            pick.setPickNumber(dto.pickNumber());
            pick.setTeamName(dto.teamName());
            if (dto.teamNameEn() != null) {
                pick.setTeamNameEn(dto.teamNameEn());
            }
            if (dto.playerName() != null) {
                pick.setPlayerName(dto.playerName());
            }
            if (dto.playerNameEn() != null) {
                pick.setPlayerNameEn(dto.playerNameEn());
            }
            if (dto.notes() != null) {
                pick.setNotes(dto.notes());
            }
            draftPickRepository.save(pick);
            imported++;
        }

        log.info("批量导入选秀记录完成：成功{}条，跳过重复{}条", imported, picks.size() - imported);
        return imported;
    }

    private void apply(DraftPick pick, DraftPickRequest req) {
        pick.setYear(req.year());
        pick.setRound(req.round());
        pick.setPickNumber(req.pickNumber());
        pick.setTeamName(req.teamName());
        if (req.playerName() != null) {
            pick.setPlayerName(req.playerName());
        }
        if (req.notes() != null) {
            pick.setNotes(req.notes());
        }
    }

    private DraftPickDto toDto(DraftPick p) {
        return new DraftPickDto(
                p.getId(),
                p.getYear(),
                p.getRound(),
                p.getPickNumber(),
                p.getTeamName(),
                p.getTeamNameEn(),
                p.getPlayerName(),
                p.getPlayerNameEn(),
                p.getNotes(),
                p.getCreateTime());
    }

    private static ApiException notFound(Long id) {
        throw new ApiException(HttpStatus.NOT_FOUND, "选秀记录不存在: " + id);
    }
}

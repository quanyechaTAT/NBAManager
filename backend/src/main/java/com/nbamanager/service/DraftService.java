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

    @Transactional(readOnly = true)
    public List<DraftPickDto> listByYear(Integer year) {
        return draftPickRepository.findByYear(year).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DraftPickDto> listByTeam(String teamName) {
        return draftPickRepository.findByTeamName(teamName).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public DraftPickDto create(DraftPickRequest req) {
        DraftPick pick = new DraftPick();
        apply(pick, req);
        return toDto(draftPickRepository.save(pick));
    }

    @Transactional
    public DraftPickDto update(Long id, DraftPickRequest req) {
        DraftPick pick = draftPickRepository.findById(id)
                .orElseThrow(() -> notFound(id));
        apply(pick, req);
        return toDto(draftPickRepository.save(pick));
    }

    @Transactional
    public void delete(Long id) {
        if (!draftPickRepository.existsById(id)) {
            throw notFound(id);
        }
        draftPickRepository.deleteById(id);
    }

    @Transactional
    public int importDraftPicks(List<DraftPickDto> picks) {
        int upserted = 0;
        for (DraftPickDto dto : picks) {
            DraftPick pick = draftPickRepository
                    .findByYearAndRoundAndPickNumber(dto.year(), dto.round(), dto.pickNumber())
                    .orElseGet(DraftPick::new);
            applyImport(pick, dto);
            draftPickRepository.save(pick);
            upserted++;
        }

        log.info("Draft picks import finished: upserted {}", upserted);
        return upserted;
    }

    private void applyImport(DraftPick pick, DraftPickDto dto) {
        pick.setYear(dto.year());
        pick.setRound(dto.round());
        pick.setPickNumber(dto.pickNumber());
        pick.setTeamName(dto.teamName());
        if (dto.teamNameEn() != null && !dto.teamNameEn().isBlank()) {
            pick.setTeamNameEn(dto.teamNameEn());
        }
        if (dto.playerName() != null && !dto.playerName().isBlank()) {
            pick.setPlayerName(dto.playerName());
        }
        if (dto.playerNameEn() != null && !dto.playerNameEn().isBlank()) {
            pick.setPlayerNameEn(dto.playerNameEn());
        }
        if (dto.nbaPlayerId() != null && dto.nbaPlayerId() > 0) {
            pick.setNbaPlayerId(dto.nbaPlayerId());
        }
        if (dto.notes() != null && !dto.notes().isBlank()) {
            pick.setNotes(dto.notes());
        }
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
                p.getNbaPlayerId(),
                p.getNotes(),
                p.getCreateTime());
    }

    private static ApiException notFound(Long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "Draft pick not found: " + id);
    }
}

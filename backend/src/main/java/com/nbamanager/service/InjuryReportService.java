package com.nbamanager.service;

import com.nbamanager.domain.InjuryReport;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.InjuryReportRepository;
import com.nbamanager.web.dto.InjuryReportDto;
import com.nbamanager.web.dto.InjuryReportRequest;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InjuryReportService {

    private final InjuryReportRepository injuryReportRepository;

    @Transactional(readOnly = true)
    public List<InjuryReportDto> list(String teamName) {
        List<InjuryReport> reports;
        if (teamName != null && !teamName.isBlank()) {
            reports = injuryReportRepository.findByTeamName(teamName.trim());
        } else {
            reports = injuryReportRepository.findAll();
        }
        return reports.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InjuryReportDto> listByStatus(String status) {
        List<InjuryReport> reports = injuryReportRepository.findByStatus(status.trim());
        return reports.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public InjuryReportDto create(InjuryReportRequest req) {
        InjuryReport report = new InjuryReport();
        apply(report, req);
        return toDto(injuryReportRepository.save(report));
    }

    @Transactional
    public InjuryReportDto update(Long id, InjuryReportRequest req) {
        InjuryReport report = injuryReportRepository.findById(id)
                .orElseThrow(() -> notFound(id));
        apply(report, req);
        return toDto(injuryReportRepository.save(report));
    }

    @Transactional
    public void delete(Long id) {
        if (!injuryReportRepository.existsById(id)) {
            throw notFound(id);
        }
        injuryReportRepository.deleteById(id);
    }

    private void apply(InjuryReport report, InjuryReportRequest req) {
        report.setPlayerId(req.playerId());
        report.setPlayerName(req.playerName());
        report.setTeamName(req.teamName());
        report.setInjuryType(req.injuryType());
        report.setStatus(req.status());
        report.setDescription(req.description());
        report.setStartDate(req.startDate());
        report.setExpectedReturn(req.expectedReturn());
    }

    private InjuryReportDto toDto(InjuryReport report) {
        return new InjuryReportDto(
                report.getId(),
                report.getPlayerId(),
                report.getPlayerName(),
                report.getTeamName(),
                report.getInjuryType(),
                report.getStatus(),
                report.getDescription(),
                report.getStartDate(),
                report.getExpectedReturn(),
                report.getCreateTime());
    }

    private static ApiException notFound(Long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "伤病报告不存在: " + id);
    }
}

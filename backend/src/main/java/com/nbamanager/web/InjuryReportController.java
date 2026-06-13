package com.nbamanager.web;

import com.nbamanager.service.InjuryReportService;
import com.nbamanager.web.dto.InjuryReportDto;
import com.nbamanager.web.dto.InjuryReportRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/injuries")
@RequiredArgsConstructor
public class InjuryReportController {

    private final InjuryReportService injuryReportService;

    @GetMapping
    public List<InjuryReportDto> list(
            @RequestParam(required = false) String teamName) {
        return injuryReportService.list(teamName);
    }

    @GetMapping("/status/{status}")
    public List<InjuryReportDto> listByStatus(@PathVariable String status) {
        return injuryReportService.listByStatus(status);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public InjuryReportDto create(@Valid @RequestBody InjuryReportRequest request) {
        return injuryReportService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public InjuryReportDto update(@PathVariable Long id,
                                  @Valid @RequestBody InjuryReportRequest request) {
        return injuryReportService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        injuryReportService.delete(id);
    }
}

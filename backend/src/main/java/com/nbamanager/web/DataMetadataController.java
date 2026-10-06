package com.nbamanager.web;

import com.nbamanager.service.DataMetadataService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class DataMetadataController {
    private final DataMetadataService service;
    @GetMapping("/metadata") public Map<String, Object> metadata() { return service.overview(); }
}

package com.example.demo.controller;

import com.example.demo.service.MonitorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/monitor")
public class SystemMonitorController {

    private final MonitorService monitorService;

    public SystemMonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping("/dashboard-data")
    public Map<String, Object> getDashboardData() {
        return monitorService.getDashboardData();
    }
}

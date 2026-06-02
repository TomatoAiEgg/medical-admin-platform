package com.smartclinic.medical.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.smartclinic.common.core.domain.R;
import com.smartclinic.medical.service.MedicalMonitorService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 挂号监控看板接口。
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/medical/monitor")
public class MedicalMonitorController {

    private final MedicalMonitorService monitorService;

    @SaCheckPermission("medical:monitor:dashboard")
    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        return R.ok(monitorService.dashboard());
    }

    @SaCheckPermission("medical:monitor:doctor")
    @GetMapping("/doctor/{doctorId}")
    public R<Map<String, Object>> doctor(@PathVariable String doctorId) {
        return R.ok(monitorService.doctorMonitor(doctorId));
    }

    @SaCheckPermission("medical:monitor:doctor")
    @GetMapping("/doctor/{doctorId}/trace")
    public R<Map<String, Object>> doctorTrace(@PathVariable String doctorId, @RequestParam Map<String, Object> params) {
        return R.ok(monitorService.doctorTrace(doctorId, params));
    }

    @SaCheckPermission("medical:monitor:doctor")
    @GetMapping("/doctor/list")
    public R<List<Map<String, Object>>> doctorList(@RequestParam Map<String, Object> params) {
        return R.ok(monitorService.doctorSummaries(params));
    }

    @SaCheckPermission("medical:monitor:patient")
    @GetMapping("/patient/{patientId}")
    public R<Map<String, Object>> patient(@PathVariable String patientId) {
        return R.ok(monitorService.patientMonitor(patientId));
    }

    @SaCheckPermission("medical:monitor:patient")
    @GetMapping("/patient/{patientId}/trace")
    public R<Map<String, Object>> patientTrace(@PathVariable String patientId, @RequestParam Map<String, Object> params) {
        return R.ok(monitorService.patientTrace(patientId, params));
    }

    @SaCheckPermission("medical:monitor:patient")
    @GetMapping("/patient/list")
    public R<List<Map<String, Object>>> patientList(@RequestParam Map<String, Object> params) {
        return R.ok(monitorService.patientSummaries(params));
    }

    @SaCheckPermission("medical:trace:list")
    @GetMapping("/trace")
    public R<Map<String, Object>> trace(@RequestParam Map<String, Object> params) {
        return R.ok(monitorService.traceDetail(params));
    }
}

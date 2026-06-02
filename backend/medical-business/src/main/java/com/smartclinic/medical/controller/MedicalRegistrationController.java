package com.smartclinic.medical.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.smartclinic.common.core.domain.R;
import com.smartclinic.common.idempotent.annotation.RepeatSubmit;
import com.smartclinic.common.log.annotation.Log;
import com.smartclinic.common.log.enums.BusinessType;
import com.smartclinic.common.mybatis.core.page.TableDataInfo;
import com.smartclinic.common.satoken.utils.LoginHelper;
import com.smartclinic.common.web.core.BaseController;
import com.smartclinic.medical.service.MedicalCrudService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 挂号订单后台查询接口。
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/medical/registration")
public class MedicalRegistrationController extends BaseController {

    private final MedicalCrudService crudService;

    @SaCheckPermission("medical:registration:list")
    @GetMapping("/list")
    public TableDataInfo<Map<String, Object>> list(@RequestParam Map<String, Object> params,
                                                   @RequestParam(required = false) Integer pageNum,
                                                   @RequestParam(required = false) Integer pageSize) {
        return crudService.page("registration", params, pageNum, pageSize);
    }

    @SaCheckPermission("medical:registration:query")
    @GetMapping("/{registrationId}")
    public R<Map<String, Object>> info(@PathVariable String registrationId) {
        return R.ok(crudService.getById("registration", registrationId));
    }

    @SaCheckPermission("medical:registration:add")
    @GetMapping("/createOptions")
    public R<Map<String, Object>> createOptions(@RequestParam Map<String, Object> params) {
        return R.ok(crudService.registrationCreateOptions(params));
    }

    @SaCheckPermission("medical:registration:add")
    @Log(title = "挂号订单创建", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Map<String, Object>> add(@RequestBody Map<String, Object> body) {
        String operatorUserId = String.valueOf(LoginHelper.getUserId());
        return R.ok(crudService.createRegistration(body, operatorUserId));
    }

    @SaCheckPermission("medical:registration:audit")
    @GetMapping("/{registrationId}/audit")
    public TableDataInfo<Map<String, Object>> audit(@PathVariable String registrationId,
                                                    @RequestParam(required = false) Integer pageNum,
                                                    @RequestParam(required = false) Integer pageSize) {
        return crudService.auditByRegistration(registrationId, pageNum, pageSize);
    }

    @SaCheckPermission("medical:registration:audit")
    @GetMapping("/{registrationId}/inventoryAudit")
    public TableDataInfo<Map<String, Object>> inventoryAudit(@PathVariable String registrationId,
                                                             @RequestParam(required = false) Integer pageNum,
                                                             @RequestParam(required = false) Integer pageSize) {
        return crudService.inventoryAuditByRegistration(registrationId, pageNum, pageSize);
    }

    @SaCheckPermission("medical:registration:query")
    @GetMapping("/{registrationId}/timeline")
    public R<Object> timeline(@PathVariable String registrationId) {
        return R.ok(crudService.registrationTimeline(registrationId));
    }

    @SaCheckPermission("medical:registration:query")
    @GetMapping("/{registrationId}/detail")
    public R<Map<String, Object>> detail(@PathVariable String registrationId) {
        return R.ok(crudService.registrationDetail(registrationId));
    }

    @SaCheckPermission("medical:registration:edit")
    @Log(title = "挂号订单状态", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{registrationId}/transition")
    public R<Void> transition(@PathVariable String registrationId, @RequestBody Map<String, Object> body) {
        String action = String.valueOf(body.get("action"));
        Object reason = body.get("reason");
        String operatorUserId = String.valueOf(LoginHelper.getUserId());
        return toAjax(crudService.transitionRegistration(registrationId, action, reason == null ? null : String.valueOf(reason), operatorUserId));
    }

    @SaCheckPermission("medical:registration:edit")
    @Log(title = "挂号订单改约", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{registrationId}/reschedule")
    public R<Void> reschedule(@PathVariable String registrationId, @RequestBody Map<String, Object> body) {
        Long newSlotId = Long.valueOf(String.valueOf(body.get("slotId")));
        Object reason = body.get("reason");
        String operatorUserId = String.valueOf(LoginHelper.getUserId());
        return toAjax(crudService.rescheduleRegistration(registrationId, newSlotId, reason == null ? null : String.valueOf(reason), operatorUserId));
    }

    @SaCheckPermission("medical:registration:edit")
    @Log(title = "同步过期挂号订单", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/expireOverdue")
    public R<Map<String, Object>> expireOverdue(@RequestBody(required = false) Map<String, Object> body) {
        Object reason = body == null ? null : body.get("reason");
        String operatorUserId = String.valueOf(LoginHelper.getUserId());
        return R.ok(crudService.expireOverdueRegistrations(reason == null ? null : String.valueOf(reason), operatorUserId));
    }
}

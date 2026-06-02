package com.smartclinic.medical.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.smartclinic.common.core.domain.R;
import com.smartclinic.common.idempotent.annotation.RepeatSubmit;
import com.smartclinic.common.log.annotation.Log;
import com.smartclinic.common.log.enums.BusinessType;
import com.smartclinic.common.mybatis.core.page.TableDataInfo;
import com.smartclinic.common.web.core.BaseController;
import com.smartclinic.medical.service.MedicalCrudService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 号源管理后台接口。
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/medical/slot")
public class MedicalSlotController extends BaseController {

    private final MedicalCrudService crudService;

    @SaCheckPermission("medical:slot:list")
    @GetMapping("/list")
    public TableDataInfo<Map<String, Object>> list(@RequestParam Map<String, Object> params,
                                                   @RequestParam(required = false) Integer pageNum,
                                                   @RequestParam(required = false) Integer pageSize) {
        return crudService.page("slot", params, pageNum, pageSize);
    }

    @SaCheckPermission("medical:slot:query")
    @GetMapping("/{slotId}")
    public R<Map<String, Object>> info(@PathVariable Long slotId) {
        return R.ok(crudService.getById("slot", slotId));
    }

    @SaCheckPermission("medical:slot:add")
    @Log(title = "号源管理", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping
    public R<Void> add(@RequestBody Map<String, Object> body) {
        return toAjax(crudService.insert("slot", body));
    }

    @SaCheckPermission("medical:slot:add")
    @Log(title = "批量生成号源", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/batchGenerate")
    public R<Map<String, Object>> batchGenerate(@RequestBody Map<String, Object> body) {
        return R.ok(crudService.batchGenerateSlots(body));
    }

    @SaCheckPermission("medical:slot:edit")
    @Log(title = "号源管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{slotId}")
    public R<Void> edit(@PathVariable Long slotId, @RequestBody Map<String, Object> body) {
        return toAjax(crudService.update("slot", slotId, body));
    }

    @SaCheckPermission("medical:slot:edit")
    @Log(title = "号源状态", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestBody Map<String, Object> body) {
        Long slotId = Long.valueOf(String.valueOf(body.get("slotId")));
        String status = String.valueOf(body.get("status"));
        Object remarks = body.get("remarks");
        return toAjax(crudService.changeSlotStatus(slotId, status, remarks == null ? null : String.valueOf(remarks)));
    }

    @SaCheckPermission("medical:slot:edit")
    @Log(title = "号源库存调整", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{slotId}/inventory")
    public R<Void> adjustInventory(@PathVariable Long slotId, @RequestBody Map<String, Object> body) {
        Integer capacity = body.get("capacity") == null ? null : Integer.valueOf(String.valueOf(body.get("capacity")));
        Integer remainingSlots = body.get("remainingSlots") == null ? null : Integer.valueOf(String.valueOf(body.get("remainingSlots")));
        Object reason = body.get("reason");
        return toAjax(crudService.adjustSlotInventory(slotId, capacity, remainingSlots, reason == null ? null : String.valueOf(reason)));
    }

    @SaCheckPermission("medical:slot:edit")
    @Log(title = "号源停诊恢复", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/{slotId}/operationalStatus")
    public R<Void> changeOperationalStatus(@PathVariable Long slotId, @RequestBody Map<String, Object> body) {
        String status = String.valueOf(body.get("status"));
        Object reason = body.get("reason");
        return toAjax(crudService.changeSlotOperationalStatus(slotId, status, reason == null ? null : String.valueOf(reason)));
    }
}

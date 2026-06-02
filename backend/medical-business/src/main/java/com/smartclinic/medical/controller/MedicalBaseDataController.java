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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 医疗基础数据后台接口。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/medical")
public class MedicalBaseDataController extends BaseController {

    private final MedicalCrudService crudService;

    @SaCheckPermission("medical:department:list")
    @GetMapping("/department/list")
    public TableDataInfo<Map<String, Object>> departmentList(@RequestParam Map<String, Object> params,
                                                             @RequestParam(required = false) Integer pageNum,
                                                             @RequestParam(required = false) Integer pageSize) {
        return crudService.page("department", params, pageNum, pageSize);
    }

    @SaCheckPermission("medical:department:query")
    @GetMapping("/department/{departmentCode}")
    public R<Map<String, Object>> departmentInfo(@PathVariable String departmentCode) {
        return R.ok(crudService.getById("department", departmentCode));
    }

    @SaCheckPermission("medical:department:add")
    @Log(title = "科室管理", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/department")
    public R<Void> addDepartment(@RequestBody Map<String, Object> body) {
        return toAjax(crudService.insert("department", body));
    }

    @SaCheckPermission("medical:department:edit")
    @Log(title = "科室管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/department/{departmentCode}")
    public R<Void> editDepartment(@PathVariable String departmentCode, @RequestBody Map<String, Object> body) {
        return toAjax(crudService.update("department", departmentCode, body));
    }

    @SaCheckPermission("medical:doctor:list")
    @GetMapping("/doctor/list")
    public TableDataInfo<Map<String, Object>> doctorList(@RequestParam Map<String, Object> params,
                                                         @RequestParam(required = false) Integer pageNum,
                                                         @RequestParam(required = false) Integer pageSize) {
        return crudService.page("doctor", params, pageNum, pageSize);
    }

    @SaCheckPermission("medical:doctor:query")
    @GetMapping("/doctor/{doctorId}")
    public R<Map<String, Object>> doctorInfo(@PathVariable String doctorId) {
        return R.ok(crudService.getById("doctor", doctorId));
    }

    @SaCheckPermission("medical:doctor:add")
    @Log(title = "医生管理", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/doctor")
    public R<Void> addDoctor(@RequestBody Map<String, Object> body) {
        return toAjax(crudService.insert("doctor", body));
    }

    @SaCheckPermission("medical:doctor:edit")
    @Log(title = "医生管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/doctor/{doctorId}")
    public R<Void> editDoctor(@PathVariable String doctorId, @RequestBody Map<String, Object> body) {
        return toAjax(crudService.update("doctor", doctorId, body));
    }

    @SaCheckPermission("medical:patient:list")
    @GetMapping("/patient/list")
    public TableDataInfo<Map<String, Object>> patientList(@RequestParam Map<String, Object> params,
                                                          @RequestParam(required = false) Integer pageNum,
                                                          @RequestParam(required = false) Integer pageSize) {
        return crudService.page("patient", params, pageNum, pageSize);
    }

    @SaCheckPermission("medical:patient:query")
    @GetMapping("/patient/{patientId}")
    public R<Map<String, Object>> patientInfo(@PathVariable String patientId) {
        return R.ok(crudService.getById("patient", patientId));
    }

    @SaCheckPermission("medical:patient:add")
    @Log(title = "患者管理", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/patient")
    public R<Void> addPatient(@RequestBody Map<String, Object> body) {
        return toAjax(crudService.insert("patient", body));
    }

    @SaCheckPermission("medical:patient:edit")
    @Log(title = "患者管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/patient/{patientId}")
    public R<Void> editPatient(@PathVariable String patientId, @RequestBody Map<String, Object> body) {
        return toAjax(crudService.update("patient", patientId, body));
    }

    @SaCheckPermission("medical:user:list")
    @GetMapping("/platformUser/list")
    public TableDataInfo<Map<String, Object>> platformUserList(@RequestParam Map<String, Object> params,
                                                               @RequestParam(required = false) Integer pageNum,
                                                               @RequestParam(required = false) Integer pageSize) {
        return crudService.page("platformUser", params, pageNum, pageSize);
    }

    @SaCheckPermission("medical:user:query")
    @GetMapping("/platformUser/{userId}")
    public R<Map<String, Object>> platformUserInfo(@PathVariable String userId) {
        return R.ok(crudService.getById("platformUser", userId));
    }

    @SaCheckPermission("medical:user:add")
    @Log(title = "平台用户", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/platformUser")
    public R<Void> addPlatformUser(@RequestBody Map<String, Object> body) {
        return toAjax(crudService.insert("platformUser", body));
    }

    @SaCheckPermission("medical:user:edit")
    @Log(title = "平台用户", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/platformUser/{userId}")
    public R<Void> editPlatformUser(@PathVariable String userId, @RequestBody Map<String, Object> body) {
        return toAjax(crudService.update("platformUser", userId, body));
    }

    @SaCheckPermission("medical:binding:list")
    @GetMapping("/binding/list")
    public TableDataInfo<Map<String, Object>> bindingList(@RequestParam Map<String, Object> params,
                                                          @RequestParam(required = false) Integer pageNum,
                                                          @RequestParam(required = false) Integer pageSize) {
        return crudService.page("binding", params, pageNum, pageSize);
    }

    @SaCheckPermission("medical:binding:query")
    @GetMapping("/binding/{bindingId}")
    public R<Map<String, Object>> bindingInfo(@PathVariable Long bindingId) {
        return R.ok(crudService.getById("binding", bindingId));
    }

    @SaCheckPermission("medical:binding:add")
    @Log(title = "用户患者绑定", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/binding")
    public R<Void> addBinding(@RequestBody Map<String, Object> body) {
        return toAjax(crudService.insertBinding(body));
    }

    @SaCheckPermission("medical:binding:edit")
    @Log(title = "用户患者绑定", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/binding/{bindingId}")
    public R<Void> editBinding(@PathVariable Long bindingId, @RequestBody Map<String, Object> body) {
        return toAjax(crudService.updateBinding(bindingId, body));
    }
}

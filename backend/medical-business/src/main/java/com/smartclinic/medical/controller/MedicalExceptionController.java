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
import com.smartclinic.medical.service.MedicalMonitorService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 异常规则与异常记录接口。
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/medical/exception")
public class MedicalExceptionController extends BaseController {

    private final MedicalCrudService crudService;
    private final MedicalMonitorService monitorService;

    @SaCheckPermission("medical:exception:rule:list")
    @GetMapping("/rule/list")
    public TableDataInfo<Map<String, Object>> ruleList(@RequestParam Map<String, Object> params,
                                                       @RequestParam(required = false) Integer pageNum,
                                                       @RequestParam(required = false) Integer pageSize) {
        return crudService.page("exceptionRule", params, pageNum, pageSize);
    }

    @SaCheckPermission("medical:exception:list")
    @GetMapping("/list")
    public TableDataInfo<Map<String, Object>> exceptionList(@RequestParam Map<String, Object> params,
                                                            @RequestParam(required = false) Integer pageNum,
                                                            @RequestParam(required = false) Integer pageSize) {
        return crudService.page("exceptionRecord", params, pageNum, pageSize);
    }

    @SaCheckPermission("medical:exception:handle:log")
    @GetMapping("/handle/log/list")
    public TableDataInfo<Map<String, Object>> handleLogList(@RequestParam Map<String, Object> params,
                                                            @RequestParam(required = false) Integer pageNum,
                                                            @RequestParam(required = false) Integer pageSize) {
        return crudService.page("exceptionHandleLog", params, pageNum, pageSize);
    }

    @SaCheckPermission("medical:exception:scan")
    @Log(title = "异常扫描", businessType = BusinessType.OTHER)
    @RepeatSubmit
    @PostMapping("/scan")
    public R<Map<String, Object>> scan() {
        return R.ok(monitorService.scanExceptions());
    }

    @SaCheckPermission("medical:exception:handle")
    @Log(title = "异常处理", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/handle")
    public R<Void> handle(@RequestBody Map<String, Object> body) {
        Long exceptionId = Long.valueOf(String.valueOf(body.get("exceptionId")));
        String status = String.valueOf(body.get("status"));
        Object remark = body.get("remark");
        String userId = String.valueOf(LoginHelper.getUserId());
        String userName = LoginHelper.getUsername();
        return toAjax(monitorService.handleException(exceptionId, status, remark == null ? null : String.valueOf(remark), userId, userName));
    }
}

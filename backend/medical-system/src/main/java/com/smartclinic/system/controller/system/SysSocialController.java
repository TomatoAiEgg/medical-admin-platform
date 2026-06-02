package com.smartclinic.system.controller.system;

import lombok.RequiredArgsConstructor;
import com.smartclinic.common.core.domain.R;
import com.smartclinic.common.satoken.utils.LoginHelper;
import com.smartclinic.common.web.core.BaseController;
import com.smartclinic.system.domain.vo.SysSocialVo;
import com.smartclinic.system.service.ISysSocialService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 社会化关系
 *
 * @author thiszhc
 * @date 2023-06-16
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/social")
public class SysSocialController extends BaseController {

    private final ISysSocialService socialUserService;

    /**
     * 查询社会化关系列表
     */
    @GetMapping("/list")
    public R<List<SysSocialVo>> list() {
        return R.ok(socialUserService.queryListByUserId(LoginHelper.getUserId()));
    }

}

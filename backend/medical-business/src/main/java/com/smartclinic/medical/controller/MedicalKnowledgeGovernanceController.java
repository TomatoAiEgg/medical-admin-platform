package com.smartclinic.medical.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.smartclinic.common.core.domain.R;
import com.smartclinic.common.idempotent.annotation.RepeatSubmit;
import com.smartclinic.common.log.annotation.Log;
import com.smartclinic.common.log.enums.BusinessType;
import com.smartclinic.common.mybatis.core.page.TableDataInfo;
import com.smartclinic.medical.service.MedicalCrudService;
import com.smartclinic.medical.service.MedicalKnowledgeService;
import com.smartclinic.medical.service.MedicalMonitorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Knowledge governance APIs.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/medical/knowledge")
public class MedicalKnowledgeGovernanceController {

    private final MedicalCrudService crudService;
    private final MedicalMonitorService monitorService;
    private final MedicalKnowledgeService knowledgeService;

    @SaCheckPermission("medical:knowledge:document:list")
    @GetMapping("/document/list")
    public TableDataInfo<Map<String, Object>> documentList(@RequestParam Map<String, Object> params,
                                                           @RequestParam(required = false) Integer pageNum,
                                                           @RequestParam(required = false) Integer pageSize) {
        return crudService.page("knowledgeDocument", params, pageNum, pageSize);
    }

    @SaCheckPermission("medical:knowledge:document:list")
    @GetMapping("/document/namespace/summary")
    public R<List<Map<String, Object>>> documentNamespaceSummary() {
        return R.ok(crudService.knowledgeDocumentNamespaceSummary());
    }

    @SaCheckPermission("medical:knowledge:chunk:list")
    @GetMapping("/chunk/list")
    public TableDataInfo<Map<String, Object>> chunkList(@RequestParam Map<String, Object> params,
                                                        @RequestParam(required = false) Integer pageNum,
                                                        @RequestParam(required = false) Integer pageSize) {
        return crudService.page("knowledgeChunk", params, pageNum, pageSize);
    }

    @SaCheckPermission("medical:knowledge:clean:list")
    @GetMapping("/clean/task/list")
    public TableDataInfo<Map<String, Object>> cleanTaskList(@RequestParam Map<String, Object> params,
                                                            @RequestParam(required = false) Integer pageNum,
                                                            @RequestParam(required = false) Integer pageSize) {
        return crudService.page("cleanTask", params, pageNum, pageSize);
    }

    @SaCheckPermission("medical:knowledge:vector:list")
    @GetMapping("/vector/task/list")
    public TableDataInfo<Map<String, Object>> vectorTaskList(@RequestParam Map<String, Object> params,
                                                             @RequestParam(required = false) Integer pageNum,
                                                             @RequestParam(required = false) Integer pageSize) {
        return crudService.page("vectorTask", params, pageNum, pageSize);
    }

    @SaCheckPermission("medical:knowledge:metadata:list")
    @GetMapping("/metadata/revision/list")
    public TableDataInfo<Map<String, Object>> metadataRevisionList(@RequestParam Map<String, Object> params,
                                                                   @RequestParam(required = false) Integer pageNum,
                                                                   @RequestParam(required = false) Integer pageSize) {
        return crudService.page("metadataRevision", params, pageNum, pageSize);
    }

    @SaCheckPermission("medical:knowledge:quality:list")
    @GetMapping("/quality/list")
    public TableDataInfo<Map<String, Object>> qualityList(@RequestParam Map<String, Object> params,
                                                          @RequestParam(required = false) Integer pageNum,
                                                          @RequestParam(required = false) Integer pageSize) {
        return crudService.page("qualityCheck", params, pageNum, pageSize);
    }

    @SaCheckPermission("medical:knowledge:retrieval:list")
    @GetMapping("/retrieval/log/list")
    public TableDataInfo<Map<String, Object>> retrievalLogList(@RequestParam Map<String, Object> params,
                                                               @RequestParam(required = false) Integer pageNum,
                                                               @RequestParam(required = false) Integer pageSize) {
        return crudService.page("retrievalLog", params, pageNum, pageSize);
    }

    @SaCheckPermission("medical:knowledge:document:import")
    @Log(title = "知识文档导入", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/document/import")
    public R<Map<String, Object>> importDocument(@RequestBody Map<String, Object> body) {
        return R.ok(knowledgeService.importDocument(body));
    }

    @SaCheckPermission("medical:knowledge:clean:run")
    @Log(title = "知识文档清洗", businessType = BusinessType.OTHER)
    @RepeatSubmit
    @PostMapping("/clean/run")
    public R<Map<String, Object>> cleanDocument(@RequestBody Map<String, Object> body) {
        UUID documentId = UUID.fromString(String.valueOf(body.get("documentId")));
        Object cleanStrategy = body.get("cleanStrategy");
        return R.ok(knowledgeService.cleanDocument(documentId, cleanStrategy == null ? null : String.valueOf(cleanStrategy)));
    }

    @SaCheckPermission("medical:knowledge:vector:rebuild")
    @Log(title = "知识向量重建", businessType = BusinessType.OTHER)
    @RepeatSubmit
    @PostMapping("/vector/rebuild")
    public R<Map<String, Object>> rebuildVector(@RequestBody Map<String, Object> body) {
        UUID documentId = UUID.fromString(String.valueOf(body.get("documentId")));
        Object embeddingModel = body.get("embeddingModel");
        return R.ok(knowledgeService.rebuildVector(documentId, embeddingModel == null ? null : String.valueOf(embeddingModel)));
    }

    @SaCheckPermission("medical:knowledge:metadata:edit")
    @Log(title = "知识 metadata 修订", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PutMapping("/metadata/revision")
    public R<Map<String, Object>> reviseMetadata(@RequestBody Map<String, Object> body) {
        return R.ok(knowledgeService.reviseMetadata(body));
    }

    @SaCheckPermission("medical:knowledge:retrieval:test")
    @Log(title = "知识检索测试", businessType = BusinessType.OTHER)
    @RepeatSubmit
    @PostMapping("/retrieval/test")
    public R<Map<String, Object>> retrievalTest(@RequestBody Map<String, Object> body) {
        return R.ok(knowledgeService.retrievalTest(body));
    }

    @SaCheckPermission("medical:knowledge:quality:check")
    @Log(title = "知识片段质量检查", businessType = BusinessType.OTHER)
    @RepeatSubmit
    @PostMapping("/quality/check")
    public R<Map<String, Object>> checkQuality() {
        return R.ok(monitorService.checkKnowledgeQuality());
    }
}

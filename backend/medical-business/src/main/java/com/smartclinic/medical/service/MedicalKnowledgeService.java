package com.smartclinic.medical.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import com.smartclinic.common.core.exception.ServiceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Knowledge governance orchestration for medical operation documents.
 */
@Service
@RequiredArgsConstructor
public class MedicalKnowledgeService {

    private static final int DEFAULT_CHUNK_SIZE = 800;
    private static final int DEFAULT_CHUNK_OVERLAP = 80;
    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-v2";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importDocument(Map<String, Object> body) {
        String namespace = required(body, "namespace");
        String sourceId = required(body, "sourceId");
        String sourceName = value(body, "sourceName", sourceId);
        String documentType = value(body, "documentType", "GUIDE").toUpperCase(Locale.ROOT);
        String title = required(body, "title");
        String content = required(body, "content");
        String version = value(body, "version", "v1");
        String status = value(body, "status", "ACTIVE").toUpperCase(Locale.ROOT);
        String embeddingModel = value(body, "embeddingModel", DEFAULT_EMBEDDING_MODEL);
        int chunkSize = intValue(body.get("chunkSize"), DEFAULT_CHUNK_SIZE, 100, 3000);
        int overlap = intValue(body.get("chunkOverlap"), DEFAULT_CHUNK_OVERLAP, 0, Math.max(0, chunkSize - 1));
        String metadataJson = toJson(metadata(body));

        UUID documentId = jdbcTemplate.queryForObject("""
            insert into ai_registration.knowledge_document
              (namespace, source_id, source_name, document_type, title, content_sha256, version, status, metadata)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            on conflict (namespace, source_id, version) do update
              set source_name = excluded.source_name,
                  document_type = excluded.document_type,
                  title = excluded.title,
                  content_sha256 = excluded.content_sha256,
                  status = excluded.status,
                  metadata = excluded.metadata,
                  updated_at = now()
            returning id
            """, UUID.class, namespace, sourceId, sourceName, documentType, title, sha256(content), version, status, metadataJson);
        if (documentId == null) {
            throw new ServiceException("知识文档导入失败");
        }

        String cleaned = cleanText(content);
        UUID cleanTaskId = createCleanTask(documentId, namespace, sourceId, "NORMALIZE_TEXT", content.length(), cleaned.length(), metadataJson);
        List<ChunkDraft> chunks = splitChunks(cleaned, chunkSize, overlap);
        if (chunks.isEmpty()) {
            throw new ServiceException("清洗后内容为空，无法切片");
        }

        UUID vectorTaskId = createVectorTask(documentId, namespace, "VECTORIZE", embeddingModel, chunks.size(), metadataJson);
        applyChunkMetadata(documentId, title, documentType, metadataJson, chunks);
        finishVectorTask(vectorTaskId, embeddingModel, chunks.size(), chunks.size(), 0, null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", documentId);
        result.put("cleanTaskId", cleanTaskId);
        result.put("vectorTaskId", vectorTaskId);
        result.put("chunkCount", chunks.size());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cleanDocument(UUID documentId, String strategy) {
        Map<String, Object> document = getDocument(documentId);
        String namespace = String.valueOf(document.get("namespace"));
        String sourceId = String.valueOf(document.get("source_id"));
        List<Map<String, Object>> chunks = jdbcTemplate.queryForList("""
            select id, content
              from ai_registration.knowledge_chunk
             where document_id = ?
             order by chunk_index asc
            """, documentId);
        String joined = String.join("\n\n", chunks.stream().map(row -> String.valueOf(row.get("content"))).toList());
        String cleaned = cleanText(joined);
        UUID taskId = createCleanTask(documentId, namespace, sourceId, blankToDefault(strategy, "NORMALIZE_TEXT"), joined.length(), cleaned.length(), "{}");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("documentId", documentId);
        result.put("beforeLength", joined.length());
        result.put("afterLength", cleaned.length());
        result.put("changed", !Objects.equals(joined, cleaned));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rebuildVector(UUID documentId, String embeddingModel) {
        Map<String, Object> document = getDocument(documentId);
        String namespace = String.valueOf(document.get("namespace"));
        String model = blankToDefault(embeddingModel, DEFAULT_EMBEDDING_MODEL);
        List<Map<String, Object>> chunks = jdbcTemplate.queryForList("""
            select id, content
              from ai_registration.knowledge_chunk
             where document_id = ?
               and enabled = true
             order by chunk_index asc
            """, documentId);
        if (chunks.isEmpty()) {
            throw new ServiceException("当前文档没有可向量化片段");
        }

        UUID taskId = createVectorTask(documentId, namespace, "REBUILD", model, chunks.size(), "{}");
        finishVectorTask(taskId, model, chunks.size(), chunks.size(), 0, null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("documentId", documentId);
        result.put("chunkCount", chunks.size());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reviseMetadata(Map<String, Object> body) {
        String targetType = required(body, "targetType").toUpperCase(Locale.ROOT);
        String targetId = required(body, "targetId");
        String changedBy = value(body, "changedBy", "admin");
        String reason = value(body, "changeReason", "metadata revision");
        String newMetadata = toJson(metadata(body));
        String table;
        String idColumn;
        if ("DOCUMENT".equals(targetType)) {
            table = "knowledge_document";
            idColumn = "id";
        } else if ("CHUNK".equals(targetType)) {
            table = "knowledge_chunk";
            idColumn = "id";
        } else {
            throw new ServiceException("不支持的 metadata 目标类型: " + targetType);
        }

        String oldMetadata = jdbcTemplate.queryForObject(
            "select metadata::text from ai_registration." + table + " where " + idColumn + " = ?::uuid",
            String.class,
            targetId
        );
        if (oldMetadata == null) {
            throw new ServiceException("metadata 目标不存在: " + targetId);
        }
        jdbcTemplate.update("""
            insert into ai_registration.knowledge_metadata_revision
              (target_type, target_id, old_metadata, new_metadata, changed_by, change_reason)
            values (?, ?, ?::jsonb, ?::jsonb, ?, ?)
            """, targetType, targetId, oldMetadata, newMetadata, changedBy, reason);
        jdbcTemplate.update("update ai_registration." + table + " set metadata = ?::jsonb, updated_at = now() where " + idColumn + " = ?::uuid",
            newMetadata, targetId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetType", targetType);
        result.put("targetId", targetId);
        result.put("metadata", parseJson(newMetadata));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> retrievalTest(Map<String, Object> body) {
        String namespace = required(body, "namespace");
        String query = required(body, "query");
        String embeddingModel = value(body, "embeddingModel", DEFAULT_EMBEDDING_MODEL);
        int topK = intValue(body.get("topK"), 5, 1, 50);
        double minScore = doubleValue(body.get("minScore"), 0D);
        String traceId = "medical-knowledge-" + UUID.randomUUID();
        Instant started = Instant.now();

        try {
            List<Map<String, Object>> hits = searchChunks(namespace, query, topK);
            long latencyMs = Duration.between(started, Instant.now()).toMillis();
            String status = hits.isEmpty() ? "MISS" : "HIT";
            jdbcTemplate.update("""
                insert into ai_registration.knowledge_retrieval_log
                  (trace_id, namespace, corpus_name, query_text, top_k, min_score, status, hit_count,
                   best_hit_id, best_score, latency_ms, hit_ids)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """, traceId, namespace, namespace, query, topK, minScore, status, hits.size(),
                hits.isEmpty() ? null : hits.get(0).get("id"),
                hits.isEmpty() ? null : hits.get(0).get("score"),
                latencyMs,
                toJson(hits.stream().map(hit -> hit.get("id")).toList()));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("traceId", traceId);
            result.put("status", status);
            result.put("latencyMs", latencyMs);
            result.put("hits", hits);
            return result;
        } catch (Exception ex) {
            long latencyMs = Duration.between(started, Instant.now()).toMillis();
            jdbcTemplate.update("""
                insert into ai_registration.knowledge_retrieval_log
                  (trace_id, namespace, corpus_name, query_text, top_k, min_score, status, hit_count, latency_ms, error_message)
                values (?, ?, ?, ?, ?, ?, 'RETRIEVAL_ERROR', 0, ?, ?)
                """, traceId, namespace, namespace, query, topK, minScore, latencyMs, ex.getMessage());
            throw ex;
        }
    }

    private UUID createCleanTask(UUID documentId, String namespace, String sourceId, String strategy,
                                 int beforeLength, int afterLength, String metadataJson) {
        return jdbcTemplate.queryForObject("""
            insert into ai_registration.knowledge_clean_task
              (document_id, namespace, source_id, status, clean_strategy, before_length, after_length, metadata, started_at, finished_at, updated_at)
            values (?, ?, ?, 'SUCCEEDED', ?, ?, ?, ?::jsonb, now(), now(), now())
            returning task_id
            """, UUID.class, documentId, namespace, sourceId, strategy, beforeLength, afterLength, metadataJson);
    }

    private UUID createVectorTask(UUID documentId, String namespace, String taskType, String embeddingModel, int chunkCount, String metadataJson) {
        return jdbcTemplate.queryForObject("""
            insert into ai_registration.knowledge_vector_task
              (document_id, namespace, task_type, status, embedding_model, chunk_count, metadata, started_at, updated_at)
            values (?, ?, ?, 'RUNNING', ?, ?, ?::jsonb, now(), now())
            returning task_id
            """, UUID.class, documentId, namespace, taskType, embeddingModel, chunkCount, metadataJson);
    }

    private void finishVectorTask(UUID taskId, String embeddingModel, int chunkCount, int successCount, int failCount, String errorMessage) {
        jdbcTemplate.update("""
            update ai_registration.knowledge_vector_task
               set status = ?,
                   embedding_model = ?,
                   chunk_count = ?,
                   success_count = ?,
                   fail_count = ?,
                   error_message = ?,
                   finished_at = now(),
                   updated_at = now()
             where task_id = ?
            """, failCount == 0 ? "SUCCEEDED" : "FAILED", embeddingModel, chunkCount, successCount, failCount, errorMessage, taskId);
    }

    private void applyChunkMetadata(UUID documentId, String title, String documentType, String metadataJson, List<ChunkDraft> chunks) {
        for (ChunkDraft chunk : chunks) {
            jdbcTemplate.update("""
                update ai_registration.knowledge_chunk
                   set title = ?,
                       chunk_type = ?,
                       metadata = ?::jsonb,
                       updated_at = now()
                 where id = ?
                   and document_id = ?
                """, title + " #" + (chunk.index() + 1), documentType, metadataJson, chunk.id(), documentId);
        }
    }

    private Map<String, Object> getDocument(UUID documentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            select id, namespace, source_id, metadata
              from ai_registration.knowledge_document
             where id = ?
            """, documentId);
        if (rows.isEmpty()) {
            throw new ServiceException("知识文档不存在: " + documentId);
        }
        return rows.get(0);
    }

    private List<Map<String, Object>> searchChunks(String namespace, String query, int topK) {
        String pattern = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbcTemplate.queryForList("""
            select id::text as id,
                   document_id::text as document_id,
                   namespace,
                   title,
                   content,
                   1.0::float8 as score
              from ai_registration.knowledge_chunk
             where namespace = ?
               and enabled = true
               and content ilike ? escape '\\'
             order by updated_at desc, chunk_index asc
             limit ?
            """, namespace, pattern, topK);
    }

    private List<ChunkDraft> splitChunks(String content, int chunkSize, int overlap) {
        List<ChunkDraft> chunks = new ArrayList<>();
        int start = 0;
        int index = 0;
        while (start < content.length()) {
            int end = Math.min(content.length(), start + chunkSize);
            String text = content.substring(start, end).trim();
            if (!text.isEmpty()) {
                chunks.add(new ChunkDraft(UUID.randomUUID(), index++, text));
            }
            if (end == content.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }

    private String cleanText(String text) {
        return text.replace("\uFEFF", "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replaceAll("[\\t\\x0B\\f]+", " ")
            .replaceAll(" {2,}", " ")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> metadata(Map<String, Object> body) {
        Object metadata = body.get("metadata");
        if (metadata instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            return normalized;
        }
        if (metadata instanceof String text && !text.isBlank()) {
            Object parsed = parseJson(text);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
                return normalized;
            }
        }
        return Map.of();
    }

    private Object parseJson(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            throw new ServiceException("metadata 不是合法 JSON");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ServiceException("JSON 序列化失败");
        }
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new ServiceException("当前 JDK 不支持 SHA-256");
        }
    }

    private String required(Map<String, Object> body, String name) {
        Object value = body == null ? null : body.get(name);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new ServiceException("缺少必要参数: " + name);
        }
        return String.valueOf(value).trim();
    }

    private String value(Map<String, Object> body, String name, String defaultValue) {
        Object value = body == null ? null : body.get(name);
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value).trim();
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private int intValue(Object value, int defaultValue, int min, int max) {
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        int parsed = Integer.parseInt(String.valueOf(value));
        return Math.max(min, Math.min(max, parsed));
    }

    private double doubleValue(Object value, double defaultValue) {
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private record ChunkDraft(UUID id, int index, String content) {
    }
}

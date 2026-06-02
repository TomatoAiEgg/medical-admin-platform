package com.smartclinic.medical.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PGobject;
import com.smartclinic.common.core.exception.ServiceException;
import com.smartclinic.common.core.utils.StringUtils;
import com.smartclinic.common.mybatis.core.page.TableDataInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * 医疗后台表访问服务。
 *
 * <p>本服务只允许访问白名单表与字段，底层复用 Spring JDBC 的生产实现，
 * 用于快速承接 PostgreSQL/jsonb/pgvector 业务表。</p>
 */
@Service
@RequiredArgsConstructor
public class MedicalCrudService {

    private static final String SCHEMA = "ai_registration";
    private static final Set<String> UUID_COLUMNS = Set.of("id", "document_id", "chunk_id");
    private static final Set<String> BIGINT_COLUMNS = Set.of(
        "audit_id",
        "binding_id",
        "check_id",
        "exception_id",
        "log_id",
        "revision_id",
        "rule_id",
        "slot_id"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private final Map<String, TableMeta> metas = buildMetas();

    public TableDataInfo<Map<String, Object>> page(String tableKey, Map<String, Object> params, Integer pageNum, Integer pageSize) {
        TableMeta meta = meta(tableKey);
        int current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 200);

        QueryParts query = buildWhere(meta, params);
        String from = SCHEMA + "." + meta.tableName();
        Long total = jdbcTemplate.queryForObject("select count(1) from " + from + query.where(), Long.class, query.args().toArray());
        String sql = "select * from " + from + query.where() + " order by " + meta.orderColumn() + " desc limit ? offset ?";
        List<Object> args = new ArrayList<>(query.args());
        args.add(size);
        args.add((current - 1) * size);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray()).stream()
            .map(this::normalizeRow)
            .toList();
        return new TableDataInfo<>(rows, total == null ? 0 : total);
    }

    public List<Map<String, Object>> knowledgeDocumentNamespaceSummary() {
        return jdbcTemplate.queryForList("""
            select namespace,
                   count(1) as document_count,
                   count(1) filter (where status = 'ACTIVE') as active_count,
                   max(updated_at) as latest_updated_at
              from ai_registration.knowledge_document
             group by namespace
             order by namespace asc
            """).stream()
            .map(this::normalizeRow)
            .toList();
    }

    public Map<String, Object> getById(String tableKey, Object id) {
        TableMeta meta = meta(tableKey);
        String sql = "select * from " + SCHEMA + "." + meta.tableName() + " where " + meta.idColumn() + " = ?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, id).stream()
            .map(this::normalizeRow)
            .toList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Transactional(rollbackFor = Exception.class)
    public int insert(String tableKey, Map<String, Object> body) {
        TableMeta meta = meta(tableKey);
        Map<String, Object> values = writableValues(meta, body, true);
        if (values.isEmpty()) {
            throw new ServiceException("没有可写入字段");
        }
        String columns = String.join(", ", values.keySet());
        String placeholders = String.join(", ", Collections.nCopies(values.size(), "?"));
        String sql = "insert into " + SCHEMA + "." + meta.tableName() + " (" + columns + ") values (" + placeholders + ")";
        return jdbcTemplate.update(sql, values.values().toArray());
    }

    @Transactional(rollbackFor = Exception.class)
    public int update(String tableKey, Object id, Map<String, Object> body) {
        TableMeta meta = meta(tableKey);
        Map<String, Object> values = writableValues(meta, body, false);
        if (values.isEmpty()) {
            throw new ServiceException("没有可更新字段");
        }
        if (meta.writableColumns().contains("updated_at")) {
            values.put("updated_at", OffsetDateTime.now());
        }
        StringJoiner sets = new StringJoiner(", ");
        values.keySet().forEach(column -> sets.add(column + " = ?"));
        List<Object> args = new ArrayList<>(values.values());
        args.add(id);
        String sql = "update " + SCHEMA + "." + meta.tableName() + " set " + sets + " where " + meta.idColumn() + " = ?";
        return jdbcTemplate.update(sql, args.toArray());
    }

    @Transactional(rollbackFor = Exception.class)
    public int insertBinding(Map<String, Object> body) {
        clearOtherDefaultBinding(null, body);
        return insert("binding", body);
    }

    @Transactional(rollbackFor = Exception.class)
    public int updateBinding(Long bindingId, Map<String, Object> body) {
        clearOtherDefaultBinding(bindingId, body);
        return update("binding", bindingId, body);
    }

    @Transactional(rollbackFor = Exception.class)
    public int changeSlotStatus(Long slotId, String status, String remarks) {
        return jdbcTemplate.update("""
            update ai_registration.clinic_slot
               set status = ?, remarks = coalesce(?, remarks), updated_at = now()
             where slot_id = ?
            """, status, remarks, slotId);
    }

    @Transactional(rollbackFor = Exception.class, noRollbackFor = ServiceException.class)
    public Map<String, Object> createRegistration(Map<String, Object> body, String operatorUserId) {
        Object slotIdValue = getRequiredBodyValue(body, "slotId");
        String userId = String.valueOf(getRequiredBodyValue(body, "userId"));
        String patientId = String.valueOf(getRequiredBodyValue(body, "patientId"));
        Long slotId = Long.valueOf(String.valueOf(slotIdValue));
        String sourceChannel = String.valueOf(body.getOrDefault("sourceChannel", "MANUAL_ADMIN"));
        Object externalRequestIdValue = body.get("externalRequestId");
        String externalRequestId = externalRequestIdValue == null || StrUtil.isBlank(String.valueOf(externalRequestIdValue))
            ? "ADMIN-" + UUID.randomUUID()
            : String.valueOf(externalRequestIdValue);

        List<Map<String, Object>> existing = jdbcTemplate.queryForList("""
            select * from ai_registration.registration_order where external_request_id = ?
            """, externalRequestId).stream().map(this::normalizeRow).toList();
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        Map<String, Object> slot = getById("slot", slotId);
        if (slot == null) {
            writeRegistrationAudit(null, "CREATE", operatorUserId, "号源不存在: " + slotId, body, null, false);
            throw new ServiceException("号源不存在: " + slotId);
        }
        if (!"OPEN".equals(String.valueOf(slot.get("status")))) {
            writeRegistrationAudit(null, "CREATE", operatorUserId, "号源不可预约，当前状态: " + slot.get("status"), body, slot, false);
            throw new ServiceException("号源不可预约，当前状态: " + slot.get("status"));
        }
        if (toInt(slot.get("remaining_slots")) <= 0) {
            writeRegistrationAudit(null, "CREATE", operatorUserId, "号源已满", body, slot, false);
            throw new ServiceException("号源已满");
        }
        if (getById("patient", patientId) == null) {
            writeRegistrationAudit(null, "CREATE", operatorUserId, "患者不存在: " + patientId, body, slot, false);
            throw new ServiceException("患者不存在: " + patientId);
        }
        if (getById("platformUser", userId) == null) {
            writeRegistrationAudit(null, "CREATE", operatorUserId, "平台用户不存在: " + userId, body, slot, false);
            throw new ServiceException("平台用户不存在: " + userId);
        }
        Long bindingCount = jdbcTemplate.queryForObject("""
            select count(1)
              from ai_registration.user_patient_binding
             where user_id = ?
               and patient_id = ?
               and active = true
            """, Long.class, userId, patientId);
        if (bindingCount == null || bindingCount <= 0) {
            writeRegistrationAudit(null, "CREATE", operatorUserId, "平台用户未绑定该患者", body, slot, false);
            throw new ServiceException("平台用户未绑定该患者，请先维护就诊人绑定关系");
        }
        Long duplicateCount = jdbcTemplate.queryForObject("""
            select count(1)
              from ai_registration.registration_order
             where patient_id = ?
               and slot_id = ?
               and status in ('PENDING_CONFIRM', 'BOOKED')
            """, Long.class, patientId, slotId);
        if (duplicateCount != null && duplicateCount > 0) {
            writeRegistrationAudit(null, "CREATE", operatorUserId, "该患者已预约当前号源", body, slot, false);
            throw new ServiceException("该患者已预约当前号源");
        }

        int remainingBefore = toInt(slot.get("remaining_slots"));
        int reserved = jdbcTemplate.update("""
            update ai_registration.clinic_slot
               set remaining_slots = remaining_slots - 1,
                   status = case when remaining_slots - 1 = 0 then 'FULL' else status end,
                   updated_at = now()
             where slot_id = ?
               and status = 'OPEN'
               and remaining_slots > 0
            """, slotId);
        if (reserved != 1) {
            writeRegistrationAudit(null, "CREATE", operatorUserId, "号源扣减失败", body, slot, false);
            throw new ServiceException("号源扣减失败，请刷新后重试");
        }

        String registrationId = "REG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        jdbcTemplate.update("""
            insert into ai_registration.registration_order
                (registration_id, user_id, patient_id, slot_id, department_code, doctor_id, clinic_date, start_time,
                 status, confirmation_required, source_channel, external_request_id, confirmed_at, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?::date, ?::time, 'BOOKED', false, ?, ?, now(), now(), now())
            """,
            registrationId, userId, patientId, slotId, slot.get("department_code"), slot.get("doctor_id"),
            slot.get("clinic_date"), slot.get("start_time"), sourceChannel, externalRequestId);

        Map<String, Object> created = getById("registration", registrationId);
        Map<String, Object> latestSlot = getById("slot", slotId);
        int remainingAfter = latestSlot == null ? Math.max(0, remainingBefore - 1) : toInt(latestSlot.get("remaining_slots"));
        writeRegistrationAudit(registrationId, "CREATE", operatorUserId, "created", body, created, true);
        writeInventoryAudit(slot, "RESERVE", remainingBefore, remainingAfter, "挂号创建扣减号源", "REGISTRATION_CREATE", registrationId);
        return created;
    }

    public Map<String, Object> registrationCreateOptions(Map<String, Object> params) {
        int limit = Math.min(Objects.requireNonNullElse(toNullableInt(getParam(params, "limit")), 50), 200);
        Object clinicDate = blankToNull(getParam(params, "clinicDate"));
        Object departmentCode = blankToNull(getParam(params, "departmentCode"));
        Object doctorId = blankToNull(getParam(params, "doctorId"));
        StringBuilder slotSql = new StringBuilder("""
            select slot_id, department_code, doctor_id, clinic_date, start_time, end_time,
                   status, capacity, remaining_slots, registration_fee, room_no
              from ai_registration.clinic_slot
             where status = 'OPEN'
               and remaining_slots > 0
            """);
        List<Object> slotArgs = new ArrayList<>();
        if (clinicDate != null) {
            slotSql.append(" and clinic_date = ?::date\n");
            slotArgs.add(clinicDate);
        }
        if (departmentCode != null) {
            slotSql.append(" and department_code = ?\n");
            slotArgs.add(departmentCode);
        }
        if (doctorId != null) {
            slotSql.append(" and doctor_id = ?\n");
            slotArgs.add(doctorId);
        }
        slotSql.append(" order by clinic_date asc, start_time asc, slot_id asc limit ?");
        slotArgs.add(limit);
        List<Map<String, Object>> slots = jdbcTemplate.queryForList(slotSql.toString(), slotArgs.toArray())
            .stream().map(this::normalizeRow).toList();
        List<Map<String, Object>> patients = jdbcTemplate.queryForList("""
            select patient_id, patient_name, phone_masked, verified_status, active
              from ai_registration.patient_profile
             where active = true
             order by updated_at desc
             limit ?
            """, limit).stream().map(this::normalizeRow).toList();
        List<Map<String, Object>> users = jdbcTemplate.queryForList("""
            select user_id, display_name, nickname, phone_masked, status
              from ai_registration.platform_user
             where status = 'ACTIVE'
             order by updated_at desc
             limit ?
            """, limit).stream().map(this::normalizeRow).toList();
        List<Map<String, Object>> bindings = jdbcTemplate.queryForList("""
            select binding_id, user_id, patient_id, relation_code, is_default, active
              from ai_registration.user_patient_binding
             where active = true
             order by user_id asc, is_default desc, bound_at desc
             limit ?
            """, limit * 5).stream().map(this::normalizeRow).toList();

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("slots", slots);
        options.put("patients", patients);
        options.put("users", users);
        options.put("bindings", bindings);
        return options;
    }

    @Transactional(rollbackFor = Exception.class)
    public int transitionRegistration(String registrationId, String action, String reason, String operatorUserId) {
        Map<String, Object> before = getById("registration", registrationId);
        if (before == null) {
            throw new ServiceException("挂号订单不存在: " + registrationId);
        }

        String oldStatus = String.valueOf(before.get("status"));
        Transition transition = resolveTransition(action, oldStatus);
        int updated = jdbcTemplate.update("""
            update ai_registration.registration_order
               set status = ?,
                   confirmation_required = case when ? = 'BOOKED' then false else confirmation_required end,
                   confirmed_at = case when ? = 'BOOKED' then coalesce(confirmed_at, now()) else confirmed_at end,
                   cancelled_at = case when ? = 'CANCELLED' then coalesce(cancelled_at, now()) else cancelled_at end,
                   cancel_reason = case when ? = 'CANCELLED' then coalesce(?, cancel_reason) else cancel_reason end,
                   updated_at = now()
             where registration_id = ?
            """, transition.targetStatus(), transition.targetStatus(), transition.targetStatus(), transition.targetStatus(),
            transition.targetStatus(), reason, registrationId);

        Map<String, Object> after = getById("registration", registrationId);
        writeRegistrationAudit(registrationId, transition.operationType(), operatorUserId, reason, before, after, true);

        if (transition.releaseSlot() && before.get("slot_id") != null) {
            releaseSlotForRegistration(before, registrationId, transition.operationType(), reason);
        }
        return updated;
    }

    @Transactional(rollbackFor = Exception.class)
    public int rescheduleRegistration(String registrationId, Long newSlotId, String reason, String operatorUserId) {
        Map<String, Object> before = getById("registration", registrationId);
        if (before == null) {
            throw new ServiceException("挂号订单不存在: " + registrationId);
        }
        String oldStatus = String.valueOf(before.get("status"));
        ensureStatus(oldStatus, Set.of("PENDING_CONFIRM", "BOOKED", "RESCHEDULED"), "只有待确认或已预约订单可以改约");

        Object oldSlotId = before.get("slot_id");
        if (oldSlotId != null && String.valueOf(oldSlotId).equals(String.valueOf(newSlotId))) {
            throw new ServiceException("新旧号源不能相同");
        }

        Map<String, Object> newSlot = getById("slot", newSlotId);
        if (newSlot == null) {
            throw new ServiceException("新号源不存在: " + newSlotId);
        }
        if (!"OPEN".equals(String.valueOf(newSlot.get("status")))) {
            throw new ServiceException("新号源不可预约，当前状态: " + newSlot.get("status"));
        }
        int newSlotRemainingBefore = toInt(newSlot.get("remaining_slots"));
        if (newSlotRemainingBefore <= 0) {
            throw new ServiceException("新号源已满");
        }

        Long duplicateCount = jdbcTemplate.queryForObject("""
            select count(1)
              from ai_registration.registration_order
             where patient_id = ?
               and slot_id = ?
               and registration_id <> ?
               and status in ('PENDING_CONFIRM', 'BOOKED', 'RESCHEDULED')
            """, Long.class, before.get("patient_id"), newSlotId, registrationId);
        if (duplicateCount != null && duplicateCount > 0) {
            throw new ServiceException("该患者已预约新号源");
        }

        int reserved = jdbcTemplate.update("""
            update ai_registration.clinic_slot
               set remaining_slots = remaining_slots - 1,
                   status = case when remaining_slots - 1 = 0 then 'FULL' else status end,
                   updated_at = now()
             where slot_id = ?
               and status = 'OPEN'
               and remaining_slots > 0
            """, newSlotId);
        if (reserved != 1) {
            throw new ServiceException("新号源预占失败，请刷新后重试");
        }

        int updated = jdbcTemplate.update("""
            update ai_registration.registration_order
               set slot_id = ?,
                   department_code = ?,
                   doctor_id = ?,
                   clinic_date = ?::date,
                   start_time = ?::time,
                   status = 'RESCHEDULED',
                   confirmation_required = false,
                   confirmed_at = coalesce(confirmed_at, now()),
                   updated_at = now()
             where registration_id = ?
            """, newSlotId, newSlot.get("department_code"), newSlot.get("doctor_id"),
            newSlot.get("clinic_date"), newSlot.get("start_time"), registrationId);

        releaseSlotForRegistration(before, registrationId, "RESCHEDULE", reason);
        Map<String, Object> after = getById("registration", registrationId);
        writeInventoryAudit(newSlot, "RESERVE", newSlotRemainingBefore, newSlotRemainingBefore - 1,
            reason, "REGISTRATION_RESCHEDULE", registrationId + "-reschedule-reserve-" + newSlotId);
        writeRegistrationAudit(registrationId, "RESCHEDULE", operatorUserId, reason, before, after, true);
        return updated;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> expireOverdueRegistrations(String reason, String operatorUserId) {
        List<Map<String, Object>> overdueOrders = jdbcTemplate.queryForList("""
            select *
              from ai_registration.registration_order
             where status in ('PENDING_CONFIRM', 'BOOKED', 'RESCHEDULED')
               and (
                    clinic_date < current_date
                    or (clinic_date = current_date and start_time < current_time)
               )
             order by clinic_date asc, start_time asc, registration_id asc
             limit 500
            """).stream().map(this::normalizeRow).toList();

        int expiredCount = 0;
        for (Map<String, Object> before : overdueOrders) {
            String registrationId = String.valueOf(before.get("registration_id"));
            int updated = jdbcTemplate.update("""
                update ai_registration.registration_order
                   set status = 'EXPIRED',
                       updated_at = now()
                 where registration_id = ?
                   and status in ('PENDING_CONFIRM', 'BOOKED', 'RESCHEDULED')
                """, registrationId);
            if (updated == 1) {
                expiredCount++;
                Map<String, Object> after = getById("registration", registrationId);
                writeRegistrationAudit(registrationId, "STATUS_CHANGE", operatorUserId,
                    reason == null ? "后台同步过期订单" : reason, before, after, true);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("expiredCount", expiredCount);
        result.put("scannedCount", overdueOrders.size());
        result.put("limit", 500);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public int adjustSlotInventory(Long slotId, Integer capacity, Integer remainingSlots, String reason) {
        Map<String, Object> slot = getById("slot", slotId);
        if (slot == null) {
            throw new ServiceException("号源不存在: " + slotId);
        }
        int oldCapacity = toInt(slot.get("capacity"));
        int oldRemaining = toInt(slot.get("remaining_slots"));
        int newCapacity = capacity == null ? oldCapacity : capacity;
        int newRemaining = remainingSlots == null ? Math.min(oldRemaining, newCapacity) : remainingSlots;
        if (newCapacity < 0 || newRemaining < 0 || newRemaining > newCapacity) {
            throw new ServiceException("号源容量或剩余号源不合法");
        }

        int updated = jdbcTemplate.update("""
            update ai_registration.clinic_slot
               set capacity = ?,
                   remaining_slots = ?,
                   updated_at = now()
             where slot_id = ?
            """, newCapacity, newRemaining, slotId);
        writeInventoryAudit(slot, "ADJUST", oldRemaining, newRemaining, reason, "SLOT_INVENTORY_ADJUST", "slot-" + slotId + "-adjust-" + System.nanoTime());
        return updated;
    }

    @Transactional(rollbackFor = Exception.class)
    public int changeSlotOperationalStatus(Long slotId, String status, String reason) {
        Map<String, Object> slot = getById("slot", slotId);
        if (slot == null) {
            throw new ServiceException("号源不存在: " + slotId);
        }
        String targetStatus = normalizeSlotStatus(status);
        int oldRemaining = toInt(slot.get("remaining_slots"));
        int updated = jdbcTemplate.update("""
            update ai_registration.clinic_slot
               set status = ?,
                   remarks = coalesce(?, remarks),
                   updated_at = now()
             where slot_id = ?
            """, targetStatus, reason, slotId);
        String operationType = "OPEN".equals(targetStatus) ? "RESUME" : "SUSPEND";
        writeInventoryAudit(slot, operationType, oldRemaining, oldRemaining, reason, "SLOT_STATUS_CHANGE", "slot-" + slotId + "-" + targetStatus + "-" + System.nanoTime());
        return updated;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchGenerateSlots(Map<String, Object> body) {
        String departmentCode = String.valueOf(getRequiredBodyValue(body, "departmentCode"));
        String doctorId = String.valueOf(getRequiredBodyValue(body, "doctorId"));
        LocalDate startDate = LocalDate.parse(String.valueOf(getRequiredBodyValue(body, "startDate")));
        LocalDate endDate = LocalDate.parse(String.valueOf(getRequiredBodyValue(body, "endDate")));
        LocalTime startTime = LocalTime.parse(String.valueOf(getRequiredBodyValue(body, "startTime")));
        LocalTime endTime = LocalTime.parse(String.valueOf(getRequiredBodyValue(body, "endTime")));
        int capacity = toInt(getRequiredBodyValue(body, "capacity"));
        int intervalMinutes = Objects.requireNonNullElse(toNullableInt(getParam(body, "intervalMinutes")), 30);
        String sourceType = String.valueOf(body.getOrDefault("sourceType", "LOCAL"));
        Object registrationFee = body.get("registrationFee");
        Object roomNo = body.get("roomNo");
        Object remarks = body.get("remarks");
        Set<Integer> weekdays = parseWeekdays(body.get("weekdays"));

        if (endDate.isBefore(startDate)) {
            throw new ServiceException("结束日期不能早于开始日期");
        }
        if (!endTime.isAfter(startTime)) {
            throw new ServiceException("结束时间必须晚于开始时间");
        }
        if (capacity <= 0) {
            throw new ServiceException("号源容量必须大于 0");
        }
        if (intervalMinutes <= 0 || intervalMinutes > 240) {
            throw new ServiceException("号源间隔分钟数不合法");
        }
        Long doctorCount = jdbcTemplate.queryForObject("""
            select count(1)
              from ai_registration.doctor
             where doctor_id = ?
               and department_code = ?
               and active = true
            """, Long.class, doctorId, departmentCode);
        if (doctorCount == null || doctorCount == 0) {
            throw new ServiceException("医生不存在、已停用或不属于所选科室");
        }

        int createdCount = 0;
        int skippedCount = 0;
        int candidateCount = 0;
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            int dayValue = date.getDayOfWeek().getValue();
            if (!weekdays.isEmpty() && !weekdays.contains(dayValue)) {
                continue;
            }
            for (LocalTime cursor = startTime; cursor.plusMinutes(intervalMinutes).compareTo(endTime) <= 0; cursor = cursor.plusMinutes(intervalMinutes)) {
                candidateCount++;
                LocalTime slotEndTime = cursor.plusMinutes(intervalMinutes);
                int inserted = jdbcTemplate.update("""
                    insert into ai_registration.clinic_slot
                        (department_code, doctor_id, clinic_date, start_time, end_time, source_type, status,
                         capacity, remaining_slots, registration_fee, room_no, remarks, created_at, updated_at)
                    values (?, ?, ?::date, ?::time, ?::time, ?, 'OPEN', ?, ?, ?, ?, ?, now(), now())
                    on conflict (doctor_id, clinic_date, start_time) do nothing
                    """, departmentCode, doctorId, date, cursor, slotEndTime, sourceType, capacity, capacity,
                    registrationFee, roomNo, remarks);
                if (inserted == 1) {
                    createdCount++;
                    Map<String, Object> slot = jdbcTemplate.queryForList("""
                        select *
                          from ai_registration.clinic_slot
                         where doctor_id = ?
                           and clinic_date = ?::date
                           and start_time = ?::time
                         limit 1
                        """, doctorId, date, cursor).stream().map(this::normalizeRow).findFirst().orElse(Map.of(
                        "department_code", departmentCode,
                        "doctor_id", doctorId,
                        "clinic_date", date,
                        "start_time", cursor
                    ));
                    writeInventoryAudit(slot, "ADJUST", 0, capacity, "批量放号生成号源",
                        "SLOT_BATCH_GENERATE", "slot-batch-" + doctorId + "-" + date + "-" + cursor);
                } else {
                    skippedCount++;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("candidateCount", candidateCount);
        result.put("createdCount", createdCount);
        result.put("skippedCount", skippedCount);
        return result;
    }

    public TableDataInfo<Map<String, Object>> auditByRegistration(String registrationId, Integer pageNum, Integer pageSize) {
        return page("registrationAudit", Map.of("registrationId", registrationId), pageNum, pageSize);
    }

    public TableDataInfo<Map<String, Object>> inventoryAuditByRegistration(String registrationId, Integer pageNum, Integer pageSize) {
        Map<String, Object> order = getById("registration", registrationId);
        if (order == null || order.get("slot_id") == null) {
            return new TableDataInfo<>(List.of(), 0);
        }
        Map<String, Object> slot = getById("slot", order.get("slot_id"));
        if (slot == null) {
            return new TableDataInfo<>(List.of(), 0);
        }
        Map<String, Object> params = new HashMap<>();
        params.put("departmentCode", slot.get("department_code"));
        params.put("doctorId", slot.get("doctor_id"));
        params.put("clinicDate", slot.get("clinic_date"));
        params.put("startTime", slot.get("start_time"));
        return page("inventoryAudit", params, pageNum, pageSize);
    }

    public List<Map<String, Object>> registrationTimeline(String registrationId) {
        Map<String, Object> order = getById("registration", registrationId);
        if (order == null) {
            return List.of();
        }

        List<Map<String, Object>> events = new ArrayList<>();
        addTimelineEvent(events, "ORDER", order.get("status"), "订单当前状态", order.get("updated_at"), order);

        List<Map<String, Object>> audits = jdbcTemplate.queryForList("""
            select audit_id, operation_type, success, reason, source_service, trace_id, created_at,
                   request_payload, response_payload, before_snapshot, after_snapshot
              from ai_registration.registration_audit_log
             where registration_id = ?
             order by created_at asc, audit_id asc
            """, registrationId).stream().map(this::normalizeRow).toList();
        for (Map<String, Object> audit : audits) {
            String title = "挂号操作：" + audit.getOrDefault("operation_type", "");
            addTimelineEvent(events, "REGISTRATION_AUDIT", audit.get("operation_type"), title, audit.get("created_at"), audit);
        }

        Object slotId = order.get("slot_id");
        if (slotId != null) {
            Map<String, Object> slot = getById("slot", slotId);
            if (slot != null) {
                List<Map<String, Object>> inventoryAudits = jdbcTemplate.queryForList("""
                    select audit_id, operation_type, trace_id, department_code, doctor_id, clinic_date, start_time,
                           success, reason, remaining_before, remaining_after, source_service, operation_id,
                           operation_source, created_at
                      from ai_registration.clinic_slot_inventory_audit_log
                     where department_code = ?
                       and doctor_id = ?
                       and clinic_date = ?
                       and start_time = ?
                     order by created_at asc, audit_id asc
                    """, slot.get("department_code"), slot.get("doctor_id"), slot.get("clinic_date"), slot.get("start_time"))
                    .stream().map(this::normalizeRow).toList();
                for (Map<String, Object> audit : inventoryAudits) {
                    String title = "号源库存：" + audit.getOrDefault("operation_type", "");
                    addTimelineEvent(events, "INVENTORY_AUDIT", audit.get("operation_type"), title, audit.get("created_at"), audit);
                }
            }
        }

        events.sort(Comparator.comparing(event -> String.valueOf(event.getOrDefault("event_time", ""))));
        return events;
    }

    public Map<String, Object> registrationDetail(String registrationId) {
        Map<String, Object> order = getById("registration", registrationId);
        if (order == null) {
            throw new ServiceException("挂号订单不存在: " + registrationId);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("order", order);
        detail.put("user", getById("platformUser", order.get("user_id")));
        detail.put("patient", getById("patient", order.get("patient_id")));
        detail.put("doctor", getById("doctor", order.get("doctor_id")));
        detail.put("department", getById("department", order.get("department_code")));
        detail.put("slot", order.get("slot_id") == null ? null : getById("slot", order.get("slot_id")));
        detail.put("binding", queryOne("""
            select *
              from ai_registration.user_patient_binding
             where user_id = ?
               and patient_id = ?
             order by active desc, is_default desc, updated_at desc
             limit 1
            """, order.get("user_id"), order.get("patient_id")));
        detail.put("auditLogs", jdbcTemplate.queryForList("""
            select audit_id, registration_id, operation_type, operator_user_id, source_service, success,
                   reason, trace_id, created_at, request_payload, response_payload, before_snapshot, after_snapshot
              from ai_registration.registration_audit_log
             where registration_id = ?
             order by created_at asc, audit_id asc
            """, registrationId).stream().map(this::normalizeRow).toList());
        detail.put("inventoryAuditLogs", inventoryAuditRowsForOrder(order));
        detail.put("exceptions", jdbcTemplate.queryForList("""
            select exception_id, rule_code, exception_type, severity, title, content, status,
                   handled_by, handled_at, handle_remark, detected_at, updated_at, evidence_json
              from ai_registration.monitor_exception_record
             where registration_id = ?
                or patient_id = ?
                or slot_id = ?
             order by detected_at desc, exception_id desc
             limit 100
            """, registrationId, order.get("patient_id"), order.get("slot_id")).stream().map(this::normalizeRow).toList());
        detail.put("timeline", registrationTimeline(registrationId));
        return detail;
    }

    private void addTimelineEvent(List<Map<String, Object>> events, String eventType, Object status, String title, Object eventTime, Map<String, Object> payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_type", eventType);
        event.put("status", status);
        event.put("title", title);
        event.put("event_time", eventTime);
        event.put("payload", payload);
        events.add(event);
    }

    private List<Map<String, Object>> inventoryAuditRowsForOrder(Map<String, Object> order) {
        Object slotId = order.get("slot_id");
        if (slotId == null) {
            return List.of();
        }
        Map<String, Object> slot = getById("slot", slotId);
        if (slot == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList("""
            select audit_id, operation_type, trace_id, department_code, doctor_id, clinic_date, start_time,
                   success, reason, remaining_before, remaining_after, source_service, operation_id,
                   operation_source, created_at
              from ai_registration.clinic_slot_inventory_audit_log
             where department_code = ?
               and doctor_id = ?
               and clinic_date = ?
               and start_time = ?
             order by created_at asc, audit_id asc
            """, slot.get("department_code"), slot.get("doctor_id"), slot.get("clinic_date"), slot.get("start_time"))
            .stream().map(this::normalizeRow).toList();
    }

    private Map<String, Object> queryOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args).stream().map(this::normalizeRow).toList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    public TableMeta meta(String tableKey) {
        TableMeta meta = metas.get(tableKey);
        if (meta == null) {
            throw new ServiceException("未开放的数据表: " + tableKey);
        }
        return meta;
    }

    private QueryParts buildWhere(TableMeta meta, Map<String, Object> params) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, String> entry : meta.filterColumns().entrySet()) {
            Object value = getParam(params, entry.getKey());
            if (value == null || StrUtil.isBlankIfStr(value)) {
                continue;
            }
            String column = entry.getValue();
            if (meta.likeColumns().contains(column)) {
                appendWhere(where, column + " like ?");
                args.add("%" + value + "%");
            } else if (UUID_COLUMNS.contains(column)) {
                appendWhere(where, column + " = ?::uuid");
                args.add(value);
            } else if (BIGINT_COLUMNS.contains(column)) {
                appendWhere(where, column + " = ?::bigint");
                args.add(value);
            } else {
                appendWhere(where, column + " = ?");
                args.add(value);
            }
        }
        appendDateRangeWhere(meta, params, where, args);
        return new QueryParts(where.isEmpty() ? "" : " where " + where, args);
    }

    private void appendDateRangeWhere(TableMeta meta, Map<String, Object> params, StringBuilder where, List<Object> args) {
        String dateColumn = meta.filterColumns().get("clinicDate");
        if (dateColumn == null) {
            return;
        }
        appendRangeCondition(params, where, args, dateColumn, "startDate", ">=");
        appendRangeCondition(params, where, args, dateColumn, "endDate", "<=");
    }

    private void appendRangeCondition(Map<String, Object> params, StringBuilder where, List<Object> args,
                                      String column, String paramName, String operator) {
        Object value = getParam(params, paramName);
        if (value == null || StrUtil.isBlankIfStr(value)) {
            return;
        }
        appendWhere(where, column + " " + operator + " ?::date");
        args.add(value);
    }

    private void appendWhere(StringBuilder where, String condition) {
        if (!where.isEmpty()) {
            where.append(" and ");
        }
        where.append(condition);
    }

    private Object getParam(Map<String, Object> params, String name) {
        if (params == null) {
            return null;
        }
        Object value = params.get(name);
        if (value != null) {
            return value;
        }
        return params.get(StringUtils.toUnderScoreCase(name));
    }

    private Object getRequiredBodyValue(Map<String, Object> body, String name) {
        Object value = getParam(body, name);
        if (value == null || StrUtil.isBlankIfStr(value)) {
            throw new ServiceException("缺少必要参数: " + name);
        }
        return value;
    }

    private Map<String, Object> writableValues(TableMeta meta, Map<String, Object> body, boolean insert) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (body == null) {
            return values;
        }
        for (String column : meta.writableColumns()) {
            if (!insert && column.equals(meta.idColumn())) {
                continue;
            }
            Object value = getParam(body, StringUtils.toCamelCase(column));
            if (value == null && body.containsKey(column)) {
                value = body.get(column);
            }
            if (value != null) {
                values.put(column, value);
            }
        }
        return values;
    }

    private Map<String, Object> normalizeRow(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        row.forEach((key, value) -> normalized.put(key, normalizeValue(value)));
        return normalized;
    }

    private Object normalizeValue(Object value) {
        if (value instanceof PGobject pgObject) {
            return pgObject.getValue();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        return value;
    }

    private Transition resolveTransition(String action, String oldStatus) {
        String normalized = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CONFIRM" -> {
                ensureStatus(oldStatus, Set.of("PENDING_CONFIRM", "BOOKED", "RESCHEDULED"), "只有待确认订单可以确认");
                yield new Transition("BOOKED", "CONFIRM", false);
            }
            case "CANCEL" -> {
                ensureStatus(oldStatus, Set.of("PENDING_CONFIRM", "BOOKED", "RESCHEDULED"), "只有待确认或已预约订单可以取消");
                yield new Transition("CANCELLED", "CANCEL", Set.of("BOOKED", "RESCHEDULED").contains(oldStatus));
            }
            case "COMPLETE" -> {
                ensureStatus(oldStatus, Set.of("BOOKED", "RESCHEDULED"), "只有已预约订单可以完成就诊");
                yield new Transition("COMPLETED", "COMPLETE", false);
            }
            case "NO_SHOW" -> {
                ensureStatus(oldStatus, Set.of("BOOKED", "RESCHEDULED"), "只有已预约订单可以标记爽约");
                yield new Transition("NO_SHOW", "NO_SHOW", false);
            }
            default -> throw new ServiceException("不支持的订单操作: " + action);
        };
    }

    private void ensureStatus(String status, Set<String> allowed, String message) {
        if (!allowed.contains(status)) {
            throw new ServiceException(message + "，当前状态: " + status);
        }
    }

    private void releaseSlotForRegistration(Map<String, Object> registration, String registrationId, String operationType, String reason) {
        Object slotId = registration.get("slot_id");
        Map<String, Object> slot = getById("slot", slotId);
        if (slot == null) {
            return;
        }
        int beforeRemaining = toInt(slot.get("remaining_slots"));
        int capacity = toInt(slot.get("capacity"));
        int afterRemaining = Math.min(capacity, beforeRemaining + 1);
        jdbcTemplate.update("""
            update ai_registration.clinic_slot
               set remaining_slots = ?,
                   status = case when status = 'FULL' then 'OPEN' else status end,
                   updated_at = now()
             where slot_id = ?
            """, afterRemaining, slotId);
        writeInventoryAudit(slot, "RELEASE", beforeRemaining, afterRemaining, reason,
            "REGISTRATION_" + operationType, registrationId + "-" + operationType + "-release-" + System.nanoTime());
    }

    private void writeRegistrationAudit(String registrationId, String operationType, String operatorUserId, String reason,
                                        Map<String, Object> before, Map<String, Object> after, boolean success) {
        jdbcTemplate.update("""
            insert into ai_registration.registration_audit_log
                (registration_id, operation_type, operator_user_id, source_service, success, reason,
                 request_payload, response_payload, before_snapshot, after_snapshot)
            values (?, ?, ?, 'medical-admin', ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb)
            """,
            registrationId, operationType, operatorUserId, success, reason,
            toJson(Map.of("registrationId", registrationId, "operationType", operationType)),
            toJson(responseSnapshot(after)),
            toJson(before == null ? Map.of() : before),
            toJson(after == null ? Map.of() : after));
    }

    private void writeInventoryAudit(Map<String, Object> slot, String operationType, int remainingBefore, int remainingAfter,
                                     String reason, String operationSource, String operationId) {
        jdbcTemplate.update("""
            insert into ai_registration.clinic_slot_inventory_audit_log
                (operation_type, department_code, doctor_id, clinic_date, start_time, success, reason,
                 remaining_before, remaining_after, source_service, operation_id, operation_source)
            values (?, ?, ?, ?::date, ?::time, true, ?, ?, ?, 'medical-admin', ?, ?)
            on conflict do nothing
            """,
            operationType, slot.get("department_code"), slot.get("doctor_id"), slot.get("clinic_date"), slot.get("start_time"),
            reason, remainingBefore, remainingAfter, operationId, operationSource);
    }

    private String normalizeSlotStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("OPEN", "CLOSED", "SUSPENDED", "CANCELLED").contains(normalized)) {
            throw new ServiceException("不支持的号源状态: " + status);
        }
        return normalized;
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private Integer toNullableInt(Object value) {
        if (value == null || StrUtil.isBlankIfStr(value)) {
            return null;
        }
        return toInt(value);
    }

    private Object blankToNull(Object value) {
        return value == null || StrUtil.isBlankIfStr(value) ? null : value;
    }

    private void clearOtherDefaultBinding(Long bindingId, Map<String, Object> body) {
        Object isDefault = getParam(body, "isDefault");
        if (!Boolean.parseBoolean(String.valueOf(isDefault))) {
            return;
        }
        Object userId = getRequiredBodyValue(body, "userId");
        jdbcTemplate.update("""
            update ai_registration.user_patient_binding
               set is_default = false,
                   updated_at = now()
             where user_id = ?
               and active = true
               and (?::bigint is null or binding_id <> ?::bigint)
            """, userId, bindingId, bindingId);
    }

    private Set<Integer> parseWeekdays(Object value) {
        if (value == null || StrUtil.isBlankIfStr(value)) {
            return Set.of();
        }
        Set<Integer> weekdays = new LinkedHashSet<>();
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                weekdays.add(toInt(item));
            }
        } else {
            String[] items = String.valueOf(value).split(",");
            for (String item : items) {
                if (!item.isBlank()) {
                    weekdays.add(Integer.parseInt(item.trim()));
                }
            }
        }
        for (Integer weekday : weekdays) {
            if (weekday < 1 || weekday > 7) {
                throw new ServiceException("星期参数必须在 1 到 7 之间");
            }
        }
        return weekdays;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ServiceException("JSON 序列化失败");
        }
    }

    private Map<String, Object> responseSnapshot(Map<String, Object> after) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", after == null ? null : after.get("status"));
        return snapshot;
    }

    private Map<String, TableMeta> buildMetas() {
        Map<String, TableMeta> map = new HashMap<>();
        add(map, "department", "department", "department_code", "updated_at",
            cols("department_code", "department_name", "category_code", "description", "online_enabled", "triage_priority", "sort_order", "active", "updated_at"),
            filters("departmentCode:department_code", "departmentName:department_name", "categoryCode:category_code", "active:active", "onlineEnabled:online_enabled"),
            set("department_name", "description"));
        add(map, "doctor", "doctor", "doctor_id", "updated_at",
            cols("doctor_id", "department_code", "doctor_name", "title_name", "speciality", "intro", "active", "title", "specialty", "online_enabled", "updated_at"),
            filters("doctorId:doctor_id", "doctorName:doctor_name", "departmentCode:department_code", "active:active", "onlineEnabled:online_enabled"),
            set("doctor_name", "speciality", "specialty", "intro"));
        add(map, "slot", "clinic_slot", "slot_id", "updated_at",
            cols("department_code", "doctor_id", "clinic_date", "start_time", "end_time", "source_type", "status", "capacity", "remaining_slots", "registration_fee", "room_no", "remarks", "updated_at"),
            filters("slotId:slot_id", "departmentCode:department_code", "doctorId:doctor_id", "clinicDate:clinic_date", "status:status", "sourceType:source_type"),
            set("remarks", "room_no"));
        add(map, "patient", "patient_profile", "patient_id", "updated_at",
            cols("patient_id", "patient_name", "id_type", "id_number_masked", "phone_masked", "active", "verified_status", "source_channel", "updated_at"),
            filters("patientId:patient_id", "patientName:patient_name", "phoneMasked:phone_masked", "verifiedStatus:verified_status", "active:active"),
            set("patient_name", "phone_masked", "id_number_masked"));
        add(map, "platformUser", "platform_user", "user_id", "updated_at",
            cols("user_id", "open_id", "union_id", "nickname", "display_name", "status", "avatar_url", "source_channel", "phone_masked", "updated_at"),
            filters("userId:user_id", "displayName:display_name", "nickname:nickname", "status:status", "sourceChannel:source_channel"),
            set("display_name", "nickname", "phone_masked"));
        add(map, "binding", "user_patient_binding", "binding_id", "updated_at",
            cols("binding_id", "user_id", "patient_id", "relation_code", "is_default", "active", "bound_at", "created_at", "updated_at"),
            filters("bindingId:binding_id", "userId:user_id", "patientId:patient_id", "relationCode:relation_code", "isDefault:is_default", "active:active"),
            set());
        add(map, "registration", "registration_order", "registration_id", "updated_at",
            cols("registration_id", "user_id", "patient_id", "slot_id", "department_code", "doctor_id", "clinic_date", "start_time", "status", "confirmation_required", "source_channel", "chat_id", "external_request_id", "cancel_reason", "confirmed_at", "cancelled_at", "updated_at"),
            filters("registrationId:registration_id", "userId:user_id", "patientId:patient_id", "slotId:slot_id", "departmentCode:department_code", "doctorId:doctor_id", "clinicDate:clinic_date", "status:status", "sourceChannel:source_channel"),
            set("registration_id", "user_id", "patient_id", "department_code", "doctor_id", "status", "cancel_reason"));
        add(map, "registrationAudit", "registration_audit_log", "audit_id", "created_at",
            cols("registration_id", "operation_type", "operator_user_id", "chat_id", "source_service", "success", "reason", "trace_id"),
            filters("registrationId:registration_id", "operationType:operation_type", "operatorUserId:operator_user_id", "traceId:trace_id", "success:success"),
            set("reason"));
        add(map, "inventoryAudit", "clinic_slot_inventory_audit_log", "audit_id", "created_at",
            cols("operation_type", "trace_id", "department_code", "doctor_id", "clinic_date", "start_time", "success", "reason", "remaining_before", "remaining_after", "source_service", "operation_id", "operation_source"),
            filters("departmentCode:department_code", "doctorId:doctor_id", "clinicDate:clinic_date", "startTime:start_time", "operationType:operation_type", "success:success", "traceId:trace_id"),
            set("reason"));
        add(map, "exceptionRule", "monitor_exception_rule", "rule_id", "updated_at",
            cols("rule_code", "rule_name", "rule_type", "severity", "enabled", "config_json", "description", "updated_at"),
            filters("ruleCode:rule_code", "ruleName:rule_name", "ruleType:rule_type", "severity:severity", "enabled:enabled"),
            set("rule_name", "description"));
        add(map, "exceptionRecord", "monitor_exception_record", "exception_id", "updated_at",
            cols("rule_code", "exception_type", "severity", "registration_id", "user_id", "patient_id", "doctor_id", "department_code", "slot_id", "biz_date", "title", "content", "evidence_json", "status", "handled_by", "handled_at", "handle_remark", "updated_at"),
            filters("ruleCode:rule_code", "exceptionType:exception_type", "severity:severity", "registrationId:registration_id", "userId:user_id", "patientId:patient_id", "doctorId:doctor_id", "departmentCode:department_code", "status:status", "bizDate:biz_date"),
            set("title", "content"));
        add(map, "exceptionHandleLog", "monitor_exception_handle_log", "log_id", "created_at",
            cols("exception_id", "old_status", "new_status", "handle_user_id", "handle_user_name", "remark"),
            filters("exceptionId:exception_id", "oldStatus:old_status", "newStatus:new_status", "handleUserId:handle_user_id", "handleUserName:handle_user_name"),
            set("remark"));
        add(map, "cleanTask", "knowledge_clean_task", "task_id", "updated_at",
            cols("task_id", "document_id", "namespace", "source_id", "status", "clean_strategy", "before_length", "after_length", "error_message", "metadata", "started_at", "finished_at", "updated_at"),
            filters("taskId:task_id", "documentId:document_id", "namespace:namespace", "sourceId:source_id", "status:status"),
            set("namespace", "source_id", "status"));
        add(map, "vectorTask", "knowledge_vector_task", "task_id", "updated_at",
            cols("task_id", "document_id", "namespace", "task_type", "status", "embedding_model", "embedding_dimensions", "chunk_count", "success_count", "fail_count", "error_message", "metadata", "started_at", "finished_at", "updated_at"),
            filters("taskId:task_id", "documentId:document_id", "namespace:namespace", "taskType:task_type", "status:status"),
            set("namespace", "task_type", "status"));
        add(map, "metadataRevision", "knowledge_metadata_revision", "revision_id", "created_at",
            cols("target_type", "target_id", "old_metadata", "new_metadata", "changed_by", "change_reason"),
            filters("targetType:target_type", "targetId:target_id", "changedBy:changed_by"),
            set("change_reason"));
        add(map, "qualityCheck", "knowledge_chunk_quality_check", "check_id", "updated_at",
            cols("chunk_id", "namespace", "chunk_table", "issue_type", "severity", "issue_detail", "status", "resolved_by", "resolved_at", "updated_at"),
            filters("chunkId:chunk_id", "namespace:namespace", "chunkTable:chunk_table", "issueType:issue_type", "severity:severity", "status:status"),
            set("issue_detail", "status"));
        add(map, "knowledgeDocument", "knowledge_document", "id", "updated_at",
            cols("id", "namespace", "source_id", "source_name", "document_type", "title", "content_sha256", "version", "status", "metadata", "updated_at"),
            filters("id:id", "namespace:namespace", "sourceId:source_id", "sourceName:source_name", "documentType:document_type", "title:title", "status:status"),
            set("title", "source_name"));
        add(map, "knowledgeChunk", "knowledge_chunk", "id", "updated_at",
            cols("id", "document_id", "namespace", "chunk_index", "chunk_type", "title", "content", "token_count", "metadata", "embedding_model", "embedding_dimensions", "enabled", "updated_at"),
            filters("id:id", "documentId:document_id", "namespace:namespace", "chunkType:chunk_type", "title:title", "enabled:enabled"),
            set("title", "content"));
        add(map, "retrievalLog", "knowledge_retrieval_log", "id", "created_at",
            cols("id", "trace_id", "chat_id", "namespace", "corpus_name", "query_text", "top_k", "min_score", "status", "hit_count", "best_hit_id", "best_score", "latency_ms", "error_message", "hit_ids"),
            filters("traceId:trace_id", "chatId:chat_id", "namespace:namespace", "status:status", "queryText:query_text"),
            set("query_text"));
        return map;
    }

    private void add(Map<String, TableMeta> map, String key, String tableName, String idColumn, String orderColumn,
                     Set<String> writableColumns, Map<String, String> filterColumns, Set<String> likeColumns) {
        map.put(key, new TableMeta(tableName, idColumn, orderColumn, writableColumns, filterColumns, likeColumns));
    }

    private Set<String> cols(String... columns) {
        return new LinkedHashSet<>(Arrays.asList(columns));
    }

    private Set<String> set(String... columns) {
        return new HashSet<>(Arrays.asList(columns));
    }

    private Map<String, String> filters(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : pairs) {
            String[] arr = pair.split(":");
            map.put(arr[0], arr[1]);
        }
        return map;
    }

    public record TableMeta(String tableName, String idColumn, String orderColumn, Set<String> writableColumns,
                            Map<String, String> filterColumns, Set<String> likeColumns) {
    }

    private record QueryParts(String where, List<Object> args) {
    }

    private record Transition(String targetStatus, String operationType, boolean releaseSlot) {
    }
}

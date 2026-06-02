package com.smartclinic.medical.service;

import lombok.RequiredArgsConstructor;
import com.smartclinic.common.core.exception.ServiceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/**
 * 挂号监控、异常扫描与知识库治理服务。
 */
@Service
@RequiredArgsConstructor
public class MedicalMonitorService {

    private final JdbcTemplate jdbcTemplate;

    public Map<String, Object> dashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todayRegistrationCount", count("select count(1) from ai_registration.registration_order where clinic_date = current_date"));
        data.put("bookedCount", count("select count(1) from ai_registration.registration_order where status = 'BOOKED'"));
        data.put("rescheduledCount", count("select count(1) from ai_registration.registration_order where status = 'RESCHEDULED'"));
        data.put("cancelledCount", count("select count(1) from ai_registration.registration_order where status = 'CANCELLED'"));
        data.put("expiredCount", count("select count(1) from ai_registration.registration_order where status = 'EXPIRED'"));
        data.put("overdueActiveCount", count("""
            select count(1)
              from ai_registration.registration_order
             where status in ('PENDING_CONFIRM', 'BOOKED', 'RESCHEDULED')
               and (clinic_date < current_date or (clinic_date = current_date and start_time < current_time))
            """));
        data.put("todayOpenSlotCount", count("select count(1) from ai_registration.clinic_slot where clinic_date = current_date and status = 'OPEN'"));
        data.put("todayRemainingSlots", count("select coalesce(sum(remaining_slots), 0) from ai_registration.clinic_slot where clinic_date = current_date and status = 'OPEN'"));
        data.put("unhandledExceptionCount", count("select count(1) from ai_registration.monitor_exception_record where status in ('UNHANDLED','PROCESSING')"));
        data.put("knowledgeDocumentCount", count("select count(1) from ai_registration.knowledge_document"));
        data.put("enabledKnowledgeChunkCount", count("select count(1) from ai_registration.knowledge_chunk where enabled = true"));
        return data;
    }

    public Map<String, Object> doctorMonitor(String doctorId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("doctorId", doctorId);
        data.put("todaySlotCount", count("select count(1) from ai_registration.clinic_slot where doctor_id = ? and clinic_date = current_date", doctorId));
        data.put("todayRemainingSlots", count("select coalesce(sum(remaining_slots), 0) from ai_registration.clinic_slot where doctor_id = ? and clinic_date = current_date", doctorId));
        data.put("registrationCount", count("select count(1) from ai_registration.registration_order where doctor_id = ?", doctorId));
        data.put("cancelledCount", count("select count(1) from ai_registration.registration_order where doctor_id = ? and status = 'CANCELLED'", doctorId));
        data.put("exceptionCount", count("select count(1) from ai_registration.monitor_exception_record where doctor_id = ?", doctorId));
        data.put("recentOrders", jdbcTemplate.queryForList("""
            select registration_id, patient_id, department_code, clinic_date, start_time, status, created_at
              from ai_registration.registration_order
             where doctor_id = ?
             order by created_at desc
             limit 10
            """, doctorId));
        return data;
    }

    public Map<String, Object> patientMonitor(String patientId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("patientId", patientId);
        data.put("registrationCount", count("select count(1) from ai_registration.registration_order where patient_id = ?", patientId));
        data.put("cancelledCount", count("select count(1) from ai_registration.registration_order where patient_id = ? and status = 'CANCELLED'", patientId));
        data.put("exceptionCount", count("select count(1) from ai_registration.monitor_exception_record where patient_id = ?", patientId));
        data.put("bindingCount", count("select count(1) from ai_registration.user_patient_binding where patient_id = ? and active = true", patientId));
        data.put("recentOrders", jdbcTemplate.queryForList("""
            select registration_id, department_code, doctor_id, clinic_date, start_time, status, created_at
              from ai_registration.registration_order
             where patient_id = ?
             order by created_at desc
             limit 10
            """, patientId));
        return data;
    }

    public Map<String, Object> doctorTrace(String doctorId, Map<String, Object> params) {
        Object startDate = blankToNull(params.get("startDate"));
        Object endDate = blankToNull(params.get("endDate"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("targetType", "DOCTOR");
        data.put("doctorId", doctorId);
        data.put("orders", monitorOrders("o.doctor_id = ?", doctorId, startDate, endDate));
        data.put("exceptions", monitorExceptions("doctor_id = ?", doctorId, startDate, endDate));
        data.put("registrationAudits", monitorRegistrationAudits("o.doctor_id = ?", doctorId, startDate, endDate));
        data.put("inventoryAudits", jdbcTemplate.queryForList("""
            select audit_id, operation_type, trace_id, department_code, doctor_id, clinic_date, start_time,
                   success, reason, remaining_before, remaining_after, operation_source, created_at
              from ai_registration.clinic_slot_inventory_audit_log
             where doctor_id = ?
               and (cast(? as date) is null or clinic_date >= cast(? as date))
               and (cast(? as date) is null or clinic_date <= cast(? as date))
             order by created_at desc, audit_id desc
             limit 100
            """, doctorId, startDate, startDate, endDate, endDate));
        data.put("summary", roleTraceSummary("doctor_id = ?", doctorId, startDate, endDate));
        return data;
    }

    public Map<String, Object> patientTrace(String patientId, Map<String, Object> params) {
        Object startDate = blankToNull(params.get("startDate"));
        Object endDate = blankToNull(params.get("endDate"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("targetType", "PATIENT");
        data.put("patientId", patientId);
        data.put("orders", monitorOrders("o.patient_id = ?", patientId, startDate, endDate));
        data.put("exceptions", monitorExceptions("patient_id = ?", patientId, startDate, endDate));
        data.put("registrationAudits", monitorRegistrationAudits("o.patient_id = ?", patientId, startDate, endDate));
        data.put("bindings", jdbcTemplate.queryForList("""
            select b.binding_id, b.user_id, u.display_name, u.nickname, u.phone_masked,
                   b.relation_code, b.is_default, b.active, b.bound_at, b.updated_at
              from ai_registration.user_patient_binding b
              left join ai_registration.platform_user u on u.user_id = b.user_id
             where b.patient_id = ?
             order by b.is_default desc, b.updated_at desc, b.binding_id desc
             limit 50
            """, patientId));
        data.put("summary", roleTraceSummary("patient_id = ?", patientId, startDate, endDate));
        return data;
    }

    public List<Map<String, Object>> doctorSummaries(Map<String, Object> params) {
        Object startDate = blankToNull(params.get("startDate"));
        Object endDate = blankToNull(params.get("endDate"));
        Object departmentCode = blankToNull(params.get("departmentCode"));
        Object doctorId = blankToNull(params.get("doctorId"));
        StringBuilder sql = new StringBuilder("""
            with order_stats as (
                select doctor_id,
                       count(1) as registration_count,
                       count(1) filter (where status = 'BOOKED') as booked_count,
                       count(1) filter (where status = 'RESCHEDULED') as rescheduled_count,
                       count(1) filter (where status = 'CANCELLED') as cancelled_count,
                       count(1) filter (where status = 'EXPIRED') as expired_count,
                       count(1) filter (where status = 'COMPLETED') as completed_count,
                       count(1) filter (where status = 'NO_SHOW') as no_show_count
                  from ai_registration.registration_order
                 where (cast(? as date) is null or clinic_date >= cast(? as date))
                   and (cast(? as date) is null or clinic_date <= cast(? as date))
                 group by doctor_id
            ),
            slot_stats as (
                select doctor_id,
                       count(1) as slot_count,
                       coalesce(sum(capacity), 0) as slot_capacity,
                       coalesce(sum(remaining_slots), 0) as remaining_slots
                  from ai_registration.clinic_slot
                 where (cast(? as date) is null or clinic_date >= cast(? as date))
                   and (cast(? as date) is null or clinic_date <= cast(? as date))
                 group by doctor_id
            )
            select d.doctor_id,
                   d.doctor_name,
                   d.department_code,
                   coalesce(o.registration_count, 0) as registration_count,
                   coalesce(o.booked_count, 0) as booked_count,
                   coalesce(o.rescheduled_count, 0) as rescheduled_count,
                   coalesce(o.cancelled_count, 0) as cancelled_count,
                   coalesce(o.expired_count, 0) as expired_count,
                   coalesce(o.completed_count, 0) as completed_count,
                   coalesce(o.no_show_count, 0) as no_show_count,
                   coalesce(s.slot_count, 0) as slot_count,
                   coalesce(s.slot_capacity, 0) as slot_capacity,
                   coalesce(s.remaining_slots, 0) as remaining_slots,
                   case when coalesce(s.slot_capacity, 0) = 0 then 0
                        else round((coalesce(s.slot_capacity, 0) - coalesce(s.remaining_slots, 0))::numeric * 100 / coalesce(s.slot_capacity, 0), 2)
                   end as utilization_rate
              from ai_registration.doctor d
              left join order_stats o on o.doctor_id = d.doctor_id
              left join slot_stats s on s.doctor_id = d.doctor_id
             where 1 = 1
            """);
        List<Object> args = new ArrayList<>();
        args.add(startDate);
        args.add(startDate);
        args.add(endDate);
        args.add(endDate);
        args.add(startDate);
        args.add(startDate);
        args.add(endDate);
        args.add(endDate);
        if (departmentCode != null) {
            sql.append(" and d.department_code = ?\n");
            args.add(departmentCode);
        }
        if (doctorId != null) {
            sql.append(" and d.doctor_id = ?\n");
            args.add(doctorId);
        }
        sql.append("""
             order by registration_count desc, utilization_rate desc, d.doctor_id asc
             limit 200
            """);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> patientSummaries(Map<String, Object> params) {
        Object startDate = blankToNull(params.get("startDate"));
        Object endDate = blankToNull(params.get("endDate"));
        Object patientId = blankToNull(params.get("patientId"));
        StringBuilder sql = new StringBuilder("""
            with order_stats as (
                select patient_id,
                       count(1) as registration_count,
                       count(1) filter (where status = 'BOOKED') as booked_count,
                       count(1) filter (where status = 'RESCHEDULED') as rescheduled_count,
                       count(1) filter (where status = 'CANCELLED') as cancelled_count,
                       count(1) filter (where status = 'EXPIRED') as expired_count,
                       count(1) filter (where status = 'COMPLETED') as completed_count,
                       count(1) filter (where status = 'NO_SHOW') as no_show_count,
                       min(clinic_date) filter (where status in ('BOOKED', 'RESCHEDULED') and clinic_date >= current_date) as next_clinic_date
                  from ai_registration.registration_order
                 where (cast(? as date) is null or clinic_date >= cast(? as date))
                   and (cast(? as date) is null or clinic_date <= cast(? as date))
                 group by patient_id
            ),
            duplicate_risk as (
                select patient_id, count(1) as duplicate_risk_count
                  from (
                    select patient_id, doctor_id, department_code, clinic_date, start_time
                      from ai_registration.registration_order
                     where status in ('PENDING_CONFIRM', 'BOOKED', 'RESCHEDULED')
                       and (cast(? as date) is null or clinic_date >= cast(? as date))
                       and (cast(? as date) is null or clinic_date <= cast(? as date))
                     group by patient_id, doctor_id, department_code, clinic_date, start_time
                    having count(1) > 1
                  ) x
                 group by patient_id
            )
            select p.patient_id,
                   p.patient_name,
                   p.phone_masked,
                   p.verified_status,
                   coalesce(o.registration_count, 0) as registration_count,
                   coalesce(o.booked_count, 0) as booked_count,
                   coalesce(o.rescheduled_count, 0) as rescheduled_count,
                   coalesce(o.cancelled_count, 0) as cancelled_count,
                   coalesce(o.expired_count, 0) as expired_count,
                   coalesce(o.completed_count, 0) as completed_count,
                   coalesce(o.no_show_count, 0) as no_show_count,
                   o.next_clinic_date,
                   coalesce(r.duplicate_risk_count, 0) as duplicate_risk_count
              from ai_registration.patient_profile p
              left join order_stats o on o.patient_id = p.patient_id
              left join duplicate_risk r on r.patient_id = p.patient_id
             where 1 = 1
            """);
        List<Object> args = new ArrayList<>();
        args.add(startDate);
        args.add(startDate);
        args.add(endDate);
        args.add(endDate);
        args.add(startDate);
        args.add(startDate);
        args.add(endDate);
        args.add(endDate);
        if (patientId != null) {
            sql.append(" and p.patient_id = ?\n");
            args.add(patientId);
        }
        sql.append("""
             order by registration_count desc, no_show_count desc, cancelled_count desc, p.patient_id asc
             limit 200
            """);
        return jdbcTemplate.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> traceDetail(Map<String, Object> params) {
        Object traceId = blankToNull(params.get("traceId"));
        Object registrationId = blankToNull(params.get("registrationId"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("traceId", traceId);
        data.put("registrationId", registrationId);

        List<Map<String, Object>> registrationAudits = jdbcTemplate.queryForList("""
            select audit_id, registration_id, operation_type, operator_user_id, source_service, success,
                   reason, trace_id, created_at, request_payload, response_payload, before_snapshot, after_snapshot
              from ai_registration.registration_audit_log
             where (cast(? as text) is null or trace_id = ?)
               and (cast(? as text) is null or registration_id = ?)
             order by created_at asc, audit_id asc
             limit 200
            """, traceId, traceId, registrationId, registrationId);
        data.put("registrationAudits", registrationAudits);

        List<Map<String, Object>> inventoryAudits = jdbcTemplate.queryForList("""
            select audit_id, operation_type, trace_id, department_code, doctor_id, clinic_date, start_time,
                   success, reason, remaining_before, remaining_after, source_service, operation_id,
                   operation_source, created_at
              from ai_registration.clinic_slot_inventory_audit_log
             where (cast(? as text) is not null and trace_id = ?)
                or (cast(? as text) is not null and operation_id like '%' || cast(? as text) || '%')
             order by created_at asc, audit_id asc
             limit 200
            """, traceId, traceId, registrationId, registrationId);
        data.put("inventoryAudits", inventoryAudits);

        List<Map<String, Object>> exceptions = jdbcTemplate.queryForList("""
            select exception_id, rule_code, exception_type, severity, registration_id, user_id, patient_id,
                   doctor_id, department_code, slot_id, title, content, status, handled_by, handled_at,
                   handle_remark, detected_at, updated_at, evidence_json
              from ai_registration.monitor_exception_record
             where (cast(? as text) is null or evidence_json ->> 'traceId' = ?)
               and (cast(? as text) is null or registration_id = ?)
             order by detected_at desc, exception_id desc
             limit 200
            """, traceId, traceId, registrationId, registrationId);
        data.put("exceptions", exceptions);

        List<Map<String, Object>> retrievalLogs = jdbcTemplate.queryForList("""
            select id, trace_id, chat_id, namespace, corpus_name, query_text, top_k, min_score,
                   status, hit_count, best_hit_id, best_score, latency_ms, error_message, hit_ids, created_at
              from ai_registration.knowledge_retrieval_log
             where (cast(? as text) is not null and trace_id = ?)
             order by created_at asc, id asc
             limit 200
            """, traceId, traceId);
        data.put("retrievalLogs", retrievalLogs);
        data.put("events", buildTraceEvents(registrationAudits, inventoryAudits, exceptions, retrievalLogs));
        return data;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> scanExceptions() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("SLOT_OVERBOOKED", update("""
            insert into ai_registration.monitor_exception_record
              (rule_code, exception_type, severity, department_code, doctor_id, slot_id, biz_date, title, content, evidence_json)
            select 'SLOT_OVERBOOKED', 'SLOT', 'CRITICAL', department_code, doctor_id, slot_id, clinic_date,
                   '号源剩余数异常', 'remaining_slots 小于 0 或大于 capacity',
                   jsonb_build_object('capacity', capacity, 'remainingSlots', remaining_slots)
              from ai_registration.clinic_slot s
             where (remaining_slots < 0 or remaining_slots > capacity)
               and not exists (
                 select 1 from ai_registration.monitor_exception_record r
                  where r.rule_code = 'SLOT_OVERBOOKED' and r.slot_id = s.slot_id and r.status <> 'RESOLVED'
               )
            """));
        result.put("ORDER_WITHOUT_SLOT", update("""
            insert into ai_registration.monitor_exception_record
              (rule_code, exception_type, severity, registration_id, user_id, patient_id, doctor_id, department_code, biz_date, title, content, evidence_json)
            select 'ORDER_WITHOUT_SLOT', 'ORDER', 'ERROR', registration_id, user_id, patient_id, doctor_id, department_code, clinic_date,
                   '挂号订单缺少号源', 'registration_order.slot_id 为空',
                   jsonb_build_object('registrationId', registration_id)
              from ai_registration.registration_order o
             where slot_id is null
               and not exists (
                 select 1 from ai_registration.monitor_exception_record r
                  where r.rule_code = 'ORDER_WITHOUT_SLOT' and r.registration_id = o.registration_id and r.status <> 'RESOLVED'
               )
            """));
        result.put("ORDER_SLOT_MISMATCH", update("""
            insert into ai_registration.monitor_exception_record
              (rule_code, exception_type, severity, registration_id, user_id, patient_id, doctor_id, department_code, slot_id, biz_date, title, content, evidence_json)
            select 'ORDER_SLOT_MISMATCH', 'ORDER', 'ERROR', o.registration_id, o.user_id, o.patient_id, o.doctor_id, o.department_code, o.slot_id, o.clinic_date,
                   '订单与号源信息不一致', '订单科室/医生/日期/时间与号源不一致',
                   jsonb_build_object('order', row_to_json(o), 'slot', row_to_json(s))
              from ai_registration.registration_order o
              join ai_registration.clinic_slot s on s.slot_id = o.slot_id
             where (o.department_code <> s.department_code or o.doctor_id <> s.doctor_id or o.clinic_date <> s.clinic_date or o.start_time <> s.start_time)
               and not exists (
                 select 1 from ai_registration.monitor_exception_record r
                  where r.rule_code = 'ORDER_SLOT_MISMATCH' and r.registration_id = o.registration_id and r.status <> 'RESOLVED'
               )
            """));
        result.put("DUPLICATE_BOOKING", update("""
            insert into ai_registration.monitor_exception_record
              (rule_code, exception_type, severity, patient_id, doctor_id, department_code, biz_date, title, content, evidence_json)
            select 'DUPLICATE_BOOKING', 'ORDER', 'WARN', patient_id, doctor_id, department_code, clinic_date,
                   '同一患者同一时段重复挂号', '存在未取消的重复预约',
                   jsonb_build_object('patientId', patient_id, 'clinicDate', clinic_date, 'startTime', start_time, 'count', count(1), 'registrationIds', jsonb_agg(registration_id))
              from ai_registration.registration_order
             where status <> 'CANCELLED'
             group by patient_id, doctor_id, department_code, clinic_date, start_time
            having count(1) > 1
               and not exists (
                 select 1 from ai_registration.monitor_exception_record r
                  where r.rule_code = 'DUPLICATE_BOOKING'
                    and r.patient_id = registration_order.patient_id
                    and r.doctor_id = registration_order.doctor_id
                    and r.department_code = registration_order.department_code
                    and r.biz_date = registration_order.clinic_date
                    and r.evidence_json ->> 'startTime' = registration_order.start_time::text
                    and r.status <> 'RESOLVED'
               )
            """));
        result.put("REGISTRATION_AUDIT_MISSING", update("""
            insert into ai_registration.monitor_exception_record
              (rule_code, exception_type, severity, registration_id, user_id, patient_id, doctor_id, department_code, biz_date, title, content, evidence_json)
            select 'REGISTRATION_AUDIT_MISSING', 'AUDIT', 'WARN', o.registration_id, o.user_id, o.patient_id, o.doctor_id, o.department_code, o.clinic_date,
                   '挂号订单缺少审计日志', 'registration_order 没有关联 registration_audit_log',
                   jsonb_build_object('registrationId', o.registration_id)
              from ai_registration.registration_order o
             where not exists (select 1 from ai_registration.registration_audit_log a where a.registration_id = o.registration_id)
               and not exists (
                 select 1 from ai_registration.monitor_exception_record r
                  where r.rule_code = 'REGISTRATION_AUDIT_MISSING' and r.registration_id = o.registration_id and r.status <> 'RESOLVED'
               )
            """));
        result.put("DOCTOR_DISABLED_WITH_SLOT", update("""
            insert into ai_registration.monitor_exception_record
              (rule_code, exception_type, severity, doctor_id, department_code, slot_id, biz_date, title, content, evidence_json)
            select 'DOCTOR_DISABLED_WITH_SLOT', 'DOCTOR', 'WARN', s.doctor_id, s.department_code, s.slot_id, s.clinic_date,
                   '停用医生仍有开放号源', 'doctor.active=false 或 online_enabled=false 但存在 OPEN 号源',
                   jsonb_build_object('doctorId', s.doctor_id, 'slotId', s.slot_id)
              from ai_registration.clinic_slot s
              join ai_registration.doctor d on d.doctor_id = s.doctor_id
             where s.status = 'OPEN' and (d.active = false or d.online_enabled = false)
               and not exists (
                 select 1 from ai_registration.monitor_exception_record r
                  where r.rule_code = 'DOCTOR_DISABLED_WITH_SLOT' and r.slot_id = s.slot_id and r.status <> 'RESOLVED'
               )
            """));
        result.put("DEPARTMENT_DISABLED_WITH_SLOT", update("""
            insert into ai_registration.monitor_exception_record
              (rule_code, exception_type, severity, department_code, slot_id, biz_date, title, content, evidence_json)
            select 'DEPARTMENT_DISABLED_WITH_SLOT', 'DEPARTMENT', 'WARN', s.department_code, s.slot_id, s.clinic_date,
                   '停用科室仍有开放号源', 'department.active=false 或 online_enabled=false 但存在 OPEN 号源',
                   jsonb_build_object('departmentCode', s.department_code, 'slotId', s.slot_id)
              from ai_registration.clinic_slot s
              join ai_registration.department d on d.department_code = s.department_code
             where s.status = 'OPEN' and (d.active = false or d.online_enabled = false)
               and not exists (
                 select 1 from ai_registration.monitor_exception_record r
                  where r.rule_code = 'DEPARTMENT_DISABLED_WITH_SLOT' and r.slot_id = s.slot_id and r.status <> 'RESOLVED'
               )
            """));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public int handleException(Long exceptionId, String status, String remark, String handleUserId, String handleUserName) {
        String targetStatus = normalizeExceptionStatus(status);
        String oldStatus = jdbcTemplate.queryForObject(
            "select status from ai_registration.monitor_exception_record where exception_id = ?",
            String.class,
            exceptionId
        );
        if (oldStatus == null) {
            throw new ServiceException("异常记录不存在");
        }
        int rows = jdbcTemplate.update("""
            update ai_registration.monitor_exception_record
               set status = ?, handled_by = ?, handled_at = now(), handle_remark = ?, updated_at = now()
             where exception_id = ?
            """, targetStatus, handleUserId, remark, exceptionId);
        jdbcTemplate.update("""
            insert into ai_registration.monitor_exception_handle_log
              (exception_id, old_status, new_status, handle_user_id, handle_user_name, remark)
            values (?, ?, ?, ?, ?, ?)
            """, exceptionId, oldStatus, targetStatus, handleUserId, handleUserName, remark);
        return rows;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> checkKnowledgeQuality() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("EMPTY_CONTENT", update("""
            insert into ai_registration.knowledge_chunk_quality_check
              (chunk_id, namespace, chunk_table, issue_type, severity, issue_detail)
            select id, namespace, 'knowledge_chunk', 'EMPTY_CONTENT', 'ERROR', 'content 为空'
              from ai_registration.knowledge_chunk c
             where trim(content) = ''
               and not exists (
                 select 1 from ai_registration.knowledge_chunk_quality_check q
                  where q.chunk_id = c.id and q.issue_type = 'EMPTY_CONTENT' and q.status = 'OPEN'
               )
            """));
        result.put("MISSING_METADATA", update("""
            insert into ai_registration.knowledge_chunk_quality_check
              (chunk_id, namespace, chunk_table, issue_type, severity, issue_detail)
            select id, namespace, 'knowledge_chunk', 'MISSING_METADATA', 'WARN', 'metadata 为空对象'
              from ai_registration.knowledge_chunk c
             where metadata = '{}'::jsonb
               and not exists (
                 select 1 from ai_registration.knowledge_chunk_quality_check q
                  where q.chunk_id = c.id and q.issue_type = 'MISSING_METADATA' and q.status = 'OPEN'
               )
            """));
        result.put("MISSING_EMBEDDING_MODEL", update("""
            insert into ai_registration.knowledge_chunk_quality_check
              (chunk_id, namespace, chunk_table, issue_type, severity, issue_detail)
            select id, namespace, 'knowledge_chunk', 'MISSING_EMBEDDING', 'ERROR', 'embedding_model 或 embedding_dimensions 缺失'
              from ai_registration.knowledge_chunk c
             where embedding_model is null or embedding_dimensions is null
               and not exists (
                 select 1 from ai_registration.knowledge_chunk_quality_check q
                  where q.chunk_id = c.id and q.issue_type = 'MISSING_EMBEDDING' and q.status = 'OPEN'
               )
            """));
        return result;
    }

    private List<Map<String, Object>> monitorOrders(String roleCondition, Object roleId, Object startDate, Object endDate) {
        return jdbcTemplate.queryForList("""
            select o.registration_id, o.user_id, u.display_name, u.nickname,
                   o.patient_id, p.patient_name, p.phone_masked,
                   o.department_code, d.department_name,
                   o.doctor_id, doc.doctor_name,
                   o.slot_id, o.clinic_date, o.start_time, o.status,
                   o.source_channel, o.created_at, o.updated_at
              from ai_registration.registration_order o
              left join ai_registration.platform_user u on u.user_id = o.user_id
              left join ai_registration.patient_profile p on p.patient_id = o.patient_id
              left join ai_registration.department d on d.department_code = o.department_code
              left join ai_registration.doctor doc on doc.doctor_id = o.doctor_id
             where %s
               and (cast(? as date) is null or o.clinic_date >= cast(? as date))
               and (cast(? as date) is null or o.clinic_date <= cast(? as date))
             order by o.clinic_date desc, o.start_time desc, o.created_at desc
             limit 100
            """.formatted(roleCondition), roleId, startDate, startDate, endDate, endDate);
    }

    private List<Map<String, Object>> monitorExceptions(String roleCondition, Object roleId, Object startDate, Object endDate) {
        return jdbcTemplate.queryForList("""
            select exception_id, rule_code, exception_type, severity, registration_id, user_id, patient_id,
                   doctor_id, department_code, slot_id, title, content, status, handled_by, handled_at,
                   handle_remark, detected_at, updated_at, evidence_json
              from ai_registration.monitor_exception_record
             where %s
               and (cast(? as date) is null or biz_date >= cast(? as date))
               and (cast(? as date) is null or biz_date <= cast(? as date))
             order by detected_at desc, exception_id desc
             limit 100
            """.formatted(roleCondition), roleId, startDate, startDate, endDate, endDate);
    }

    private List<Map<String, Object>> monitorRegistrationAudits(String roleCondition, Object roleId, Object startDate, Object endDate) {
        return jdbcTemplate.queryForList("""
            select a.audit_id, a.registration_id, a.operation_type, a.operator_user_id, a.source_service,
                   a.success, a.reason, a.trace_id, a.created_at
              from ai_registration.registration_audit_log a
              join ai_registration.registration_order o on o.registration_id = a.registration_id
             where %s
               and (cast(? as date) is null or o.clinic_date >= cast(? as date))
               and (cast(? as date) is null or o.clinic_date <= cast(? as date))
             order by a.created_at desc, a.audit_id desc
             limit 100
            """.formatted(roleCondition), roleId, startDate, startDate, endDate, endDate);
    }

    private Map<String, Object> roleTraceSummary(String roleCondition, Object roleId, Object startDate, Object endDate) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            select count(1) as order_count,
                   count(1) filter (where status in ('PENDING_CONFIRM', 'BOOKED', 'RESCHEDULED')) as active_order_count,
                   count(1) filter (where status = 'CANCELLED') as cancelled_count,
                   count(1) filter (where status = 'COMPLETED') as completed_count,
                   count(1) filter (where status = 'EXPIRED') as expired_count,
                   count(1) filter (where status = 'NO_SHOW') as no_show_count
              from ai_registration.registration_order
             where %s
               and (cast(? as date) is null or clinic_date >= cast(? as date))
               and (cast(? as date) is null or clinic_date <= cast(? as date))
            """.formatted(roleCondition), roleId, startDate, startDate, endDate, endDate);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private Long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private int update(String sql) {
        return jdbcTemplate.update(sql);
    }

    private String normalizeExceptionStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("UNHANDLED", "PROCESSING", "CONFIRMED", "IGNORED", "RESOLVED").contains(normalized)) {
            throw new ServiceException("不支持的异常处理状态: " + status);
        }
        return normalized;
    }

    private Object blankToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : value;
    }

    private List<Map<String, Object>> buildTraceEvents(List<Map<String, Object>> registrationAudits,
                                                       List<Map<String, Object>> inventoryAudits,
                                                       List<Map<String, Object>> exceptions,
                                                       List<Map<String, Object>> retrievalLogs) {
        List<Map<String, Object>> events = new java.util.ArrayList<>();
        registrationAudits.forEach(row -> addTraceEvent(events, "REGISTRATION", row.get("operation_type"), row.get("created_at"), row));
        inventoryAudits.forEach(row -> addTraceEvent(events, "INVENTORY", row.get("operation_type"), row.get("created_at"), row));
        exceptions.forEach(row -> addTraceEvent(events, "EXCEPTION", row.get("rule_code"), row.get("detected_at"), row));
        retrievalLogs.forEach(row -> addTraceEvent(events, "RAG", row.get("status"), row.get("created_at"), row));
        events.sort(java.util.Comparator.comparing(event -> String.valueOf(event.getOrDefault("eventTime", ""))));
        return events;
    }

    private void addTraceEvent(List<Map<String, Object>> events, String type, Object title, Object eventTime, Map<String, Object> payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("title", title);
        event.put("eventTime", eventTime);
        event.put("payload", payload);
        events.add(event);
    }
}

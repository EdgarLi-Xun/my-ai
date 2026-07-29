package cn.edgarli.web;

import cn.edgarli.common.BizException;
import cn.edgarli.entity.AiCallLog;
import cn.edgarli.entity.AuditLog;
import cn.edgarli.mapper.AiCallLogMapper;
import cn.edgarli.mapper.AuditLogMapper;
import cn.edgarli.web.converter.AiCallLogConverter;
import cn.edgarli.web.converter.AuditLogConverter;
import cn.edgarli.web.vo.AiCallLogVo;
import cn.edgarli.web.vo.AuditLogVo;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日志查询 API（ADR 0004 §12）。
 * Log query API (ADR 0004 §12).
 * <p>
 * 仅 admin 可访问（{@link cn.edgarli.infrastructure.security.SecurityConfig} 已配
 * {@code /api/logs/**} hasRole ADMIN）。
 * Admin-only access — {@code /api/logs/**} is restricted to role ADMIN by
 * the security configuration; non-admins receive HTTP 403.
 */
@RestController
@RequestMapping("/api/logs")
public class LogsController {

    /** ADR §已知风险 #6：size 上限 200 防爆。 */
    private static final int MAX_PAGE_SIZE = 200;

    private final AiCallLogMapper aiCallLogMapper;
    private final AuditLogMapper auditLogMapper;

    public LogsController(AiCallLogMapper aiCallLogMapper, AuditLogMapper auditLogMapper) {
        this.aiCallLogMapper = aiCallLogMapper;
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * 分页查询 AI 调用日志（按 created_at 倒序）。
     * List AI call log entries (newest first).
     * <p>
     * size 会被收敛到 {@code [1, MAX_PAGE_SIZE]}，page 不能为负；
     * from / to 为可选时间区间（左闭右开）。
     *
     * @param from 起始时间（含）/ lower bound, inclusive
     * @param to 截止时间（不含）/ upper bound, exclusive
     * @param page 页码（从 0 起）/ page index, 0-based
     * @param size 每页条数（1..200）/ page size, clamped to [1,200]
     * @return AI 调用日志 VO 列表 / list of AI call log VOs
     */
    @GetMapping("/ai-calls")
    public List<AiCallLogVo> listAiCalls(
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        QueryWrapper q = QueryWrapper.create()
                .where("1=1")
                .orderBy("created_at", false);
        if (from != null) {
            q.and("created_at >= {0}", from);
        }
        if (to != null) {
            q.and("created_at < {0}", to);
        }
        q.limit(safePage * safeSize, safeSize);
        return aiCallLogMapper.selectListByQuery(q).stream()
                .map(AiCallLogConverter::toResponse)
                .toList();
    }

    /**
     * 按 ID 取单条 AI 调用日志。
     * Fetch a single AI call log by id.
     *
     * @param id 日志主键 / log id
     * @return 日志 VO / log VO
     * @throws BizException 不存在时抛 notFound / thrown when the row is missing
     */
    @GetMapping("/ai-calls/{id}")
    public AiCallLogVo getAiCall(@PathVariable long id) {
        AiCallLog row = aiCallLogMapper.selectOneById(id);
        if (row == null) {
            throw BizException.notFound("AI 调用日志不存在");
        }
        return AiCallLogConverter.toResponse(row);
    }

    /**
     * 分页查询审计日志（仅未软删，按 created_at 倒序）。
     * List audit log entries (soft-deleted excluded, newest first).
     *
     * @param from 起始时间（含）/ lower bound, inclusive
     * @param to 截止时间（不含）/ upper bound, exclusive
     * @param page 页码（从 0 起）/ page index, 0-based
     * @param size 每页条数（1..200）/ page size, clamped to [1,200]
     * @return 审计日志 VO 列表 / list of audit log VOs
     */
    @GetMapping("/audit")
    public List<AuditLogVo> listAuditLogs(
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        QueryWrapper q = QueryWrapper.create()
                .where("deleted_at IS NULL")
                .orderBy("created_at", false);
        if (from != null) {
            q.and("created_at >= {0}", from);
        }
        if (to != null) {
            q.and("created_at < {0}", to);
        }
        q.limit(safePage * safeSize, safeSize);
        return auditLogMapper.selectListByQuery(q).stream()
                .map(AuditLogConverter::toResponse)
                .toList();
    }

    /**
     * 按 ID 取单条审计日志。
     * Fetch a single audit log by id.
     *
     * @param id 日志主键 / log id
     * @return 审计日志 VO / audit log VO
     * @throws BizException 不存在时抛 notFound / thrown when the row is missing
     */
    @GetMapping("/audit/{id}")
    public AuditLogVo getAuditLog(@PathVariable long id) {
        AuditLog row = auditLogMapper.selectOneById(id);
        if (row == null) {
            throw BizException.notFound("审计日志不存在");
        }
        return AuditLogConverter.toResponse(row);
    }
}
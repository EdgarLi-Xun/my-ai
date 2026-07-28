package cn.edgarli.web;

import cn.edgarli.common.BizException;
import cn.edgarli.entity.AiCallLog;
import cn.edgarli.entity.AuditLog;
import cn.edgarli.mapper.AiCallLogMapper;
import cn.edgarli.mapper.AuditLogMapper;
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
 * <p>
 * 仅 admin 可访问（{@link cn.edgarli.security.SecurityConfig} 已配
 * {@code /api/logs/**} hasRole ADMIN）。
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

    @GetMapping("/ai-calls")
    public List<AiCallLog> listAiCalls(
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
        return aiCallLogMapper.selectListByQuery(q);
    }

    @GetMapping("/ai-calls/{id}")
    public AiCallLog getAiCall(@PathVariable long id) {
        AiCallLog row = aiCallLogMapper.selectOneById(id);
        if (row == null) {
            throw BizException.notFound("AI 调用日志不存在");
        }
        return row;
    }

    @GetMapping("/audit")
    public List<AuditLog> listAuditLogs(
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
        return auditLogMapper.selectListByQuery(q);
    }

    @GetMapping("/audit/{id}")
    public AuditLog getAuditLog(@PathVariable long id) {
        AuditLog row = auditLogMapper.selectOneById(id);
        if (row == null) {
            throw BizException.notFound("审计日志不存在");
        }
        return row;
    }
}
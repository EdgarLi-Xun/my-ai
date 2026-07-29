package cn.edgarli.web.converter;

import cn.edgarli.entity.AuditLog;
import cn.edgarli.web.vo.AuditLogVo;

/**
 * AuditLog DO → AuditLogVo 转换（ADR 0005 §6）。
 * AuditLog DO → AuditLogVo converter (ADR 0005 §6).
 * <p>
 * 纯手动映射，避免引入 MapStruct 依赖；所有字段从 DO 直接拷贝。
 * Pure manual mapping (no MapStruct); every field is copied directly from the DO.
 *
 * @author MyAi
 */
public final class AuditLogConverter {

    private AuditLogConverter() {
    }

    /**
     * 将审计日志实体转为响应 VO。
     * Convert an audit log entity into its response VO.
     * <p>
     * 入参为 null 时直接返回 null，便于在流式管道中安全链接。
     * Returns {@code null} when the input is {@code null} so callers can chain safely.
     *
     * @param row 审计日志实体 / audit log entity (may be null)
     * @return 响应 VO / response VO (null when input is null)
     */
    public static AuditLogVo toResponse(AuditLog row) {
        if (row == null) {
            return null;
        }
        return new AuditLogVo(
                row.getId(),
                row.getUserId(),
                row.getAction(),
                row.getTargetType(),
                row.getTargetId(),
                row.getIpAddress(),
                row.getUserAgent(),
                row.getCreatedAt(),
                row.getDeletedAt());
    }
}
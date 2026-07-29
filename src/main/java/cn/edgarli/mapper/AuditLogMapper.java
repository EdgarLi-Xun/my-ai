package cn.edgarli.mapper;

import cn.edgarli.entity.AuditLog;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Audit log data access (MyBatis-Flex mapper, ADR 0004 §4).
 * 审计日志数据访问（ADR 0004 §4）。
 * <p>
 * 由 {@code AuditAspect @Around @Auditable} 在 service 方法 proceed 成功后
 * 写入；保留期清理走软删 + 物理删两阶段。仅 admin 可通过
 * {@code /api/logs/**} 查询。
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
package cn.edgarli.mapper;

import cn.edgarli.entity.AuditLog;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计日志数据访问（ADR 0004 §4）。
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
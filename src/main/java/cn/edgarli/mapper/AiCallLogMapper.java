package cn.edgarli.mapper;

import cn.edgarli.entity.AiCallLog;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 调用日志数据访问（ADR 0004 §3）。
 */
@Mapper
public interface AiCallLogMapper extends BaseMapper<AiCallLog> {
}
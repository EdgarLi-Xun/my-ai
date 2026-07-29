package cn.edgarli.mapper;

import cn.edgarli.entity.AiCallLog;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI call log data access (MyBatis-Flex mapper, ADR 0004 §3).
 * AI 调用日志数据访问（ADR 0004 §3）。
 * <p>
 * 由 {@code MessageCommandService.streamReply / regenerate} 在 onComplete /
 * onError 时写入；保留期清理任务（{@code LogCleanupTask}）按 created_at
 * 物理删除。tokens 允许为 NULL（Ollama 等不返回 usage）。
 */
@Mapper
public interface AiCallLogMapper extends BaseMapper<AiCallLog> {
}
package cn.edgarli.mapper;

import cn.edgarli.entity.UserApiKey;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * User API key data access interface (MyBatis-Flex mapper).
 * 用户 API Key 配置的数据访问接口。
 * <p>
 * 限定 {@code user_id} 维度的 Key 查询与删除，避免跨用户越权；
 * 默认 Key 规则与协议回退逻辑由 {@code UserApiKeyService} 维护，
 * mapper 只负责把查询条件翻译成 SQL。
 */
@Mapper
public interface UserApiKeyMapper extends BaseMapper<UserApiKey> {

    /**
     * 列出某用户全部 Key（按 id 升序）。
     * List all API keys belonging to a user (ordered by id ascending).
     *
     * @param userId 用户主键 / user primary key
     * @return 该用户的 Key 列表 / list of keys for the user
     */
    default List<UserApiKey> findAllByUserId(Long userId) {
        QueryWrapper query = QueryWrapper.create()
                .where(UserApiKey::getUserId).eq(userId)
                .orderBy(UserApiKey::getId, true);
        return selectListByQuery(query);
    }

    /**
     * 按主键 + 用户 id 查找 Key（防止跨用户读）。
     * Find a key by id and owning user id (prevents cross-user reads).
     *
     * @param id     Key 主键 / key primary key
     * @param userId 所属用户 id / owning user id
     * @return Key 实体或 null / key entity or null
     */
    default UserApiKey findByIdAndUserId(Long id, Long userId) {
        QueryWrapper query = QueryWrapper.create()
                .where(UserApiKey::getId).eq(id)
                .and(UserApiKey::getUserId).eq(userId);
        return selectOneByQuery(query);
    }

    /**
     * 按主键 + 用户 id 删除 Key（防止跨用户删）。
     * Delete a key by id and owning user id (prevents cross-user deletes).
     *
     * @param id     Key 主键 / key primary key
     * @param userId 所属用户 id / owning user id
     * @return 受影响行数 / affected rows
     */
    default int deleteByIdAndUserId(Long id, Long userId) {
        QueryWrapper query = QueryWrapper.create()
                .where(UserApiKey::getId).eq(id)
                .and(UserApiKey::getUserId).eq(userId);
        return deleteByQuery(query);
    }
}

package cn.edgarli.mapper;

import cn.edgarli.entity.User;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * User data access interface (MyBatis-Flex mapper).
 * 用户数据访问接口。
 * <p>
 * 提供用户实体的基础 CRUD 与少量业务查询（按 id / email 查找、修改默认 Key、
 * 修改密码哈希），其余通用方法继承自 {@link BaseMapper}。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 全量查询用户（按 id 升序）。
     * Find all users ordered by id ascending.
     *
     * @return 用户列表 / list of users
     */
    default List<User> findAll() {
        return selectListByQuery(QueryWrapper.create().orderBy(User::getId, true));
    }

    /**
     * 按主键 id 查找用户。
     * Find a user by primary key id.
     *
     * @param id 用户主键 / user primary key
     * @return 用户实体或 null / user entity or null
     */
    default User findById(Long id) {
        return selectOneById(id);
    }

    /**
     * 更新用户的默认 Key 引用。
     * Update the user's default API key reference.
     *
     * @param id    用户主键 / user primary key
     * @param keyId 默认 Key 主键（可为 null 表示清除）/ default key id (null to clear)
     * @return 受影响行数（1 成功 / 0 未命中）/ affected rows (1 success / 0 not matched)
     */
    default int updateDefaultKey(Long id, Long keyId) {
        return UpdateChain.of(this)
                .set(User::getDefaultKeyId, keyId)
                .where(User::getId).eq(id)
                .update() ? 1 : 0;
    }

    /**
     * 按邮箱查找用户（登录、注册查重使用）。
     * Find a user by email (used by login and registration uniqueness check).
     *
     * @param email 邮箱 / email
     * @return 用户实体或 null / user entity or null
     */
    default User findByEmail(String email) {
        return selectOneByQuery(
                QueryWrapper.create().where(User::getEmail).eq(email));
    }

    /**
     * 取用户密码哈希（登录校验、改密流程使用）。
     * Fetch the user's password hash (used by login verification and password change).
     *
     * @param id 用户主键 / user primary key
     * @return 密码哈希或 null / password hash or null
     */
    default String findPasswordHashById(Long id) {
        User user = selectOneById(id);
        return user == null ? null : user.getPasswordHash();
    }

    /**
     * 更新用户密码哈希。
     * Update the user's password hash.
     *
     * @param id   用户主键 / user primary key
     * @param hash 新的密码哈希 / new password hash
     * @return 受影响行数（1 成功 / 0 未命中）/ affected rows (1 success / 0 not matched)
     */
    default int updatePasswordHash(Long id, String hash) {
        return UpdateChain.of(this)
                .set(User::getPasswordHash, hash)
                .where(User::getId).eq(id)
                .update() ? 1 : 0;
    }
}

package cn.edgarli.mapper;

import cn.edgarli.entity.User;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 用户数据访问接口。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    default List<User> findAll() {
        return selectListByQuery(QueryWrapper.create().orderBy(User::getId, true));
    }

    default User findById(Long id) {
        return selectOneById(id);
    }

    default int updateDefaultKey(Long id, Long keyId) {
        return UpdateChain.of(this)
                .set(User::getDefaultKeyId, keyId)
                .where(User::getId).eq(id)
                .update() ? 1 : 0;
    }

    default User findByEmail(String email) {
        return selectOneByQuery(
                QueryWrapper.create().where(User::getEmail).eq(email));
    }

    default String findPasswordHashById(Long id) {
        User user = selectOneById(id);
        return user == null ? null : user.getPasswordHash();
    }

    default int updatePasswordHash(Long id, String hash) {
        return UpdateChain.of(this)
                .set(User::getPasswordHash, hash)
                .where(User::getId).eq(id)
                .update() ? 1 : 0;
    }
}

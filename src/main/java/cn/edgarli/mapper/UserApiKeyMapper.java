package cn.edgarli.mapper;

import cn.edgarli.entity.UserApiKey;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 用户 API Key 配置的数据访问接口。
 */
@Mapper
public interface UserApiKeyMapper extends BaseMapper<UserApiKey> {

    default List<UserApiKey> findAllByUserId(Long userId) {
        QueryWrapper query = QueryWrapper.create()
                .where(UserApiKey::getUserId).eq(userId)
                .orderBy(UserApiKey::getId, true);
        return selectListByQuery(query);
    }

    default UserApiKey findByIdAndUserId(Long id, Long userId) {
        QueryWrapper query = QueryWrapper.create()
                .where(UserApiKey::getId).eq(id)
                .and(UserApiKey::getUserId).eq(userId);
        return selectOneByQuery(query);
    }

    default int deleteByIdAndUserId(Long id, Long userId) {
        QueryWrapper query = QueryWrapper.create()
                .where(UserApiKey::getId).eq(id)
                .and(UserApiKey::getUserId).eq(userId);
        return deleteByQuery(query);
    }
}

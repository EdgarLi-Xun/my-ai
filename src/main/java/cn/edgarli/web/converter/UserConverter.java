package cn.edgarli.web.converter;

import cn.edgarli.entity.User;
import cn.edgarli.web.vo.UserVo;

/**
 * User DO → UserVo 转换（ADR 0005 §6：DO/VO 分离）。
 * User DO → UserVo converter (ADR 0005 §6: DO/VO separation).
 * <p>
 * 手动转换，不引 MapStruct。
 * Pure manual mapping; no MapStruct dependency.
 *
 * @author MyAi
 */
public final class UserConverter {

    private UserConverter() {
    }

    /**
     * 将持久化用户实体转为响应 DTO，剥离 {@code passwordHash} 等敏感字段。
     * Convert a persisted user entity into the response DTO, stripping sensitive
     * fields such as {@code passwordHash}.
     *
     * @param user 持久化用户 / persisted user (may be null)
     * @return 响应 DTO / response DTO (null when input is null)
     */
    public static UserVo toResponse(User user) {
        if (user == null) {
            return null;
        }
        return new UserVo(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getDefaultKeyId(),
                user.getCreateTime());
    }
}
package cn.edgarli.service;

import cn.edgarli.entity.User;

/**
 * User management service (CRUD).
 * 用户管理服务（CRUD）。
 * <p>
 * Extracts the pure data-access logic from {@code UserController}, leaving only authentication concerns.
 * 抽出 {@code UserController} 中除鉴权外的纯数据访问逻辑。
 * Cross-user access authorization (owner checks) is still handled by the controller.
 * 跨用户访问的鉴权（owner 校验）仍由 controller 处理。
 *
 * @author MyAi
 */
public interface UserService {

    /**
     * Get current logged-in user.
     * 取当前登录用户。
     *
     * @return current logged-in user / 当前登录用户
     * @throws cn.edgarli.common.BizException 4010 not logged in; 4040 user not found
     *         4010 未登录；4040 用户不存在
     */
    User getCurrentUser();

    /**
     * Look up user by id.
     * 按 id 查询用户。
     *
     * @param id user id / 用户 ID
     * @return user entity / 用户实体
     * @throws cn.edgarli.common.BizException 4040 user not found / 4040 用户不存在
     */
    User getById(Long id);

    /**
     * Create a new user (legacy; prefer {@code /api/auth/register}).
     * 创建新用户（保留接口；推荐用 {@code /api/auth/register}）。
     *
     * @param name user name (required, non-empty after trim) / 用户名（必填，trim 后非空）
     * @param email email (optional) / 邮箱（可空）
     * @return newly created user / 新创建的用户
     * @throws cn.edgarli.common.BizException 4000 name is empty / 4000 用户名为空
     */
    User create(String name, String email);

    /**
     * Delete a user (cascades all keys, conversations, messages, ai_call_log rows).
     * 删除用户（级联删除其全部 Key、conversation、message、ai_call_log）。
     *
     * @param id user id / 用户 ID
     * @throws cn.edgarli.common.BizException 4040 user not found / 4040 用户不存在
     */
    void delete(Long id);
}
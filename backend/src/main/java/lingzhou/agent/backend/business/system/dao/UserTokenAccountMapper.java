package lingzhou.agent.backend.business.system.dao;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lingzhou.agent.backend.business.system.model.UserTokenAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserTokenAccountMapper extends BaseMapper<UserTokenAccount> {

    default UserTokenAccount selectByUserId(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        QueryWrapper<UserTokenAccount> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).last("limit 1");
        return this.selectOne(wrapper);
    }

    default int deleteByUserId(Long userId) {
        if (userId == null || userId <= 0) {
            return 0;
        }
        QueryWrapper<UserTokenAccount> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        return this.delete(wrapper);
    }

    @Update("UPDATE user_token_account "
            + "SET consumed_tokens = COALESCE(consumed_tokens, 0) + #{tokens}, "
            + "remaining_tokens = GREATEST(COALESCE(granted_tokens, 0) - (COALESCE(consumed_tokens, 0) + #{tokens}), 0), "
            + "updated_at = NOW() "
            + "WHERE user_id = #{userId}")
    int incrementConsumedTokens(@Param("userId") Long userId, @Param("tokens") Long tokens);

    @Update("UPDATE user_token_account "
            + "SET granted_tokens = COALESCE(granted_tokens, 0) + #{tokens}, "
            + "remaining_tokens = COALESCE(remaining_tokens, 0) + #{tokens}, "
            + "updated_at = NOW() "
            + "WHERE user_id = #{userId}")
    int incrementGrantedTokens(@Param("userId") Long userId, @Param("tokens") Long tokens);

    @Update("UPDATE user_token_account "
            + "SET granted_tokens = COALESCE(consumed_tokens, 0) + #{remainingTokens}, "
            + "remaining_tokens = #{remainingTokens}, "
            + "is_unlimited = #{unlimited}, "
            + "updated_at = NOW() "
            + "WHERE user_id = #{userId}")
    int updateQuotaSettings(
            @Param("userId") Long userId,
            @Param("remainingTokens") Long remainingTokens,
            @Param("unlimited") Integer unlimited);
}

package lingzhou.agent.backend.business.system.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import lingzhou.agent.backend.business.system.model.SysUserModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUserModel> {

    SysUserModel selectByCode(@Param("code") String code);

    List<SysUserModel> selectByMobile(@Param("mobile") String mobile);
}

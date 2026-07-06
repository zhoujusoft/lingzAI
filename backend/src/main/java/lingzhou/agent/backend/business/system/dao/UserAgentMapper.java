package lingzhou.agent.backend.business.system.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lingzhou.agent.backend.business.system.model.UserAgent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserAgentMapper extends BaseMapper<UserAgent> {}

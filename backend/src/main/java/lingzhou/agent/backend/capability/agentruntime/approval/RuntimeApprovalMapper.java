package lingzhou.agent.backend.capability.agentruntime.approval;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RuntimeApprovalMapper extends BaseMapper<RuntimeApproval> {

    default RuntimeApproval selectByApprovalCode(String approvalCode) {
        if (approvalCode == null || approvalCode.isBlank()) {
            return null;
        }
        QueryWrapper<RuntimeApproval> wrapper = new QueryWrapper<>();
        wrapper.eq("approval_code", approvalCode.trim()).last("limit 1");
        return this.selectOne(wrapper);
    }

    default List<RuntimeApproval> selectPendingByRunCode(String runCode) {
        if (runCode == null || runCode.isBlank()) {
            return List.of();
        }
        QueryWrapper<RuntimeApproval> wrapper = new QueryWrapper<>();
        wrapper.eq("run_code", runCode.trim())
                .eq("approval_status", RuntimeApprovalConstants.APPROVAL_PENDING)
                .orderByDesc("id");
        return this.selectList(wrapper);
    }
}

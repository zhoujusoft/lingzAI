package lingzhou.agent.backend.business.monitor.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Arrays;
import java.util.List;
import lingzhou.agent.backend.business.monitor.domain.SysJob;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.annotations.Mapper;

/**
 * 调度任务信息 数据层
 *
 * @author xiehb
 */
@Mapper
public interface SysJobMapper extends BaseMapper<SysJob> {
    /**
     * 查询调度任务日志集合
     *
     * @param job 调度信息
     * @return 操作日志集合
     */
    default List<SysJob> selectJobList(SysJob job) {
        QueryWrapper<SysJob> wrapper = new QueryWrapper<>();
        if (job != null) {
            if (StringUtils.isNotBlank(job.getJobName())) {
                wrapper.like("job_name", job.getJobName());
            }
            if (StringUtils.isNotBlank(job.getJobGroup())) {
                wrapper.eq("job_group", job.getJobGroup());
            }
            if (StringUtils.isNotBlank(job.getStatus())) {
                wrapper.eq("status", job.getStatus());
            }
            if (StringUtils.isNotBlank(job.getInvokeTarget())) {
                wrapper.like("invoke_target", job.getInvokeTarget());
            }
        }
        wrapper.orderByDesc("job_id");
        return this.selectList(wrapper);
    }

    /**
     * 查询所有调度任务
     *
     * @return 调度任务列表
     */
    default List<SysJob> selectJobAll() {
        QueryWrapper<SysJob> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("job_id");
        return this.selectList(wrapper);
    }

    /**
     * 通过调度ID查询调度任务信息
     *
     * @param jobId 调度ID
     * @return 角色对象信息
     */
    default SysJob selectJobById(Long jobId) {
        return this.selectById(jobId);
    }

    /**
     * 通过调度ID删除调度任务信息
     *
     * @param jobId 调度ID
     * @return 结果
     */
    default int deleteJobById(Long jobId) {
        return this.deleteById(jobId);
    }

    /**
     * 批量删除调度任务信息
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    default int deleteJobByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return 0;
        }
        return this.deleteBatchIds(Arrays.asList(ids));
    }

    /**
     * 修改调度任务信息
     *
     * @param job 调度任务信息
     * @return 结果
     */
    default int updateJob(SysJob job) {
        return this.updateById(job);
    }

    /**
     * 新增调度任务信息
     *
     * @param job 调度任务信息
     * @return 结果
     */
    default int insertJob(SysJob job) {
        return this.insert(job);
    }
}

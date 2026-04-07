package lingzhou.agent.backend.business.monitor.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.monitor.domain.SysJobLog;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.annotations.Mapper;

/**
 * 调度任务日志信息 数据层
 *
 * @author xiehb
 */
@Mapper
public interface SysJobLogMapper extends BaseMapper<SysJobLog> {
    /**
     * 获取quartz调度器日志的计划任务
     *
     * @param jobLog 调度日志信息
     * @return 调度任务日志集合
     */
    default List<SysJobLog> selectJobLogList(SysJobLog jobLog) {
        QueryWrapper<SysJobLog> wrapper = new QueryWrapper<>();
        if (jobLog != null) {
            if (StringUtils.isNotBlank(jobLog.getJobName())) {
                wrapper.like("job_name", jobLog.getJobName());
            }
            if (StringUtils.isNotBlank(jobLog.getJobGroup())) {
                wrapper.eq("job_group", jobLog.getJobGroup());
            }
            if (StringUtils.isNotBlank(jobLog.getStatus())) {
                wrapper.eq("status", jobLog.getStatus());
            }
            if (StringUtils.isNotBlank(jobLog.getInvokeTarget())) {
                wrapper.like("invoke_target", jobLog.getInvokeTarget());
            }
            Map<String, Object> params = jobLog.getParams();
            Object beginTime = params.get("beginTime");
            Object endTime = params.get("endTime");
            if (beginTime != null && endTime != null) {
                wrapper.between("create_time", beginTime, endTime);
            } else if (beginTime != null) {
                wrapper.ge("create_time", beginTime);
            } else if (endTime != null) {
                wrapper.le("create_time", endTime);
            }
        }
        wrapper.orderByDesc("job_log_id");
        return this.selectList(wrapper);
    }

    /**
     * 查询所有调度任务日志
     *
     * @return 调度任务日志列表
     */
    default List<SysJobLog> selectJobLogAll() {
        QueryWrapper<SysJobLog> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("job_log_id");
        return this.selectList(wrapper);
    }

    /**
     * 通过调度任务日志ID查询调度信息
     *
     * @param jobLogId 调度任务日志ID
     * @return 调度任务日志对象信息
     */
    default SysJobLog selectJobLogById(Long jobLogId) {
        return this.selectById(jobLogId);
    }

    /**
     * 新增任务日志
     *
     * @param jobLog 调度日志信息
     * @return 结果
     */
    default int insertJobLog(SysJobLog jobLog) {
        return this.insert(jobLog);
    }

    /**
     * 批量删除调度日志信息
     *
     * @param logIds 需要删除的数据ID
     * @return 结果
     */
    default int deleteJobLogByIds(Long[] logIds) {
        if (logIds == null || logIds.length == 0) {
            return 0;
        }
        return this.deleteBatchIds(Arrays.asList(logIds));
    }

    /**
     * 删除任务日志
     *
     * @param jobId 调度日志ID
     * @return 结果
     */
    default int deleteJobLogById(Long jobId) {
        return this.deleteById(jobId);
    }

    /**
     * 清空任务日志
     */
    default void cleanJobLog() {
        this.delete(new QueryWrapper<>());
    }
}

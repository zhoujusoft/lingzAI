package lingzhou.agent.backend.skillstudio.project.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lingzhou.agent.backend.skillstudio.project.domain.SkillStudioProject;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

@Mapper
public interface SkillStudioProjectMapper extends BaseMapper<SkillStudioProject> {

    default IPage<SkillStudioProject> searchPage(
            Long userId, boolean adminUser, int page, int pageSize, String keyword, String projectType, String status) {
        QueryWrapper<SkillStudioProject> wrapper = new QueryWrapper<>();
        if (!adminUser) {
            wrapper.eq("create_user_id", userId);
        }
        wrapper.isNull("archived_at");
        applyKeywordFilter(wrapper, keyword);
        if (StringUtils.hasText(projectType)) {
            wrapper.eq("project_type", projectType.trim());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq("status", status.trim());
        }
        wrapper.orderByDesc("updated_at").orderByDesc("id");
        return this.selectPage(new Page<>(page, pageSize), wrapper);
    }

    private void applyKeywordFilter(QueryWrapper<SkillStudioProject> wrapper, String keyword) {
        if (wrapper == null) {
            return;
        }
        if (StringUtils.hasText(keyword)) {
            String normalized = keyword.trim();
            wrapper.and(w -> w.like("name", normalized).or().like("description", normalized));
        }
    }

    default SkillStudioProject selectOwnedProject(Long userId, Long id) {
        if (userId == null || id == null) {
            return null;
        }
        QueryWrapper<SkillStudioProject> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id).eq("create_user_id", userId).isNull("archived_at").last("limit 1");
        return this.selectOne(wrapper);
    }

    default SkillStudioProject selectActiveProject(Long id) {
        if (id == null) {
            return null;
        }
        QueryWrapper<SkillStudioProject> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id).isNull("archived_at").last("limit 1");
        return this.selectOne(wrapper);
    }

    default SkillStudioProject selectActiveByRuntimeSkillName(String runtimeSkillName) {
        if (!StringUtils.hasText(runtimeSkillName)) {
            return null;
        }
        QueryWrapper<SkillStudioProject> wrapper = new QueryWrapper<>();
        wrapper.eq("runtime_skill_name", runtimeSkillName.trim())
                .isNull("archived_at")
                .last("limit 1");
        return this.selectOne(wrapper);
    }

    default SkillStudioProject selectActiveByDraftSkillName(String draftSkillName) {
        if (!StringUtils.hasText(draftSkillName)) {
            return null;
        }
        QueryWrapper<SkillStudioProject> wrapper = new QueryWrapper<>();
        wrapper.eq("draft_skill_name", draftSkillName.trim())
                .isNull("archived_at")
                .last("limit 1");
        return this.selectOne(wrapper);
    }

    default int updateActiveStatusByRuntimeSkillName(String runtimeSkillName, String status) {
        if (!StringUtils.hasText(runtimeSkillName) || !StringUtils.hasText(status)) {
            return 0;
        }
        SkillStudioProject update = new SkillStudioProject();
        update.setStatus(status.trim());
        update.setUpdatedAt(new java.util.Date());
        QueryWrapper<SkillStudioProject> wrapper = new QueryWrapper<>();
        wrapper.eq("runtime_skill_name", runtimeSkillName.trim()).isNull("archived_at");
        return this.update(update, wrapper);
    }
}

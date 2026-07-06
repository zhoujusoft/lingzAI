package lingzhou.agent.backend.business.chat.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import lingzhou.agent.backend.business.chat.domain.RuntimeFileAsset;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RuntimeFileAssetMapper extends BaseMapper<RuntimeFileAsset> {

    default RuntimeFileAsset selectActiveByLocalPath(Long userId, Long sessionId, String fileRole, String localPath) {
        if (userId == null || userId <= 0 || localPath == null || localPath.isBlank()) {
            return null;
        }
        QueryWrapper<RuntimeFileAsset> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .eq("local_path", localPath.trim())
                .isNull("deleted_at")
                .orderByDesc("id")
                .last("limit 1");
        if (sessionId == null || sessionId <= 0) {
            wrapper.isNull("session_id");
        } else {
            wrapper.eq("session_id", sessionId);
        }
        if (fileRole != null && !fileRole.isBlank()) {
            wrapper.eq("file_role", fileRole.trim());
        }
        return this.selectOne(wrapper);
    }

    default RuntimeFileAsset selectOwnedByFileCode(Long userId, String fileCode) {
        if (userId == null || userId <= 0 || fileCode == null || fileCode.isBlank()) {
            return null;
        }
        QueryWrapper<RuntimeFileAsset> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .eq("file_code", fileCode.trim())
                .and(nested -> nested.isNull("deleted_at").or().eq("file_role", "TEMP"))
                .orderByDesc("id")
                .last("limit 1");
        return this.selectOne(wrapper);
    }

    default List<RuntimeFileAsset> selectOwnedActiveUploadsByFileCodes(Long userId, List<String> fileCodes) {
        if (userId == null || userId <= 0 || fileCodes == null || fileCodes.isEmpty()) {
            return List.of();
        }
        List<String> normalizedCodes = fileCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (normalizedCodes.isEmpty()) {
            return List.of();
        }
        QueryWrapper<RuntimeFileAsset> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .eq("file_role", "UPLOAD")
                .in("file_code", normalizedCodes)
                .isNull("deleted_at")
                .orderByDesc("id");
        return this.selectList(wrapper);
    }

    default List<RuntimeFileAsset> selectByRunId(Long userId, Long runId, String fileRole) {
        if (userId == null || userId <= 0 || runId == null || runId <= 0) {
            return List.of();
        }
        QueryWrapper<RuntimeFileAsset> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).eq("run_id", runId).isNull("deleted_at").orderByDesc("id");
        if (fileRole != null && !fileRole.isBlank()) {
            wrapper.eq("file_role", fileRole.trim());
        }
        return this.selectList(wrapper);
    }
}

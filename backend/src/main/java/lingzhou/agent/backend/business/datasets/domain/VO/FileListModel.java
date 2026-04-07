package lingzhou.agent.backend.business.datasets.domain.VO;

import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileListModel {
    private Long docId;
    private String fileName;
    private String fileId;
    private Set<String> indexIds;

    public FileListModel(Long docId, String fileName, String fileId) {
        this.docId = docId;
        this.fileName = fileName;
        this.fileId = fileId;
        this.indexIds = new HashSet<>();
    }
}

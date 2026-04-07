package lingzhou.agent.backend.business.datasets.domain;

import lombok.Data;

@Data
public class MateDataParam {

    private String kbId; // 知识库ID

    private String questionType; // 问题分类

    private String dataType; // 资料类型

    private String fileCode; // 文件编号

    private String year; // 文件年份

    private String techSpecCode; // 技术规范书编号

    private String parentDocId; // 父文档ID

    public MateDataParam() {}

    public MateDataParam(String kbId, String questionType, String techSpecCode) {
        this.kbId = kbId;
        this.questionType = questionType;
        this.techSpecCode = techSpecCode;
    }

    public MateDataParam(
            String kbId,
            String questionType,
            String dataType,
            String fileCode,
            String year,
            String techSpecCode,
            String parentDocId) {
        this.kbId = kbId;
        this.questionType = questionType;
        this.dataType = dataType;
        this.fileCode = fileCode;
        this.year = year;
        this.techSpecCode = techSpecCode;
        this.parentDocId = parentDocId;
    }
}

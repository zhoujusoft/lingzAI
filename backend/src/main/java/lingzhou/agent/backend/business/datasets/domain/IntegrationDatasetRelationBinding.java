package lingzhou.agent.backend.business.datasets.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

@TableName("integration_dataset_relation_binding")
public class IntegrationDatasetRelationBinding {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long datasetId;

    private String leftObjectCode;

    private String leftFieldName;

    private String rightObjectCode;

    private String rightFieldName;

    private String relationSource;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(Long datasetId) {
        this.datasetId = datasetId;
    }

    public String getLeftObjectCode() {
        return leftObjectCode;
    }

    public void setLeftObjectCode(String leftObjectCode) {
        this.leftObjectCode = leftObjectCode;
    }

    public String getLeftFieldName() {
        return leftFieldName;
    }

    public void setLeftFieldName(String leftFieldName) {
        this.leftFieldName = leftFieldName;
    }

    public String getRightObjectCode() {
        return rightObjectCode;
    }

    public void setRightObjectCode(String rightObjectCode) {
        this.rightObjectCode = rightObjectCode;
    }

    public String getRightFieldName() {
        return rightFieldName;
    }

    public void setRightFieldName(String rightFieldName) {
        this.rightFieldName = rightFieldName;
    }

    public String getRelationSource() {
        return relationSource;
    }

    public void setRelationSource(String relationSource) {
        this.relationSource = relationSource;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}

package lingzhou.agent.backend.business.datasets.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

@TableName("integration_dataset")
public class IntegrationDataset {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String datasetCode;

    private String name;

    private String sourceKind;

    private Long aiDataSourceId;

    private String lowcodePlatformKey;

    private String lowcodeAppId;

    private String lowcodeAppName;

    private String description;

    private String businessLogic;

    private String status;

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

    public String getDatasetCode() {
        return datasetCode;
    }

    public void setDatasetCode(String datasetCode) {
        this.datasetCode = datasetCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSourceKind() {
        return sourceKind;
    }

    public void setSourceKind(String sourceKind) {
        this.sourceKind = sourceKind;
    }

    public Long getAiDataSourceId() {
        return aiDataSourceId;
    }

    public void setAiDataSourceId(Long aiDataSourceId) {
        this.aiDataSourceId = aiDataSourceId;
    }

    public String getLowcodePlatformKey() {
        return lowcodePlatformKey;
    }

    public void setLowcodePlatformKey(String lowcodePlatformKey) {
        this.lowcodePlatformKey = lowcodePlatformKey;
    }

    public String getLowcodeAppId() {
        return lowcodeAppId;
    }

    public void setLowcodeAppId(String lowcodeAppId) {
        this.lowcodeAppId = lowcodeAppId;
    }

    public String getLowcodeAppName() {
        return lowcodeAppName;
    }

    public void setLowcodeAppName(String lowcodeAppName) {
        this.lowcodeAppName = lowcodeAppName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBusinessLogic() {
        return businessLogic;
    }

    public void setBusinessLogic(String businessLogic) {
        this.businessLogic = businessLogic;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

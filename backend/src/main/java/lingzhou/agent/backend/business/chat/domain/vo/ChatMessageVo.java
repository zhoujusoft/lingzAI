package lingzhou.agent.backend.business.chat.domain.vo;

public class ChatMessageVo {

    private Long id;
    private String messageCode;
    private Long parentMessageId;
    private String role;
    private String messageKind;
    private String content;
    private String segmentsJson;
    private String contentFormat;
    private String status;
    private String errorCode;
    private String errorMessage;
    private String createdAt;
    private String updatedAt;
    private String completedAt;
    private String paramsJson;
    private String attachmentsJson;
    private String artifactSummaryJson;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Boolean usageAvailable;
    private Integer llmCallCount;
    private Integer toolCallCount;
    private Long modelId;
    private String modelProvider;
    private String modelName;
    private String adapterType;
    private String usageSummaryJson;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMessageCode() {
        return messageCode;
    }

    public void setMessageCode(String messageCode) {
        this.messageCode = messageCode;
    }

    public Long getParentMessageId() {
        return parentMessageId;
    }

    public void setParentMessageId(Long parentMessageId) {
        this.parentMessageId = parentMessageId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getMessageKind() {
        return messageKind;
    }

    public void setMessageKind(String messageKind) {
        this.messageKind = messageKind;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSegmentsJson() {
        return segmentsJson;
    }

    public void setSegmentsJson(String segmentsJson) {
        this.segmentsJson = segmentsJson;
    }

    public String getContentFormat() {
        return contentFormat;
    }

    public void setContentFormat(String contentFormat) {
        this.contentFormat = contentFormat;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(String completedAt) {
        this.completedAt = completedAt;
    }

    public String getParamsJson() {
        return paramsJson;
    }

    public void setParamsJson(String paramsJson) {
        this.paramsJson = paramsJson;
    }

    public String getAttachmentsJson() {
        return attachmentsJson;
    }

    public void setAttachmentsJson(String attachmentsJson) {
        this.attachmentsJson = attachmentsJson;
    }

    public String getArtifactSummaryJson() {
        return artifactSummaryJson;
    }

    public void setArtifactSummaryJson(String artifactSummaryJson) {
        this.artifactSummaryJson = artifactSummaryJson;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public Boolean getUsageAvailable() {
        return usageAvailable;
    }

    public void setUsageAvailable(Boolean usageAvailable) {
        this.usageAvailable = usageAvailable;
    }

    public Integer getLlmCallCount() {
        return llmCallCount;
    }

    public void setLlmCallCount(Integer llmCallCount) {
        this.llmCallCount = llmCallCount;
    }

    public Integer getToolCallCount() {
        return toolCallCount;
    }

    public void setToolCallCount(Integer toolCallCount) {
        this.toolCallCount = toolCallCount;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public String getModelProvider() {
        return modelProvider;
    }

    public void setModelProvider(String modelProvider) {
        this.modelProvider = modelProvider;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getAdapterType() {
        return adapterType;
    }

    public void setAdapterType(String adapterType) {
        this.adapterType = adapterType;
    }

    public String getUsageSummaryJson() {
        return usageSummaryJson;
    }

    public void setUsageSummaryJson(String usageSummaryJson) {
        this.usageSummaryJson = usageSummaryJson;
    }
}

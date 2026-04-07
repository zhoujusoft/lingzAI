package lingzhou.agent.backend.business.skill.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

@TableName("skill_package_install")
public class SkillPackageInstall {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String packageId;

    private String runtimeSkillName;

    private String packageVersion;

    private Integer packageFormatVersion;

    private String sourceFilename;

    private String packageSha256;

    private String installMode;

    private String installStatus;

    private String dependencyStatus;

    private Long installedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date installedAt;

    private String summaryJson;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPackageId() {
        return packageId;
    }

    public void setPackageId(String packageId) {
        this.packageId = packageId;
    }

    public String getRuntimeSkillName() {
        return runtimeSkillName;
    }

    public void setRuntimeSkillName(String runtimeSkillName) {
        this.runtimeSkillName = runtimeSkillName;
    }

    public String getPackageVersion() {
        return packageVersion;
    }

    public void setPackageVersion(String packageVersion) {
        this.packageVersion = packageVersion;
    }

    public Integer getPackageFormatVersion() {
        return packageFormatVersion;
    }

    public void setPackageFormatVersion(Integer packageFormatVersion) {
        this.packageFormatVersion = packageFormatVersion;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public void setSourceFilename(String sourceFilename) {
        this.sourceFilename = sourceFilename;
    }

    public String getPackageSha256() {
        return packageSha256;
    }

    public void setPackageSha256(String packageSha256) {
        this.packageSha256 = packageSha256;
    }

    public String getInstallMode() {
        return installMode;
    }

    public void setInstallMode(String installMode) {
        this.installMode = installMode;
    }

    public String getInstallStatus() {
        return installStatus;
    }

    public void setInstallStatus(String installStatus) {
        this.installStatus = installStatus;
    }

    public String getDependencyStatus() {
        return dependencyStatus;
    }

    public void setDependencyStatus(String dependencyStatus) {
        this.dependencyStatus = dependencyStatus;
    }

    public Long getInstalledBy() {
        return installedBy;
    }

    public void setInstalledBy(Long installedBy) {
        this.installedBy = installedBy;
    }

    public Date getInstalledAt() {
        return installedAt;
    }

    public void setInstalledAt(Date installedAt) {
        this.installedAt = installedAt;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public void setSummaryJson(String summaryJson) {
        this.summaryJson = summaryJson;
    }
}

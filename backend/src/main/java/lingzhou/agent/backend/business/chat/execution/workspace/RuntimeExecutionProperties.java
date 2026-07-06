package lingzhou.agent.backend.business.chat.execution.workspace;

import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionMode;
import lingzhou.agent.backend.business.chat.execution.provider.RuntimeProviderType;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lingz.runtime")
public class RuntimeExecutionProperties {

    private RuntimeProviderType provider = RuntimeProviderType.NATIVE;
    private RuntimeExecutionMode mode = RuntimeExecutionMode.NATIVE;
    private String workspaceBaseDir = "workspaces";
    private int maxReadFileChars = 50000;
    private int commandTimeoutSeconds = 600;
    private int maxBashOutputChars = 20000;
    private int maxModelRoundsPerRun = 12;
    private int maxToolCallsPerRun = 24;
    private int maxPromptTokensPerRun = 1800000;
    private int maxCompletionTokensPerRun = 400000;
    private int maxTotalTokensPerRun = 2000000;
    private String pythonCommand = "python3.11";
    private String pipIndexUrl = "https://pypi.tuna.tsinghua.edu.cn/simple";

    public RuntimeProviderType getProvider() {
        return provider;
    }

    public void setProvider(RuntimeProviderType provider) {
        this.provider = provider;
    }

    public RuntimeExecutionMode getMode() {
        return mode;
    }

    public void setMode(RuntimeExecutionMode mode) {
        this.mode = mode;
    }

    public String getWorkspaceBaseDir() {
        return workspaceBaseDir;
    }

    public void setWorkspaceBaseDir(String workspaceBaseDir) {
        this.workspaceBaseDir = workspaceBaseDir;
    }

    public int getMaxReadFileChars() {
        return maxReadFileChars;
    }

    public void setMaxReadFileChars(int maxReadFileChars) {
        this.maxReadFileChars = maxReadFileChars;
    }

    public int getCommandTimeoutSeconds() {
        return commandTimeoutSeconds;
    }

    public void setCommandTimeoutSeconds(int commandTimeoutSeconds) {
        this.commandTimeoutSeconds = commandTimeoutSeconds;
    }

    public int getMaxBashOutputChars() {
        return maxBashOutputChars;
    }

    public void setMaxBashOutputChars(int maxBashOutputChars) {
        this.maxBashOutputChars = maxBashOutputChars;
    }

    public int getMaxModelRoundsPerRun() {
        return maxModelRoundsPerRun;
    }

    public void setMaxModelRoundsPerRun(int maxModelRoundsPerRun) {
        this.maxModelRoundsPerRun = maxModelRoundsPerRun;
    }

    public int getMaxToolCallsPerRun() {
        return maxToolCallsPerRun;
    }

    public void setMaxToolCallsPerRun(int maxToolCallsPerRun) {
        this.maxToolCallsPerRun = maxToolCallsPerRun;
    }

    public int getMaxPromptTokensPerRun() {
        return maxPromptTokensPerRun;
    }

    public void setMaxPromptTokensPerRun(int maxPromptTokensPerRun) {
        this.maxPromptTokensPerRun = maxPromptTokensPerRun;
    }

    public int getMaxCompletionTokensPerRun() {
        return maxCompletionTokensPerRun;
    }

    public void setMaxCompletionTokensPerRun(int maxCompletionTokensPerRun) {
        this.maxCompletionTokensPerRun = maxCompletionTokensPerRun;
    }

    public int getMaxTotalTokensPerRun() {
        return maxTotalTokensPerRun;
    }

    public void setMaxTotalTokensPerRun(int maxTotalTokensPerRun) {
        this.maxTotalTokensPerRun = maxTotalTokensPerRun;
    }

    public String getPythonCommand() {
        return pythonCommand;
    }

    public void setPythonCommand(String pythonCommand) {
        this.pythonCommand = pythonCommand;
    }

    public String getPipIndexUrl() {
        return pipIndexUrl;
    }

    public void setPipIndexUrl(String pipIndexUrl) {
        this.pipIndexUrl = pipIndexUrl;
    }
}

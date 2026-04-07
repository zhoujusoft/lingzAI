package lingzhou.agent.backend.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.skills")
public class SkillProperties {

    private String rootDir = "./skills";

    private final PackageProperties packageConfig = new PackageProperties();

    private final InstallerProperties installer = new InstallerProperties();

    public String getRootDir() {
        return rootDir;
    }

    public void setRootDir(String rootDir) {
        this.rootDir = rootDir;
    }

    public PackageProperties getPackageConfig() {
        return packageConfig;
    }

    public InstallerProperties getInstaller() {
        return installer;
    }

    public static class PackageProperties {

        private String password = "change-me";

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class InstallerProperties {

        private boolean enableDependencyInstall = true;

        private String pythonCommand = "python3";

        private String pipArgs = "-m pip install -r";

        private boolean continueOnDependencyError = true;

        public boolean isEnableDependencyInstall() {
            return enableDependencyInstall;
        }

        public void setEnableDependencyInstall(boolean enableDependencyInstall) {
            this.enableDependencyInstall = enableDependencyInstall;
        }

        public String getPythonCommand() {
            return pythonCommand;
        }

        public void setPythonCommand(String pythonCommand) {
            this.pythonCommand = pythonCommand;
        }

        public String getPipArgs() {
            return pipArgs;
        }

        public void setPipArgs(String pipArgs) {
            this.pipArgs = pipArgs;
        }

        public boolean isContinueOnDependencyError() {
            return continueOnDependencyError;
        }

        public void setContinueOnDependencyError(boolean continueOnDependencyError) {
            this.continueOnDependencyError = continueOnDependencyError;
        }
    }
}

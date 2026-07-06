package lingzhou.agent.backend.app;

import java.nio.file.Path;

/**
 * 当前技能执行作用域。
 *
 * <p>用于让 readFile / writeFile / runPython 这类公共工具在技能运行期间能够感知“当前 skill 目录”，
 * 让相对路径默认落在该目录下，避免误写到项目根目录。后续增加工作区概念时，也可以沿用这层作用域继续扩展。
 */
public final class SkillExecutionScope {

    private static final ThreadLocal<Path> CURRENT_SKILL_DIR = new ThreadLocal<>();

    private SkillExecutionScope() {}

    public static void activate(Path skillDir) {
        if (skillDir == null) {
            clear();
            return;
        }
        CURRENT_SKILL_DIR.set(skillDir.toAbsolutePath().normalize());
    }

    public static Path currentSkillDir() {
        return CURRENT_SKILL_DIR.get();
    }

    public static boolean hasActiveSkillDir() {
        return CURRENT_SKILL_DIR.get() != null;
    }

    public static void clear() {
        CURRENT_SKILL_DIR.remove();
    }
}

package lingzhou.agent.backend.capability.permission.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 技能调用注解
 * 标记需要检查权限的技能调用方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InvokeSkill {
    /**
     * 技能名称
     */
    String value() default "";
}

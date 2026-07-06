package lingzhou.agent.backend.capability.permission.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具调用注解
 * 标记需要检查权限的工具调用方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InvokeTool {
    /**
     * 工具名称
     */
    String value() default "";
}

package lingzhou.agent.backend.business.chat.execution.provider;

import lingzhou.agent.backend.business.chat.execution.model.RuntimeExecutionMode;

public enum RuntimeProviderType {
    NATIVE,
    DOCKER,
    REMOTE;

    public static RuntimeProviderType fromMode(RuntimeExecutionMode mode) {
        if (mode == null) {
            return null;
        }
        return switch (mode) {
            case NATIVE -> NATIVE;
            case DOCKER -> DOCKER;
            case DISABLED -> null;
        };
    }

    public RuntimeExecutionMode toMode() {
        return switch (this) {
            case NATIVE -> RuntimeExecutionMode.NATIVE;
            case DOCKER -> RuntimeExecutionMode.DOCKER;
            case REMOTE -> RuntimeExecutionMode.DOCKER;
        };
    }
}

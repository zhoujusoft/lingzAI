package lingzhou.agent.backend.business.chat.execution.nativefs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.execution.model.SandboxRoot;

public final class PathJail {

    private final List<Path> allowedRoots;
    private final Map<Path, SandboxRoot> permissions;

    public PathJail(List<SandboxRoot> roots) {
        if (roots == null || roots.isEmpty()) {
            this.allowedRoots = List.of();
            this.permissions = Map.of();
            return;
        }
        List<Path> normalizedRoots = new ArrayList<>();
        Map<Path, SandboxRoot> permissionMap = new LinkedHashMap<>();
        for (SandboxRoot root : roots) {
            if (root == null || root.hostPath() == null || root.hostPath().isBlank()) {
                continue;
            }
            try {
                Path path = Path.of(root.hostPath()).toAbsolutePath().normalize();
                if (root.write()) {
                    Files.createDirectories(path);
                }
                Path real = Files.exists(path) ? path.toRealPath() : path.normalize();
                if (!normalizedRoots.contains(real)) {
                    normalizedRoots.add(real);
                }
                permissionMap.put(real, root);
            } catch (IOException ex) {
                throw new IllegalStateException("初始化路径沙盒失败: " + root.hostPath(), ex);
            }
        }
        this.allowedRoots = Collections.unmodifiableList(normalizedRoots);
        this.permissions = Collections.unmodifiableMap(permissionMap);
    }

    public Path assertReadable(Path path) {
        Path resolved = assertInside(path);
        if (!isReadable(resolved)) {
            throw new SandboxViolationException("路径不可读: " + resolved);
        }
        return resolved;
    }

    public Path assertWritable(Path path) {
        Path resolved = assertInside(path);
        if (!isWritable(resolved)) {
            throw new SandboxViolationException("路径不可写: " + resolved);
        }
        return resolved;
    }

    public Path assertInside(Path path) {
        if (path == null) {
            throw new SandboxViolationException("路径不能为空");
        }
        Path normalized = path.toAbsolutePath().normalize();
        Path checked = normalizeExistingOrCandidate(normalized);
        for (Path root : allowedRoots) {
            if (checked.startsWith(root)) {
                return normalized;
            }
        }
        throw new SandboxViolationException("路径越界: " + normalized);
    }

    public boolean isReadable(Path path) {
        Path checked = normalizeExistingOrCandidate(path);
        for (Map.Entry<Path, SandboxRoot> entry : permissions.entrySet()) {
            if (checked.startsWith(entry.getKey())) {
                return entry.getValue().read();
            }
        }
        return false;
    }

    public boolean isWritable(Path path) {
        Path checked = normalizeExistingOrCandidate(path);
        for (Map.Entry<Path, SandboxRoot> entry : permissions.entrySet()) {
            if (checked.startsWith(entry.getKey())) {
                return entry.getValue().write();
            }
        }
        return false;
    }

    private Path normalizeExistingOrCandidate(Path path) {
        try {
            return Files.exists(path) ? path.toRealPath() : path.normalize();
        } catch (IOException ex) {
            throw new SandboxViolationException("路径校验失败: " + ex.getMessage());
        }
    }
}

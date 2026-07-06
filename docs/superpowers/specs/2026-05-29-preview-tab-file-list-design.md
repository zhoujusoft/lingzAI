# Preview Pane Tab 功能设计

## 概述

在现有的 HTML 预览面板（preview-pane）之上添加 Tab 切换功能，第一个 Tab 保持现有的 HTML 预览能力，第二个 Tab 新增文件列表功能，用于展示当前会话中的聊天附件和技能产物文件。

## 需求总结

### 功能需求

1. **Tab 切换**：在预览面板顶部添加两个 Tab
   - Tab 1：HTML 预览（现有功能）
   - Tab 2：文件列表（新功能）

2. **文件列表**：展示当前会话的文件资产
   - 聊天附件（UPLOAD）
   - 技能产物（ARTIFACT）
   - 临时文件（TEMP）

3. **懒加载**：切换到文件列表 Tab 时才加载数据

4. **状态保持**：Tab 切换时保持各自状态，不重新加载

5. **默认 Tab**：默认显示 HTML 预览 Tab

6. **显示条件**：有预览内容时才显示整个面板（保持现有行为）

### 非功能需求

- 遵循现有前端组件组织方式
- 使用 Composition API + `<script setup>`
- API 调用统一走 `@lingzhou/core/http/request`
- 样式与现有 UI 风格保持一致

## 技术设计

### 组件结构

```
frontend/packages/web/src/views/front/components/agent-chat/
├── FrontAgentChatPreview.vue        # 现有组件，保持不变
├── PreviewTabContainer.vue          # 新增：Tab 容器组件
├── FilePreviewPane.vue              # 新增：文件列表组件
└── FilePreviewItem.vue              # 新增：文件列表项组件

frontend/packages/web/src/composables/
└── useRuntimeFileAssets.js          # 新增：文件资产数据获取

frontend/packages/web/src/api/
└── runtimeFileAsset.js              # 新增：API 调用封装
```

### 数据流

```
用户切换 Tab
    │
    ▼
PreviewTabContainer (管理 activeTab 状态)
    │
    ├── Tab 1: HTML 预览
    │       └── FrontAgentChatPreview (现有组件)
    │
    └── Tab 2: 文件列表
            │
            ▼
        FilePreviewPane
            │
            ▼
        useRuntimeFileAssets (composable)
            │
            ▼
        GET /api/files/assets
            │
            ▼
        RuntimeFileAssetService.listAssets()
```

### API 设计

#### 新增 API 文件：`src/api/runtimeFileAsset.js`

```javascript
import request from '@lingzhou/core/http/request';

/**
 * 获取运行时文件资产列表
 * @param {Object} params
 * @param {string} [params.sessionId] - 会话ID
 * @param {string} [params.fileRole] - 文件角色: UPLOAD, ARTIFACT, TEMP
 * @param {number} [params.pageNo] - 页码
 * @param {number} [params.pageSize] - 每页大小
 */
export function listRuntimeFileAssets(params) {
  return request.get('/files/assets', { params });
}

/**
 * 获取文件预览URL
 * @param {string} fileCode - 文件编码
 */
export function getFilePreviewUrl(fileCode) {
  return `/api/files/assets/${fileCode}/preview`;
}

/**
 * 获取文件下载URL
 * @param {string} fileCode - 文件编码
 */
export function getFileDownloadUrl(fileCode) {
  return `/api/files/assets/${fileCode}/download`;
}
```

### Composable 设计

#### 新增：`src/composables/useRuntimeFileAssets.js`

职责：
- 管理文件资产列表数据
- 处理加载状态和错误状态
- 按文件角色分组
- 支持懒加载（enabled 时才加载）

核心状态：
- `loading`: 加载状态
- `error`: 错误信息
- `assets`: 文件列表
- `groupedAssets`: 按角色分组的文件
- `pagination`: 分页信息

核心方法：
- `loadAssets(pageNo)`: 加载文件列表

### 组件设计

#### 1. PreviewTabContainer.vue

职责：
- 管理 Tab 切换状态
- 包装现有预览组件和文件列表组件
- 处理关闭事件

Props：
- `renderPayload`: 预览内容（透传给 FrontAgentChatPreview）
- `sessionCode`: 会话编码（传递给 FilePreviewPane）

Events：
- `close`: 关闭预览面板

行为：
- 默认显示 HTML 预览 Tab
- 当有新的 renderPayload 时，自动切换到 HTML 预览 Tab
- Tab 切换时保持各自状态

#### 2. FilePreviewPane.vue

职责：
- 展示文件列表
- 处理加载、错误、空状态
- 按文件角色分组显示

Props：
- `sessionCode`: 会话编码
- `enabled`: 是否启用（用于懒加载）

状态：
- 使用 `useRuntimeFileAssets` composable 管理数据

#### 3. FilePreviewItem.vue

职责：
- 渲染单个文件项
- 显示文件图标、名称、大小、时间
- 提供预览和下载操作

Props：
- `file`: 文件资产对象

行为：
- 点击可预览文件（如果支持预览）或下载
- 根据文件类型显示不同图标和颜色

### 页面集成

修改文件：
- `FrontAgentChatPage.vue`
- `FrontAgentChatV2Page.vue`

修改内容：
1. 导入 `PreviewTabContainer` 组件
2. 获取当前会话的 `sessionCode`
3. 用 `PreviewTabContainer` 替换现有的 `FrontAgentChatPreview`
4. 传递 `sessionCode` prop

### 文件类型支持

预览支持的文件类型：
- 文本文件：`.txt`, `.md`, `.log`, `.csv`
- 代码文件：`.html`, `.htm`, `.json`
- 图片文件：`.png`, `.jpg`, `.jpeg`, `.gif`, `.webp`, `.svg`

图标映射：
- 图片 → `image` (绿色)
- PDF → `picture_as_pdf` (红色)
- Excel → `table` (蓝色)
- Word → `description` (蓝色)
- HTML/JSON → `code`/`data_object` (紫色)
- 文本 → `article` (灰色)
- 其他 → `insert_drive_file` (灰色)

## 实现步骤

1. **API 层**：创建 `src/api/runtimeFileAsset.js`
2. **Composable**：创建 `src/composables/useRuntimeFileAssets.js`
3. **文件列表项组件**：创建 `FilePreviewItem.vue`
4. **文件列表组件**：创建 `FilePreviewPane.vue`
5. **Tab 容器组件**：创建 `PreviewTabContainer.vue`
6. **页面集成**：修改 `FrontAgentChatPage.vue` 和 `FrontAgentChatV2Page.vue`

## 测试要点

1. Tab 切换正常，状态保持
2. 文件列表懒加载
3. 文件分组显示正确
4. 文件预览和下载功能正常
5. 空状态、加载状态、错误状态显示正常
6. 响应式布局正常

## 风险和注意事项

1. **sessionCode 获取**：需要确认页面中如何获取当前会话的 sessionCode
2. **权限**：文件资产 API 需要用户认证
3. **性能**：大量文件时考虑虚拟滚动（当前使用分页，暂不需要）

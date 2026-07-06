# 会话切换骨架屏 Loading 效果设计

## 概述

在会话历史切换时，消息区域显示带头像的骨架屏 loading 效果，提升用户体验，避免切换时的视觉断层。

## 需求

- 切换会话时，消息区域显示 2-3 条骨架消息
- 骨架消息包含真实头像（用户头像、AI 助手头像）
- 消息内容显示为带 shimmer 闪烁动画的骨架条
- 切换时清空旧消息，显示骨架屏

## 架构

### 组件职责

```
FrontChatWorkspace.vue
├── 新增状态: loadingConversation (Boolean)
├── 新增状态: loadingConversationId (String|null)
└── 传递给 ChatMessageStream

ChatMessageStream.vue
├── 新增 prop: loading (Boolean)
├── 新增 prop: loadingMessageCount (Number, 默认3)
├── 复用已有 prop: userAvatarUrl, assistantAvatarUrl
└── 新增骨架消息渲染逻辑
```

### 数据流

```
用户点击会话
    ↓
FrontChatWorkspace.selectConversation()
    ↓
1. loadingConversation = true
2. loadingConversationId = conversationId
3. messages = []  // 清空旧消息
    ↓
ChatMessageStream 检测 loading=true
    ↓
渲染骨架消息（使用真实头像）
    ↓
API 返回新消息
    ↓
1. messages = newMessages
2. loadingConversation = false
    ↓
ChatMessageStream 渲染真实消息
```

## 骨架消息结构

显示 3 条骨架消息，交替展示用户和 AI 消息：

```
消息1 (用户):
[空占位] [用户头像] [骨架条 ████████████]

消息2 (AI):
[AI头像] [骨架条 ████████████████████]
         [骨架条 ████████████]

消息3 (用户):
[空占位] [用户头像] [骨架条 ████████]
```

### 骨架条规格

- **高度**: 16px（约一行文字高度）
- **宽度**: 随机变化（60%-90% 容器宽度），增加真实感
- **圆角**: 6px
- **间距**: 骨架条之间 8px

## CSS 动画

使用 shimmer 闪烁效果：

```css
@keyframes shimmer {
    0% { background-position: -200% 0; }
    100% { background-position: 200% 0; }
}

.skeleton-bar {
    background: linear-gradient(
        90deg,
        #f0f0f0 25%,
        #e0e0e0 50%,
        #f0f0f0 75%
    );
    background-size: 200% 100%;
    animation: shimmer 1.5s infinite;
    border-radius: 6px;
}
```

## 文件改动

### FrontChatWorkspace.vue

1. 在 `data()` 中新增状态：
   - `loadingConversation: false`
   - `loadingConversationId: null`

2. 修改 `selectConversation()` 方法：
   - 设置 `loadingConversation = true`
   - 清空 `messages`
   - API 完成后设置 `loadingConversation = false`

3. 修改模板，传递新 props 给 `ChatMessageStream`：
   - `:loading="loadingConversation"`
   - `:loading-message-count="3"`

### ChatMessageStream.vue

1. 新增 props：
   ```javascript
   loading: { type: Boolean, default: false },
   loadingMessageCount: { type: Number, default: 3 },
   ```

2. 新增 computed：
   - `skeletonMessages()` - 生成骨架消息数据

3. 新增模板逻辑：
   - 当 `loading === true` 时，渲染骨架消息
   - 骨架消息使用 `v-for` 循环，复用现有的头像渲染逻辑

4. 新增 CSS：
   - `.skeleton-bar` 骨架条样式
   - `@keyframes shimmer` 动画

## 头像来源

复用现有头像数据，无需额外获取：

| 头像 | 来源 |
|------|------|
| 用户头像 | `userAvatarUrl` prop (来自 `currentUserState.profile.avatarUrl`) |
| AI 头像 | `assistantAvatarUrl` prop (来自 `agentConfigState.template.avatarUrl`) |

## 错误处理

- 如果加载失败，`loadingConversation` 仍需设为 `false`
- 显示错误提示（复用现有 `chatError` 机制）

## 测试要点

1. 切换会话时，骨架屏正确显示
2. 骨架屏显示真实头像
3. shimmer 动画正常播放
4. 加载完成后，骨架屏消失，真实消息显示
5. 加载失败时，骨架屏消失，显示错误提示
6. 快速连续切换多个会话，状态正确

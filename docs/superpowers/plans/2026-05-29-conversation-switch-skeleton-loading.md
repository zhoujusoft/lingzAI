# 会话切换骨架屏 Loading 效果实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在会话历史切换时，消息区域显示带头像的骨架屏 loading 效果，提升用户体验。

**Architecture:** 在 FrontChatWorkspace 中添加 loadingConversation 状态控制，传递给 ChatMessageStream 组件。ChatMessageStream 根据该状态渲染骨架消息，使用真实头像和 shimmer 动画。

**Tech Stack:** Vue 3 Options API, CSS animations

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `frontend/packages/web/src/views/front/components/front-chat/FrontChatWorkspace.vue` | 修改 | 添加 loading 状态，控制切换流程 |
| `frontend/packages/web/src/views/front/components/front-chat/ChatMessageStream.vue` | 修改 | 添加 loading prop，渲染骨架消息，添加 CSS |

---

### Task 1: FrontChatWorkspace 添加 loading 状态

**Files:**
- Modify: `frontend/packages/web/src/views/front/components/front-chat/FrontChatWorkspace.vue:404-437`

- [ ] **Step 1: 在 data() 中添加 loadingConversation 状态**

在 `data()` return 对象中，在 `showFileDropOverlay: false,` 后添加：

```javascript
showFileDropOverlay: false,
loadingConversation: false,
loadingConversationId: null,
```

- [ ] **Step 2: 验证修改**

运行前端开发服务器，确认无语法错误：

```bash
cd /home/fzyhccg/workspaceai/lingzhou-agent/frontend && pnpm dev
```

预期：服务启动无报错

- [ ] **Step 3: 提交**

```bash
git add frontend/packages/web/src/views/front/components/front-chat/FrontChatWorkspace.vue
git commit -m "feat(chat): add loadingConversation state for skeleton loading"
```

---

### Task 2: FrontChatWorkspace 修改 selectConversation 方法

**Files:**
- Modify: `frontend/packages/web/src/views/front/components/front-chat/FrontChatWorkspace.vue:1300-1318`

- [ ] **Step 1: 修改 selectConversation 方法添加 loading 控制**

将原方法：

```javascript
async selectConversation(item) {
    const conversationId = item?.id ?? null;
    if (!conversationId || conversationId === this.selectedConversationId) {
        return;
    }
    this.applyConversationContext(item);
    this.selectedConversationId = conversationId;
    this.sessionId = conversationId;
    const storageKey = this.getSessionStorageKey();
    if (storageKey) {
        window.localStorage.setItem(storageKey, this.sessionId);
    }
    this.conversationItems = this.conversationItems.map(current =>
        ChatConversationBean.from({
            ...current,
            active: current.id === conversationId,
        })
    );
    await this.loadConversationMessages(conversationId);
},
```

修改为：

```javascript
async selectConversation(item) {
    const conversationId = item?.id ?? null;
    if (!conversationId || conversationId === this.selectedConversationId) {
        return;
    }
    this.applyConversationContext(item);
    this.selectedConversationId = conversationId;
    this.sessionId = conversationId;
    const storageKey = this.getSessionStorageKey();
    if (storageKey) {
        window.localStorage.setItem(storageKey, this.sessionId);
    }
    this.conversationItems = this.conversationItems.map(current =>
        ChatConversationBean.from({
            ...current,
            active: current.id === conversationId,
        })
    );
    // 开始 loading，清空旧消息
    this.loadingConversation = true;
    this.loadingConversationId = conversationId;
    this.messages = [];
    try {
        await this.loadConversationMessages(conversationId);
    } finally {
        // 确保 loading 状态被清除
        this.loadingConversation = false;
        this.loadingConversationId = null;
    }
},
```

- [ ] **Step 2: 验证修改**

运行前端开发服务器，确认无语法错误：

```bash
cd /home/fzyhccg/workspaceai/lingzhou-agent/frontend && pnpm dev
```

预期：服务启动无报错

- [ ] **Step 3: 提交**

```bash
git add frontend/packages/web/src/views/front/components/front-chat/FrontChatWorkspace.vue
git commit -m "feat(chat): control loading state in selectConversation"
```

---

### Task 3: FrontChatWorkspace 传递 loading props 给 ChatMessageStream

**Files:**
- Modify: `frontend/packages/web/src/views/front/components/front-chat/FrontChatWorkspace.vue:74-91`

- [ ] **Step 1: 在 ChatMessageStream 组件上添加 loading props**

找到 ChatMessageStream 组件（约第 74 行），在现有 props 后添加：

```vue
<ChatMessageStream
    :messages="messages"
    :scroll-token="scrollToken"
    :should-auto-scroll="shouldAutoScroll"
    :empty-title="emptyTitle"
    :empty-description="emptyDescription"
    :empty-icon="emptyIcon"
    :assistant-avatar-url="assistantAvatarUrl"
    :assistant-icon="assistantIcon"
    :user-avatar-url="resolvedUserAvatarUrl"
    :welcome-actions="welcomeActions"
    :split-view="Boolean(activeHtml)"
    :loading="loadingConversation"
    :loading-message-count="3"
    @toggle-segment="toggleSegment"
    @open-html-preview="openHtmlPreview"
    @open-citation="openCitationPreview"
    @frontend-render-action="handleFrontendRenderAction"
    @welcome-action="handleWelcomeAction"
/>
```

- [ ] **Step 2: 验证修改**

运行前端开发服务器，确认无语法错误：

```bash
cd /home/fzyhccg/workspaceai/lingzhou-agent/frontend && pnpm dev
```

预期：服务启动无报错

- [ ] **Step 3: 提交**

```bash
git add frontend/packages/web/src/views/front/components/front-chat/FrontChatWorkspace.vue
git commit -m "feat(chat): pass loading props to ChatMessageStream"
```

---

### Task 4: ChatMessageStream 添加 loading props 定义

**Files:**
- Modify: `frontend/packages/web/src/views/front/components/front-chat/ChatMessageStream.vue:700-703`

- [ ] **Step 1: 在 props 中添加 loading 和 loadingMessageCount**

在 `userAvatarUrl` prop 后添加：

```javascript
userAvatarUrl: {
    type: String,
    default: '',
},
loading: {
    type: Boolean,
    default: false,
},
loadingMessageCount: {
    type: Number,
    default: 3,
},
welcomeActions: {
    type: Array,
    default: () => [],
},
```

- [ ] **Step 2: 验证修改**

运行前端开发服务器，确认无语法错误：

```bash
cd /home/fzyhccg/workspaceai/lingzhou-agent/frontend && pnpm dev
```

预期：服务启动无报错

- [ ] **Step 3: 提交**

```bash
git add frontend/packages/web/src/views/front/components/front-chat/ChatMessageStream.vue
git commit -m "feat(chat): add loading props to ChatMessageStream"
```

---

### Task 5: ChatMessageStream 添加骨架消息 computed

**Files:**
- Modify: `frontend/packages/web/src/views/front/components/front-chat/ChatMessageStream.vue` (computed 部分)

- [ ] **Step 1: 找到 computed 部分并添加 skeletonMessages**

在 computed 对象中添加 `skeletonMessages` computed 属性。找到 `assistantVisual` computed 后添加：

```javascript
skeletonMessages() {
    const count = Math.max(1, Math.min(5, this.loadingMessageCount || 3));
    const messages = [];
    for (let i = 0; i < count; i++) {
        const isUser = i % 2 === 0;
        const barCount = isUser ? 1 : 2;
        const bars = [];
        for (let j = 0; j < barCount; j++) {
            // 随机宽度 60%-90%
            const width = 60 + Math.random() * 30;
            bars.push({ width: `${width}%` });
        }
        messages.push({
            kind: isUser ? 'user' : 'assistant',
            bars,
        });
    }
    return messages;
},
```

- [ ] **Step 2: 验证修改**

运行前端开发服务器，确认无语法错误：

```bash
cd /home/fzyhccg/workspaceai/lingzhou-agent/frontend && pnpm dev
```

预期：服务启动无报错

- [ ] **Step 3: 提交**

```bash
git add frontend/packages/web/src/views/front/components/front-chat/ChatMessageStream.vue
git commit -m "feat(chat): add skeletonMessages computed"
```

---

### Task 6: ChatMessageStream 添加骨架消息模板

**Files:**
- Modify: `frontend/packages/web/src/views/front/components/front-chat/ChatMessageStream.vue:1-638`

- [ ] **Step 1: 修改模板，添加骨架消息渲染逻辑**

将模板开头的：

```vue
<template>
    <div
        ref="chatWindow"
        class="chat-message-stream chat-message-stream--default custom-scrollbar flex-1 overflow-y-auto px-4 py-8 sm:px-6 lg:px-8"
    >
        <div :class="visibleMessages.length === 0 ? 'flex min-h-full items-center' : 'space-y-6'">
```

修改为：

```vue
<template>
    <div
        ref="chatWindow"
        class="chat-message-stream chat-message-stream--default custom-scrollbar flex-1 overflow-y-auto px-4 py-8 sm:px-6 lg:px-8"
    >
        <!-- 骨架屏 loading 状态 -->
        <div v-if="loading" class="space-y-6">
            <div
                v-for="(skeleton, index) in skeletonMessages"
                :key="index"
                class="grid grid-cols-[auto_1fr_auto] gap-3 w-full items-start"
            >
                <!-- 第一列：左头像（助手消息）或空占位（用户消息） -->
                <div
                    v-if="skeleton.kind === 'assistant'"
                    :class="[
                        'chat-assistant-avatar flex h-8 w-8 shrink-0 items-center justify-center rounded-full',
                        assistantVisual.type === 'image'
                            ? 'overflow-hidden bg-transparent'
                            : 'text-white',
                    ]"
                >
                    <img
                        v-if="assistantVisual.type === 'image'"
                        :src="assistantVisual.value"
                        alt="assistant avatar"
                        class="chat-avatar-image h-full w-full rounded-full object-cover"
                    />
                    <span
                        v-else-if="assistantVisual.type === 'material'"
                        class="material-symbols-outlined text-lg"
                    >
                        {{ assistantVisual.value }}
                    </span>
                    <span v-else class="text-lg leading-none">{{ assistantVisual.value }}</span>
                </div>
                <div v-else class="w-8 shrink-0"></div>

                <!-- 第二列：骨架条 -->
                <div
                    :class="[
                        'flex min-w-0 flex-col gap-2',
                        skeleton.kind === 'user' ? 'items-end justify-self-end' : 'items-start justify-self-start',
                    ]"
                >
                    <div
                        v-for="(bar, barIndex) in skeleton.bars"
                        :key="barIndex"
                        class="skeleton-bar h-4 rounded-md"
                        :style="{ width: bar.width }"
                    ></div>
                </div>

                <!-- 第三列：右头像（用户消息）或空占位（助手消息） -->
                <div
                    v-if="skeleton.kind === 'user'"
                    class="chat-user-avatar flex h-8 w-8 shrink-0 items-center justify-center overflow-hidden rounded-full bg-slate-100"
                >
                    <img
                        v-if="userAvatarUrl"
                        :src="userAvatarUrl"
                        alt="user avatar"
                        class="chat-avatar-image h-full w-full object-cover"
                    />
                    <span v-else class="material-symbols-outlined text-lg text-slate-500">
                        account_circle
                    </span>
                </div>
                <div v-else class="w-8 shrink-0"></div>
            </div>
        </div>

        <!-- 正常消息列表 -->
        <div v-else :class="visibleMessages.length === 0 ? 'flex min-h-full items-center' : 'space-y-6'">
```

并在模板末尾的 `</div>` 前添加闭合的 `</div>`（注意：需要找到原有的 `</div>` 并确保正确闭合）。

实际上，需要将整个消息列表部分包裹在 `v-else` 中。让我重新设计这个修改：

找到模板中 `<div :class="visibleMessages.length === 0 ? ..."` 这一行，将其改为：

```vue
<div v-if="loading" class="space-y-6">
    <!-- 骨架消息 -->
    <div
        v-for="(skeleton, index) in skeletonMessages"
        :key="index"
        class="grid grid-cols-[auto_1fr_auto] gap-3 w-full items-start"
    >
        <!-- 第一列：左头像（助手消息）或空占位（用户消息） -->
        <div
            v-if="skeleton.kind === 'assistant'"
            :class="[
                'chat-assistant-avatar flex h-8 w-8 shrink-0 items-center justify-center rounded-full',
                assistantVisual.type === 'image'
                    ? 'overflow-hidden bg-transparent'
                    : 'text-white',
            ]"
        >
            <img
                v-if="assistantVisual.type === 'image'"
                :src="assistantVisual.value"
                alt="assistant avatar"
                class="chat-avatar-image h-full w-full rounded-full object-cover"
            />
            <span
                v-else-if="assistantVisual.type === 'material'"
                class="material-symbols-outlined text-lg"
            >
                {{ assistantVisual.value }}
            </span>
            <span v-else class="text-lg leading-none">{{ assistantVisual.value }}</span>
        </div>
        <div v-else class="w-8 shrink-0"></div>

        <!-- 第二列：骨架条 -->
        <div
            :class="[
                'flex min-w-0 flex-col gap-2',
                skeleton.kind === 'user' ? 'items-end justify-self-end' : 'items-start justify-self-start',
            ]"
        >
            <div
                v-for="(bar, barIndex) in skeleton.bars"
                :key="barIndex"
                class="skeleton-bar h-4 rounded-md"
                :style="{ width: bar.width }"
            ></div>
        </div>

        <!-- 第三列：右头像（用户消息）或空占位（助手消息） -->
        <div
            v-if="skeleton.kind === 'user'"
            class="chat-user-avatar flex h-8 w-8 shrink-0 items-center justify-center overflow-hidden rounded-full bg-slate-100"
        >
            <img
                v-if="userAvatarUrl"
                :src="userAvatarUrl"
                alt="user avatar"
                class="chat-avatar-image h-full w-full object-cover"
            />
            <span v-else class="material-symbols-outlined text-lg text-slate-500">
                account_circle
            </span>
        </div>
        <div v-else class="w-8 shrink-0"></div>
    </div>
</div>
<div v-else :class="visibleMessages.length === 0 ? 'flex min-h-full items-center' : 'space-y-6'">
```

- [ ] **Step 2: 验证修改**

运行前端开发服务器，确认无语法错误：

```bash
cd /home/fzyhccg/workspaceai/lingzhou-agent/frontend && pnpm dev
```

预期：服务启动无报错

- [ ] **Step 3: 提交**

```bash
git add frontend/packages/web/src/views/front/components/front-chat/ChatMessageStream.vue
git commit -m "feat(chat): add skeleton message template"
```

---

### Task 7: ChatMessageStream 添加骨架屏 CSS 样式

**Files:**
- Modify: `frontend/packages/web/src/views/front/components/front-chat/ChatMessageStream.vue` (style 部分)

- [ ] **Step 1: 在 style 部分添加骨架屏样式**

在 `<style scoped>` 中添加：

```css
/* 骨架屏 shimmer 动画 */
@keyframes shimmer {
    0% {
        background-position: -200% 0;
    }
    100% {
        background-position: 200% 0;
    }
}

.skeleton-bar {
    background: linear-gradient(
        90deg,
        #f0f0f0 25%,
        #e0e0e0 50%,
        #f0f0f0 75%
    );
    background-size: 200% 100%;
    animation: shimmer 1.5s infinite linear;
}
```

- [ ] **Step 2: 验证修改**

运行前端开发服务器，确认无语法错误：

```bash
cd /home/fzyhccg/workspaceai/lingzhou-agent/frontend && pnpm dev
```

预期：服务启动无报错

- [ ] **Step 3: 提交**

```bash
git add frontend/packages/web/src/views/front/components/front-chat/ChatMessageStream.vue
git commit -m "feat(chat): add skeleton bar CSS with shimmer animation"
```

---

### Task 8: 集成测试

**Files:**
- 无文件修改

- [ ] **Step 1: 启动全栈开发环境**

```bash
cd /home/fzyhccg/workspaceai/lingzhou-agent && ./deploy/manage.sh up
```

- [ ] **Step 2: 启动前端开发服务器**

```bash
cd /home/fzyhccg/workspaceai/lingzhou-agent/frontend && pnpm dev
```

- [ ] **Step 3: 手动测试**

1. 打开浏览器访问前端
2. 登录系统
3. 进入聊天界面
4. 点击侧边栏的会话历史记录
5. 观察：
   - 切换时消息区域应显示骨架屏
   - 骨架屏应显示真实头像（用户头像、AI头像）
   - 骨架条应有 shimmer 闪烁动画
   - 加载完成后骨架屏消失，显示真实消息

- [ ] **Step 4: 提交最终版本**

```bash
git add -A
git commit -m "feat(chat): complete skeleton loading for conversation switch"
```

---

## 自检清单

- [x] Spec 覆盖：所有设计文档中的需求都有对应任务
- [x] 无占位符：所有代码都是完整可执行的
- [x] 类型一致性：props 名称在 FrontChatWorkspace 和 ChatMessageStream 中一致
- [x] 错误处理：selectConversation 中使用 try-finally 确保 loading 状态被清除

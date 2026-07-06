<template>
    <div
        ref="chatWindow"
        class="chat-message-stream chat-message-stream--default custom-scrollbar flex-1 overflow-y-auto px-4 py-8 sm:px-6 lg:px-8"
        @scroll="handleScroll"
    >
        <transition name="skeleton-fade" mode="out-in" @after-enter="handleContentAfterEnter">
            <!-- 骨架屏 loading 状态 -->
            <div v-if="loading" key="skeleton" class="space-y-6 skeleton-container">
                <div
                    v-for="(skeleton, index) in skeletonMessages"
                    :key="index"
                    class="grid grid-cols-[auto_1fr_auto] gap-3 w-full items-start"
                >
                    <!-- 第一列：左头像（助手消息占位）或空占位（用户消息） -->
                    <div
                        v-if="skeleton.kind === 'assistant'"
                        class="skeleton-avatar h-8 w-8 shrink-0 rounded-full"
                    ></div>
                    <div v-else class="w-8 shrink-0"></div>

                    <!-- 第二列：骨架条 -->
                    <div
                        :class="[
                            'flex min-w-0 flex-col gap-2 p-3 rounded-2xl',
                            skeleton.kind === 'user'
                                ? 'items-end justify-self-end skeleton-bubble-user'
                                : 'items-start justify-self-start skeleton-bubble-assistant',
                        ]"
                    >
                        <div
                            v-for="(bar, barIndex) in skeleton.bars"
                            :key="barIndex"
                            class="skeleton-bar h-4 rounded-md"
                            :style="{ width: bar.width, minWidth: '120px' }"
                        ></div>
                    </div>

                    <!-- 第三列：右头像（用户消息占位）或空占位（助手消息） -->
                    <div
                        v-if="skeleton.kind === 'user'"
                        class="skeleton-avatar h-8 w-8 shrink-0 rounded-full"
                    ></div>
                    <div v-else class="w-8 shrink-0"></div>
                </div>
            </div>

            <!-- 正常消息列表 -->
            <div
                v-else
                key="messages"
                :class="visibleMessages.length === 0 ? 'flex min-h-full items-center' : 'space-y-6'"
            >
                <div
                    v-if="visibleMessages.length > 0 && (loadingOlder || hasOlderMessages)"
                    class="flex justify-center pb-2"
                >
                    <div
                        class="inline-flex h-8 items-center gap-2 rounded-full border border-border-soft/70 bg-surface-alt/80 px-3 text-xs font-medium text-muted"
                    >
                        <span
                            v-if="loadingOlder"
                            class="h-3 w-3 animate-spin rounded-full border-2 border-primary/15 border-t-primary"
                        ></span>
                        <span>{{ loadingOlder ? '正在加载更早消息' : '上滑加载更早消息' }}</span>
                    </div>
                </div>
                <div
                    v-if="visibleMessages.length === 0"
                    :class="[
                        splitView ? 'max-w-[34rem]' : 'max-w-[42rem]',
                        'mx-auto w-full py-20 text-center',
                    ]"
                >
                    <div
                        :class="[
                            'chat-empty-icon mx-auto mb-4',
                            assistantVisual.type === 'image'
                                ? 'chat-empty-icon--image'
                                : 'chat-empty-icon--default',
                        ]"
                    >
                        <img
                            v-if="assistantVisual.type === 'image'"
                            :src="assistantVisual.value"
                            alt="chat assistant icon"
                            class="chat-avatar-image h-full w-full rounded-full object-cover"
                        />
                        <span
                            v-else-if="assistantVisual.type === 'material'"
                            class="material-symbols-outlined text-2xl"
                        >
                            {{ assistantVisual.value }}
                        </span>
                        <span v-else class="text-2xl leading-none">{{
                            assistantVisual.value
                        }}</span>
                    </div>
                    <h3 class="text-[2rem] font-semibold tracking-[-0.03em] text-strong">
                        {{ emptyTitle }}
                    </h3>
                    <p class="mx-auto mt-3 max-w-[34rem] text-[15px] leading-7 text-body">
                        {{ emptyDescription }}
                    </p>
                    <div
                        v-if="normalizedWelcomeActions.length"
                        class="mx-auto mt-7 flex max-w-[40rem] flex-col items-center gap-2.5"
                    >
                        <button
                            v-for="(action, index) in normalizedWelcomeActions"
                            :key="action.key || index"
                            type="button"
                            class="chat-empty-action chat-empty-action--default inline-flex w-fit max-w-full items-center justify-center whitespace-normal break-words rounded-full px-6 py-2.5 text-center text-sm font-medium leading-6"
                            @click="$emit('welcome-action', action)"
                        >
                            {{ action.label }}
                        </button>
                    </div>
                </div>
                <div
                    v-for="(message, index) in visibleMessages"
                    :key="index"
                    class="grid grid-cols-[auto_1fr_auto] gap-3 w-full items-start"
                >
                    <!-- 第一列：左头像（助手消息）或空占位（用户消息） -->
                    <div
                        v-if="message.kind !== 'user'"
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

                    <!-- 第二列：消息内容区域 -->
                    <div
                        :class="[
                            'flex min-w-0 flex-col gap-1',
                            resolveMessageContentWidthClass(message),
                            message.kind === 'user'
                                ? 'items-end justify-self-end'
                                : 'items-start justify-self-start',
                        ]"
                    >
                        <template v-if="message.kind === 'user'">
                            <div
                                v-if="message.attachments && message.attachments.length"
                                class="flex flex-col items-end gap-2"
                            >
                                <div
                                    v-for="file in message.attachments"
                                    :key="file.id"
                                    class="inline-flex max-w-full items-center gap-3 rounded-[18px] border border-transparent bg-[#edf3fb] px-3 py-2 shadow-none"
                                >
                                    <div
                                        class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white text-primary"
                                    >
                                        <span class="material-symbols-outlined text-[20px]"
                                            >description</span
                                        >
                                    </div>
                                    <div class="min-w-0 text-left">
                                        <p class="truncate text-sm font-medium text-strong">
                                            {{ file.name }}
                                        </p>
                                        <p class="text-[11px] text-body">上传成功</p>
                                    </div>
                                    <button
                                        type="button"
                                        class="ml-1 inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-primary/65 transition hover:bg-white/70 hover:text-primary"
                                        title="预览"
                                        @click.stop="$emit('open-attachment-preview', file)"
                                    >
                                        <span class="material-symbols-outlined text-[20px]"
                                            >visibility</span
                                        >
                                    </button>
                                </div>
                            </div>
                            <div
                                class="chat-user-bubble chat-user-bubble--default max-w-full w-fit min-h-[44px] break-words px-4 py-3.5 text-sm leading-relaxed"
                            >
                                {{ message.text }}
                            </div>
                        </template>
                        <div
                            v-else-if="message.kind === 'assistant'"
                            :class="[
                                'chat-assistant-content min-w-0 space-y-2 bg-transparent px-0 py-0 text-sm leading-relaxed text-body',
                                hasFrontendRenderSegment(message)
                                    ? 'inline-block w-auto max-w-full'
                                    : 'w-full',
                            ]"
                        >
                            <div
                                v-if="shouldShowPendingPlaceholder(message)"
                                class="chat-pending-pill chat-pending-pill--default inline-flex items-center gap-2 rounded-full px-3 py-1.5"
                            >
                                <span
                                    class="h-3 w-3 animate-spin rounded-full border-2 border-primary/15 border-t-primary"
                                ></span>
                                <span class="text-sm font-medium">{{
                                    resolveAssistantPendingText(message)
                                }}</span>
                            </div>
                            <div
                                v-else-if="message.segments && message.segments.length"
                                class="grid gap-0"
                            >
                                <template
                                    v-for="(segment, segIndex) in getDisplaySegments(message)"
                                    :key="segment.key || segIndex"
                                >
                                    <div
                                        v-if="segment.type === 'tool' && segment.renderPayload"
                                        class="w-full min-w-0"
                                    >
                                        <ChatFrontendRenderBlock
                                            :payload="segment.renderPayload"
                                            @action="$emit('frontend-render-action', $event)"
                                        />
                                    </div>
                                    <!-- 工具调用分组 -->
                                    <div
                                        v-else-if="segment.type === 'tool-group'"
                                        :class="[
                                            'tool-group-container',
                                            { 'attached-top': segment.attachedTop },
                                        ]"
                                    >
                                        <button
                                            type="button"
                                            class="tool-group-header"
                                            @click="toggleToolGroup(segment)"
                                        >
                                            <span class="material-symbols-outlined tool-group-icon"
                                                >terminal</span
                                            >
                                            <span class="tool-group-summary">{{
                                                getToolGroupSummary(segment)
                                            }}</span>
                                            <span
                                                class="material-symbols-outlined tool-group-expand"
                                                :class="{ expanded: segment.open }"
                                                >chevron_right</span
                                            >
                                        </button>
                                        <div
                                            class="grid overflow-hidden transition-all duration-200 ease-out"
                                            :class="
                                                segment.open
                                                    ? 'grid-rows-[1fr] opacity-100'
                                                    : 'grid-rows-[0fr] opacity-0'
                                            "
                                        >
                                            <div class="overflow-hidden">
                                                <div class="tool-group-content">
                                                    <div
                                                        v-for="tool in segment.tools"
                                                        :key="tool.key"
                                                        class="tool-sequence-item"
                                                    >
                                                        <button
                                                            type="button"
                                                            class="tool-header"
                                                            @click="$emit('toggle-segment', tool)"
                                                        >
                                                            <span
                                                                v-if="tool.status === 'running'"
                                                                class="tool-icon tool-icon-running"
                                                            >
                                                                <span
                                                                    class="status-dot-animated"
                                                                ></span>
                                                            </span>
                                                            <span
                                                                v-else-if="
                                                                    tool.status === 'error' ||
                                                                    tool.status === 'failed'
                                                                "
                                                                class="material-symbols-outlined tool-icon tool-icon-error"
                                                                >error</span
                                                            >
                                                            <span
                                                                v-else
                                                                class="material-symbols-outlined tool-icon tool-icon-done"
                                                                >terminal</span
                                                            >
                                                            <span class="tool-info">
                                                                {{
                                                                    tool.actionLabel ||
                                                                    tool.displayName ||
                                                                    tool.name ||
                                                                    '工具调用'
                                                                }}
                                                            </span>
                                                            <span
                                                                v-if="tool.status === 'running'"
                                                                class="tool-status-text"
                                                                >执行中</span
                                                            >
                                                            <span
                                                                class="material-symbols-outlined expand-icon"
                                                                :class="{ expanded: tool.open }"
                                                                >chevron_right</span
                                                            >
                                                        </button>
                                                        <div
                                                            class="grid overflow-hidden transition-all duration-200 ease-out"
                                                            :class="
                                                                tool.open
                                                                    ? 'grid-rows-[1fr] opacity-100'
                                                                    : 'grid-rows-[0fr] opacity-0'
                                                            "
                                                        >
                                                            <div class="overflow-hidden">
                                                                <div class="tool-detail">
                                                                    <div class="detail-section">
                                                                        <button
                                                                            type="button"
                                                                            class="detail-toggle"
                                                                            @click.stop="
                                                                                toggleDetailSection(
                                                                                    tool,
                                                                                    'input'
                                                                                )
                                                                            "
                                                                        >
                                                                            <span
                                                                                class="material-symbols-outlined detail-toggle-icon"
                                                                                :class="{
                                                                                    expanded:
                                                                                        tool.inputExpanded,
                                                                                }"
                                                                                >chevron_right</span
                                                                            >
                                                                            参数
                                                                        </button>
                                                                        <div
                                                                            v-if="
                                                                                tool.inputExpanded
                                                                            "
                                                                            class="detail-content"
                                                                        >
                                                                            <pre
                                                                                v-if="
                                                                                    !isEmptyContent(
                                                                                        tool.inputText
                                                                                    )
                                                                                "
                                                                                class="detail-code"
                                                                                >{{
                                                                                    formatBlock(
                                                                                        tool.inputText
                                                                                    )
                                                                                }}</pre
                                                                            >
                                                                            <div
                                                                                v-else
                                                                                class="detail-empty"
                                                                            >
                                                                                无参数
                                                                            </div>
                                                                        </div>
                                                                    </div>
                                                                    <div class="detail-section">
                                                                        <button
                                                                            type="button"
                                                                            class="detail-toggle"
                                                                            @click.stop="
                                                                                toggleDetailSection(
                                                                                    tool,
                                                                                    'output'
                                                                                )
                                                                            "
                                                                        >
                                                                            <span
                                                                                class="material-symbols-outlined detail-toggle-icon"
                                                                                :class="{
                                                                                    expanded:
                                                                                        tool.outputExpanded,
                                                                                }"
                                                                                >chevron_right</span
                                                                            >
                                                                            输出
                                                                        </button>
                                                                        <div
                                                                            v-if="
                                                                                tool.outputExpanded
                                                                            "
                                                                            class="detail-content"
                                                                        >
                                                                            <template
                                                                                v-if="tool.response"
                                                                            >
                                                                                <pre
                                                                                    v-if="
                                                                                        !isEmptyContent(
                                                                                            tool.response
                                                                                        )
                                                                                    "
                                                                                    class="detail-code"
                                                                                    >{{
                                                                                        formatBlock(
                                                                                            tool.response
                                                                                        )
                                                                                    }}</pre
                                                                                >
                                                                                <div
                                                                                    v-else
                                                                                    class="detail-empty"
                                                                                >
                                                                                    无返回内容
                                                                                </div>
                                                                            </template>
                                                                            <div
                                                                                v-else
                                                                                class="detail-waiting"
                                                                            >
                                                                                <span
                                                                                    class="status-dot-animated"
                                                                                ></span>
                                                                                <span
                                                                                    >等待返回...</span
                                                                                >
                                                                            </div>
                                                                        </div>
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    <div
                                        v-else-if="segment.type === 'tool'"
                                        class="tool-sequence-item"
                                    >
                                        <button
                                            type="button"
                                            class="tool-header"
                                            @click="$emit('toggle-segment', segment)"
                                        >
                                            <span
                                                v-if="segment.status === 'running'"
                                                class="tool-icon tool-icon-running"
                                            >
                                                <span class="status-dot-animated"></span>
                                            </span>
                                            <span
                                                v-else-if="
                                                    segment.status === 'error' ||
                                                    segment.status === 'failed'
                                                "
                                                class="material-symbols-outlined tool-icon tool-icon-error"
                                                >error</span
                                            >
                                            <span
                                                v-else
                                                class="material-symbols-outlined tool-icon tool-icon-done"
                                                >terminal</span
                                            >
                                            <span class="tool-info">
                                                {{
                                                    segment.actionLabel ||
                                                    segment.displayName ||
                                                    segment.name ||
                                                    '工具调用'
                                                }}
                                            </span>
                                            <span
                                                v-if="segment.status === 'running'"
                                                class="tool-status-text"
                                                >执行中</span
                                            >
                                            <span
                                                class="material-symbols-outlined expand-icon"
                                                :class="{ expanded: segment.open }"
                                                >chevron_right</span
                                            >
                                        </button>
                                        <div
                                            class="grid overflow-hidden transition-all duration-200 ease-out"
                                            :class="
                                                segment.open
                                                    ? 'grid-rows-[1fr] opacity-100'
                                                    : 'grid-rows-[0fr] opacity-0'
                                            "
                                        >
                                            <div class="overflow-hidden">
                                                <div class="tool-detail">
                                                    <div class="detail-section">
                                                        <button
                                                            type="button"
                                                            class="detail-toggle"
                                                            @click.stop="
                                                                toggleDetailSection(
                                                                    segment,
                                                                    'input'
                                                                )
                                                            "
                                                        >
                                                            <span
                                                                class="material-symbols-outlined detail-toggle-icon"
                                                                :class="{
                                                                    expanded: segment.inputExpanded,
                                                                }"
                                                                >chevron_right</span
                                                            >
                                                            参数
                                                        </button>
                                                        <div
                                                            v-if="segment.inputExpanded"
                                                            class="detail-content"
                                                        >
                                                            <pre
                                                                v-if="
                                                                    !isEmptyContent(
                                                                        segment.inputText
                                                                    )
                                                                "
                                                                class="detail-code"
                                                                >{{
                                                                    formatBlock(segment.inputText)
                                                                }}</pre
                                                            >
                                                            <div v-else class="detail-empty">
                                                                无参数
                                                            </div>
                                                        </div>
                                                    </div>
                                                    <div class="detail-section">
                                                        <button
                                                            type="button"
                                                            class="detail-toggle"
                                                            @click.stop="
                                                                toggleDetailSection(
                                                                    segment,
                                                                    'output'
                                                                )
                                                            "
                                                        >
                                                            <span
                                                                class="material-symbols-outlined detail-toggle-icon"
                                                                :class="{
                                                                    expanded:
                                                                        segment.outputExpanded,
                                                                }"
                                                                >chevron_right</span
                                                            >
                                                            输出
                                                        </button>
                                                        <div
                                                            v-if="segment.outputExpanded"
                                                            class="detail-content"
                                                        >
                                                            <template v-if="segment.response">
                                                                <pre
                                                                    v-if="
                                                                        !isEmptyContent(
                                                                            segment.response
                                                                        )
                                                                    "
                                                                    class="detail-code"
                                                                    >{{
                                                                        formatBlock(
                                                                            segment.response
                                                                        )
                                                                    }}</pre
                                                                >
                                                                <div v-else class="detail-empty">
                                                                    无返回内容
                                                                </div>
                                                            </template>
                                                            <div v-else class="detail-waiting">
                                                                <span
                                                                    class="status-dot-animated"
                                                                ></span>
                                                                <span>等待返回...</span>
                                                            </div>
                                                        </div>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    <div
                                        v-else-if="segment.type === 'html'"
                                        class="min-w-0 cursor-pointer overflow-hidden chat-tool-card transition-colors"
                                        @click="$emit('open-html-preview', segment)"
                                    >
                                        <div class="chat-preview-inner">
                                            <div class="chat-preview-title-row text-strong">
                                                <span
                                                    class="material-symbols-outlined chat-preview-icon text-primary"
                                                    >code</span
                                                >
                                                <span>{{ segment.title || 'HTML 预览' }}</span>
                                            </div>
                                            <span class="text-xs text-body">{{
                                                segment.size
                                            }}</span>
                                        </div>
                                    </div>
                                    <div
                                        v-else-if="segment.type === 'artifact'"
                                        class="min-w-0 overflow-hidden chat-tool-card"
                                    >
                                        <div class="chat-preview-inner">
                                            <div class="chat-preview-title-row text-strong">
                                                <span
                                                    class="material-symbols-outlined chat-preview-icon text-success"
                                                    >preview</span
                                                >
                                                <span>{{
                                                    segment.fileName || segment.title || '文件产物'
                                                }}</span>
                                            </div>
                                            <div class="flex gap-1.5">
                                                <button
                                                    v-if="segment.previewable"
                                                    type="button"
                                                    class="chat-tool-btn px-2.5 py-1 text-xs font-medium transition-colors"
                                                    @click.stop="
                                                        $emit('open-html-preview', segment)
                                                    "
                                                >
                                                    预览
                                                </button>
                                                <a
                                                    v-if="segment.downloadUrl"
                                                    :href="segment.downloadUrl"
                                                    target="_blank"
                                                    rel="noopener noreferrer"
                                                    class="chat-tool-btn px-2.5 py-1 text-xs font-medium transition-colors"
                                                    @click.stop
                                                >
                                                    下载
                                                </a>
                                            </div>
                                        </div>
                                    </div>
                                    <template v-else-if="segment.type === 'citation'"></template>
                                    <div
                                        v-else-if="segment.type === 'fallback_notice'"
                                        class="mt-1"
                                    >
                                        <div
                                            class="inline-flex max-w-full items-center gap-1.5 rounded-full border border-warning/20 bg-warning/10 px-3 py-1.5 text-xs text-warning"
                                        >
                                            <span
                                                class="material-symbols-outlined shrink-0 text-base text-warning"
                                                >info</span
                                            >
                                            <span class="min-w-0 break-words">{{
                                                segment.text
                                            }}</span>
                                        </div>
                                    </div>
                                    <div
                                        v-else-if="hasRenderableText(segment.text)"
                                        class="chat-assistant-bubble chat-assistant-bubble--default min-h-[44px] w-full rounded-none px-0 py-0"
                                    >
                                        <div
                                            v-if="message.render === 'markdown'"
                                            class="chat-markdown"
                                            @click="handleMarkdownClick($event, message)"
                                            v-html="
                                                renderMarkdownWithCitations(segment.text, message)
                                            "
                                        ></div>
                                        <p v-else>{{ segment.text }}</p>
                                    </div>
                                </template>
                            </div>
                            <div
                                v-if="hasRenderableText(message.text)"
                                class="chat-assistant-bubble chat-assistant-bubble--default min-h-[44px] w-full rounded-none px-0 py-0"
                            >
                                <div
                                    v-if="message.render === 'markdown'"
                                    class="chat-markdown"
                                    @click="handleMarkdownClick($event, message)"
                                    v-html="renderMarkdownWithCitations(message.text, message)"
                                ></div>
                                <p v-else>{{ message.text }}</p>
                            </div>
                        </div>
                        <span
                            v-if="
                                message.kind !== 'assistant' ||
                                !message.pending ||
                                shouldShowPendingPlaceholder(message)
                            "
                            class="text-xs text-muted"
                            >{{ message.time }}</span
                        >
                        <span v-else class="inline-flex items-center gap-1.5 text-xs text-primary">
                            <span
                                class="h-3 w-3 animate-spin rounded-full border-2 border-primary/15 border-t-primary"
                            ></span>
                            {{ resolveAssistantPendingText(message) }}
                        </span>
                    </div>

                    <!-- 第三列：右头像（用户消息）或空占位（助手消息） -->
                    <div
                        v-if="message.kind === 'user'"
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
        </transition>
    </div>
</template>

<script>
import { marked } from 'marked';
import { isImageSource, isMaterialSymbolName } from '@/utils/iconDisplay';
import ChatFrontendRenderBlock from './ChatFrontendRenderBlock.vue';
import ChatToolGroup from '@/components/chat/ChatToolGroup.vue';
import ChatPreviewCard from '@/components/chat/ChatPreviewCard.vue';

marked.setOptions({
    breaks: true,
    gfm: true,
});

export default {
    name: 'ChatMessageStream',
    components: {
        ChatFrontendRenderBlock,
        ChatToolGroup,
        ChatPreviewCard,
    },
    emits: [
        'toggle-segment',
        'open-html-preview',
        'open-attachment-preview',
        'open-citation',
        'frontend-render-action',
        'welcome-action',
        'load-older',
    ],
    props: {
        messages: {
            type: Array,
            default: () => [],
        },
        scrollToken: {
            type: Number,
            default: 0,
        },
        shouldAutoScroll: {
            type: Boolean,
            default: true,
        },
        emptyTitle: {
            type: String,
            default: '',
        },
        emptyDescription: {
            type: String,
            default: '',
        },
        emptyIcon: {
            type: String,
            default: 'inventory_2',
        },
        assistantAvatarUrl: {
            type: String,
            default: '',
        },
        assistantIcon: {
            type: String,
            default: 'psychology',
        },
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
        splitView: {
            type: Boolean,
            default: false,
        },
        loadingOlder: {
            type: Boolean,
            default: false,
        },
        hasOlderMessages: {
            type: Boolean,
            default: false,
        },
    },
    data() {
        return {
            pendingScrollRestore: null,
            scrollBottomTimers: [],
        };
    },
    methods: {
        isImageIcon(icon) {
            return isImageSource(icon);
        },
        isMaterialIcon(icon) {
            return isMaterialSymbolName(icon);
        },
        hasRenderableText(value) {
            return String(value ?? '').trim().length > 0;
        },
        isVisibleMessage(message) {
            const messageType = String(message?.messageType || 'normal')
                .trim()
                .toLowerCase();
            return messageType !== 'event';
        },
        hasFrontendRenderSegment(message) {
            if (!message || !Array.isArray(message.segments)) {
                return false;
            }
            return message.segments.some(
                segment => segment?.type === 'tool' && Boolean(segment?.renderPayload)
            );
        },
        hasAssistantRenderableContent(message) {
            if (!message) {
                return false;
            }
            if (this.hasRenderableText(message.text)) {
                return true;
            }
            if (!Array.isArray(message.segments) || message.segments.length === 0) {
                return false;
            }
            return message.segments.some(segment => {
                if (!segment) {
                    return false;
                }
                if (
                    segment.type === 'tool' ||
                    segment.type === 'artifact' ||
                    segment.type === 'citation'
                ) {
                    return true;
                }
                return this.hasRenderableText(segment.text);
            });
        },
        shouldShowPendingPlaceholder(message) {
            return (
                message?.kind === 'assistant' &&
                Boolean(message?.pending) &&
                !this.hasAssistantRenderableContent(message)
            );
        },
        resolveAssistantPendingText(message) {
            return this.hasAssistantRenderableContent(message) ? '正在输入中' : '正在思考';
        },
        getDisplaySegments(message) {
            if (!message || !Array.isArray(message.segments)) {
                return [];
            }
            // 过滤 null/undefined，保持原始顺序
            const segments = message.segments.filter(segment => segment != null);
            const result = [];
            let toolGroup = null;
            let toolGroupStartIndex = -1;

            const flushToolGroup = () => {
                if (toolGroup && toolGroup.tools.length >= 1) {
                    if (toolGroup.tools.length === 1) {
                        // 单个工具直接输出，不分组（避免嵌套折叠：点开分组又要点开工具）
                        result.push(toolGroup.tools[0]);
                    } else {
                        // 多个工具才分组
                        toolGroup.key = `tool-group-${toolGroupStartIndex}`;
                        if (toolGroup.tools[0].groupOpen !== undefined) {
                            toolGroup.open = toolGroup.tools[0].groupOpen;
                        }
                        result.push(toolGroup);
                    }
                }
                toolGroup = null;
                toolGroupStartIndex = -1;
            };

            segments.forEach((segment, index) => {
                // 只对没有 renderPayload 的 tool 进行分组
                if (segment.type === 'tool' && !segment.renderPayload) {
                    if (!toolGroup) {
                        toolGroup = {
                            type: 'tool-group',
                            tools: [],
                            open: false,
                        };
                        toolGroupStartIndex = index;
                    }
                    toolGroup.tools.push(segment);
                } else {
                    // 遇到非 tool segment，先保存之前的 group
                    flushToolGroup();
                    result.push(segment);
                }
            });

            // 保存最后的 group
            flushToolGroup();

            // 动态贴合处理：判断前一块类型
            for (let i = 0; i < result.length; i++) {
                const current = result[i];
                const prev = result[i - 1];

                if (prev && this.isTextSegment(prev) && current.type === 'tool-group') {
                    // 文本块后接工具组：贴合显示
                    prev.attachedBottom = true;
                    current.attachedTop = true;
                }
            }

            return result;
        },
        isTextSegment(segment) {
            // 判断是否是文本类型的 segment
            if (!segment) return false;
            const type = segment.type;
            // 文本块的 type 不是 tool、html、artifact、citation、fallback_notice
            return !['tool', 'html', 'artifact', 'citation', 'fallback_notice'].includes(type);
        },
        getToolGroupSummary(group) {
            if (!group || !group.tools || group.tools.length === 0) {
                return '';
            }
            const total = group.tools.length;
            const running = group.tools.filter(t => t.status === 'running').length;
            const done = group.tools.filter(
                t => t.status === 'done' || t.status === 'completed'
            ).length;
            const error = group.tools.filter(
                t => t.status === 'error' || t.status === 'failed'
            ).length;
            const justCompleted = group.tools.filter(t => t.justCompleted).length;

            // 单个工具显示工具名
            if (total === 1) {
                const tool = group.tools[0];
                const toolName = tool.actionLabel || tool.displayName || tool.name || '工具调用';
                if (running > 0) {
                    return `${toolName} · 执行中`;
                }
                if (error > 0) {
                    return `${toolName} · 失败`;
                }
                if (tool.justCompleted) {
                    return `${toolName} · 刚完成`;
                }
                return toolName;
            }

            // 多个工具显示摘要
            if (running > 0) {
                return `${total} 个工具调用 · ${done} 完成 ${running} 执行中`;
            }
            if (error > 0) {
                return `${total} 个工具调用 · ${done} 完成 ${error} 失败`;
            }
            if (justCompleted > 0) {
                return `${total} 个工具调用 · 刚完成`;
            }
            return `${total} 个工具调用`;
        },
        toggleToolGroup(group) {
            group.open = !group.open;
            // 将展开状态持久化到第一个 tool 上
            if (group.tools && group.tools.length > 0) {
                group.tools[0].groupOpen = group.open;
            }
            this.touchMessages();
        },
        resolveMessageContentWidthClass(message) {
            // Grid 布局下，内容列占 1fr
            // 不设置最大宽度限制，让泡泡根据内容自适应
            if (message?.kind === 'user') {
                // 右侧用户消息
                return '';
            }
            if (this.hasFrontendRenderSegment(message)) {
                // 前端渲染段（工具卡片等）可以更宽
                return 'max-w-[85%]';
            }
            // 左侧助手消息
            return '';
        },
        renderMarkdown(text) {
            return marked.parse(text || '');
        },
        renderMarkdownWithCitations(text, message) {
            const citationMap = this.buildCitationMap(message);
            const rendered = this.wrapMarkdownTables(this.renderMarkdown(String(text || '')));
            return this.decorateRenderedHtml(rendered, citationMap);
        },
        wrapMarkdownTables(renderedHtml) {
            if (!renderedHtml || typeof DOMParser === 'undefined') {
                return renderedHtml;
            }
            const documentNode = new DOMParser().parseFromString(
                `<div>${renderedHtml}</div>`,
                'text/html'
            );
            const root = documentNode.body.firstElementChild;
            if (!root) {
                return renderedHtml;
            }
            documentNode.querySelectorAll('table').forEach(table => {
                const wrapper = documentNode.createElement('div');
                wrapper.className = 'chat-markdown-table-scroll';
                table.parentNode?.insertBefore(wrapper, table);
                wrapper.appendChild(table);
            });
            return root.innerHTML;
        },
        buildCitationMap(message) {
            const map = new Map();
            if (!message || !Array.isArray(message.segments)) {
                return map;
            }
            message.segments.forEach(segment => {
                if (!segment || segment.type !== 'citation') {
                    return;
                }
                const key = String(segment.ref ?? '').trim();
                if (!key || map.has(key)) {
                    return;
                }
                map.set(key, segment);
            });
            return map;
        },
        decorateRenderedHtml(renderedHtml, citationMap) {
            if (!renderedHtml || !(citationMap instanceof Map) || citationMap.size === 0) {
                return renderedHtml;
            }
            if (typeof window === 'undefined' || typeof DOMParser === 'undefined') {
                return renderedHtml;
            }
            const parser = new DOMParser();
            const documentNode = parser.parseFromString(`<div>${renderedHtml}</div>`, 'text/html');
            const root = documentNode.body.firstElementChild;
            if (!root) {
                return renderedHtml;
            }
            const nodeFilter = window.NodeFilter || NodeFilter;
            const walker = documentNode.createTreeWalker(root, nodeFilter.SHOW_TEXT);
            const textNodes = [];
            let current = walker.nextNode();
            while (current) {
                if (this.shouldDecorateCitationText(current.parentElement)) {
                    textNodes.push(current);
                }
                current = walker.nextNode();
            }
            textNodes.forEach(node => {
                const content = node.textContent || '';
                if (!/\[(\d+)\]/.test(content)) {
                    return;
                }
                const fragment = documentNode.createDocumentFragment();
                let lastIndex = 0;
                content.replace(/\[(\d+)\]/g, (matched, refText, offset) => {
                    if (offset > lastIndex) {
                        fragment.appendChild(
                            documentNode.createTextNode(content.slice(lastIndex, offset))
                        );
                    }
                    fragment.appendChild(
                        this.createCitationNode(documentNode, refText, citationMap)
                    );
                    lastIndex = offset + matched.length;
                    return matched;
                });
                if (lastIndex < content.length) {
                    fragment.appendChild(documentNode.createTextNode(content.slice(lastIndex)));
                }
                node.parentNode?.replaceChild(fragment, node);
            });
            return root.innerHTML;
        },
        shouldDecorateCitationText(element) {
            if (!element || typeof element.closest !== 'function') {
                return false;
            }
            return !element.closest('pre, code, a, button');
        },
        createCitationNode(documentNode, refText, citationMap) {
            const key = String(refText || '').trim();
            if (!key) {
                return documentNode.createTextNode('');
            }
            if (!citationMap.has(key)) {
                const badge = documentNode.createElement('span');
                badge.className =
                    'mx-0.5 inline-flex items-center rounded-full border border-border-soft bg-surface-alt px-1.5 py-0.5 text-[11px] font-semibold text-muted';
                badge.textContent = `[${key}]`;
                return badge;
            }
            const button = documentNode.createElement('button');
            button.type = 'button';
            button.setAttribute('data-citation-ref', key);
            button.className =
                'mx-0.5 inline-flex cursor-pointer items-center rounded-full border border-primary/20 bg-accent-soft px-1.5 py-0.5 text-[11px] font-semibold text-primary transition hover:bg-accent-soft/80';
            button.textContent = `[${key}]`;
            return button;
        },
        handleMarkdownClick(event, message) {
            const target = event?.target?.closest?.('[data-citation-ref]');
            if (!target) {
                return;
            }
            const key = String(target.getAttribute('data-citation-ref') || '').trim();
            if (!key) {
                return;
            }
            const citation = this.buildCitationMap(message).get(key);
            if (!citation) {
                return;
            }
            this.$emit('open-citation', citation);
        },
        formatBlock(value) {
            if (!value) {
                return '';
            }
            if (typeof value === 'string') {
                return value;
            }
            return JSON.stringify(value, null, 2);
        },
        /**
         * 判断内容是否为空（空值、空字符串、空对象）
         */
        isEmptyContent(value) {
            if (!value) {
                return true;
            }
            if (typeof value === 'string') {
                // 空字符串或只有空白字符或只有 "{}"
                const trimmed = value.trim();
                return trimmed === '' || trimmed === '{}';
            }
            if (typeof value === 'object') {
                // 空对象
                return Object.keys(value).length === 0;
            }
            return false;
        },
        getContentExpandLevel(segment, field) {
            const key = `${field}ExpandLevel`;
            return segment[key] ?? 0;
        },
        shouldShowExpandBtn(segment, field) {
            const text =
                field === 'input'
                    ? this.formatBlock(segment.inputText)
                    : this.formatBlock(segment.response);
            return text.length > 150;
        },
        getContentExpandClass(segment, field) {
            const level = this.getContentExpandLevel(segment, field);
            if (level === 0) return 'max-h-20';
            if (level === 1) return 'max-h-60';
            return '';
        },
        getContentExpandLabel(segment, field) {
            const level = this.getContentExpandLevel(segment, field);
            if (level === 0) return '展开更多';
            if (level === 1) return '全部展开';
            return '收起';
        },
        getContentExpandIcon(segment, field) {
            const level = this.getContentExpandLevel(segment, field);
            if (level === 0) return 'unfold_more';
            if (level === 1) return 'unfold_more';
            return 'unfold_less';
        },
        toggleContentExpand(segment, field) {
            const key = `${field}ExpandLevel`;
            const current = segment[key] ?? 0;
            const next = (current + 1) % 3;
            segment[key] = next;
            this.touchMessages();
        },
        toggleDetailSection(segment, field) {
            const key = `${field}Expanded`;
            segment[key] = !segment[key];
            this.touchMessages();
        },
        touchMessages() {
            this.$forceUpdate();
        },
        handleScroll(event) {
            const node = event?.target || this.$refs.chatWindow;
            if (
                !node ||
                this.loading ||
                this.loadingOlder ||
                !this.hasOlderMessages ||
                this.visibleMessages.length === 0
            ) {
                return;
            }
            const threshold = 80;
            if (node.scrollTop > threshold) {
                return;
            }
            this.pendingScrollRestore = {
                scrollHeight: node.scrollHeight,
                scrollTop: node.scrollTop,
            };
            this.$emit('load-older');
        },
        restoreScrollAfterOlderLoad() {
            const previous = this.pendingScrollRestore;
            if (!previous || this.loadingOlder) {
                return;
            }
            this.pendingScrollRestore = null;
            this.$nextTick(() => {
                const node = this.$refs.chatWindow;
                if (!node) {
                    return;
                }
                const delta = node.scrollHeight - previous.scrollHeight;
                node.scrollTop = previous.scrollTop + Math.max(0, delta);
            });
        },
        handleContentAfterEnter() {
            if (this.shouldAutoScroll && !this.loadingOlder) {
                this.scheduleScrollToBottom(true);
            }
        },
        clearScheduledScrolls() {
            this.scrollBottomTimers.forEach(timer => window.clearTimeout(timer));
            this.scrollBottomTimers = [];
        },
        scheduleScrollToBottom(force = false) {
            if (!this.shouldAutoScroll || this.loadingOlder) {
                return;
            }
            this.clearScheduledScrolls();
            [0, 60, 180, 360].forEach(delay => {
                const timer = window.setTimeout(() => {
                    this.scrollChatToBottom(force);
                }, delay);
                this.scrollBottomTimers.push(timer);
            });
        },
        scrollChatToBottom(force = false) {
            this.$nextTick(() => {
                const node = this.$refs.chatWindow;
                if (!node || !this.shouldAutoScroll) {
                    return;
                }
                if (force) {
                    node.scrollTop = node.scrollHeight;
                    return;
                }
                const threshold = 80;
                const nearBottom =
                    node.scrollTop + node.clientHeight >= node.scrollHeight - threshold;
                if (nearBottom) {
                    node.scrollTop = node.scrollHeight;
                }
            });
        },
    },
    computed: {
        normalizedWelcomeActions() {
            const supportedActionTypes = ['send_prompt', 'open_insight'];
            return (Array.isArray(this.welcomeActions) ? this.welcomeActions : [])
                .map((action, index) => {
                    const label = String(action?.label || '').trim();
                    const actionType = String(action?.actionType || '')
                        .trim()
                        .toLowerCase();
                    if (!label || !supportedActionTypes.includes(actionType)) {
                        return null;
                    }
                    return {
                        ...action,
                        actionType,
                        label,
                        key: action?.key || `${actionType}-${label}-${index}`,
                    };
                })
                .filter(Boolean);
        },
        visibleMessages() {
            return Array.isArray(this.messages)
                ? this.messages.filter(message => this.isVisibleMessage(message))
                : [];
        },
        assistantVisual() {
            if (this.isImageIcon(this.assistantAvatarUrl)) {
                return {
                    type: 'image',
                    value: this.assistantAvatarUrl,
                };
            }

            const primaryIcon = String(this.assistantIcon || '').trim();
            const fallbackIcon = String(this.emptyIcon || '').trim();
            const genericIcons = new Set(['psychology', 'smart_toy']);
            const preferredIcon =
                primaryIcon && !genericIcons.has(primaryIcon)
                    ? primaryIcon
                    : fallbackIcon || primaryIcon;

            if (this.isImageIcon(preferredIcon)) {
                return {
                    type: 'image',
                    value: preferredIcon,
                };
            }
            if (this.isMaterialIcon(preferredIcon)) {
                return {
                    type: 'material',
                    value: preferredIcon,
                };
            }
            return {
                type: 'emoji',
                value: preferredIcon || '🤖',
            };
        },
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
    },
    watch: {
        scrollToken() {
            this.scheduleScrollToBottom(true);
        },
        loadingOlder(value, previous) {
            if (previous && !value) {
                this.restoreScrollAfterOlderLoad();
            }
        },
        loading(value, previous) {
            if (previous && !value && this.shouldAutoScroll) {
                this.scheduleScrollToBottom(true);
            }
        },
    },
    updated() {
        if (!this.loadingOlder) {
            this.scrollChatToBottom();
        }
    },
    beforeUnmount() {
        this.clearScheduledScrolls();
    },
};
</script>

<style scoped>
.chat-message-stream {
    position: relative;
}

.chat-empty-icon,
.chat-assistant-avatar {
    background: linear-gradient(145deg, rgb(var(--color-text-strong)), rgb(var(--color-accent)));
    box-shadow:
        0 16px 30px -22px rgb(var(--color-text-strong) / 0.52),
        inset 0 1px 0 rgb(255 255 255 / 0.24);
}

.chat-empty-icon {
    display: flex;
    height: 4rem;
    width: 4rem;
    align-items: center;
    justify-content: center;
    border-radius: 9999px;
    color: white;
}

.chat-empty-icon--image {
    overflow: hidden;
    background: transparent;
    box-shadow: none;
}

.chat-empty-icon--default {
    background: #f0f4f9;
    box-shadow: none;
    color: rgb(var(--color-text-strong));
}

.chat-avatar-image {
    display: block;
}

.chat-user-avatar {
    box-shadow: inset 0 0 0 1px rgba(226, 232, 240, 0.9);
}

.chat-empty-action {
    border: 1px solid #e7ebef;
    background: #ffffff;
    color: rgb(var(--color-text-body));
    box-shadow: 0 12px 24px -24px rgba(15, 23, 42, 0.2);
}

.chat-empty-action:hover {
    border-color: #d9e1ea;
    background: #f7f9fb;
    color: rgb(var(--color-text-strong));
}

.chat-empty-action--default {
    border-color: transparent;
    background: #f0f4f9;
    box-shadow: none;
    color: rgb(var(--color-text-strong));
}

.chat-empty-action--default:hover {
    border-color: transparent;
    background: #e8eef7;
}

.chat-user-bubble {
    border: 1px solid rgb(var(--color-accent) / 0.12);
    border-radius: 1.25rem;
    background: linear-gradient(
        180deg,
        rgb(var(--color-accent-soft) / 0.78),
        rgb(var(--color-accent-soft) / 0.58)
    );
    color: rgb(var(--color-text-strong));
    box-shadow: 0 14px 28px -24px rgb(var(--color-accent) / 0.4);
}

.chat-user-bubble--default {
    border-color: transparent;
    background: #edf3fb;
    box-shadow: none;
}

.chat-assistant-content {
    color: rgb(var(--color-text-body));
}

.chat-message-stream--default .chat-assistant-content,
.chat-message-stream--default .chat-markdown {
    color: rgb(var(--color-text-strong));
}

.chat-assistant-bubble {
    border: 1px solid rgb(var(--color-border-soft) / 0.72);
    background: linear-gradient(
        180deg,
        rgb(var(--color-bg-surface) / 0.92),
        rgb(var(--color-bg-surface) / 0.78)
    );
    box-shadow: 0 16px 28px -26px rgba(15, 23, 42, 0.22);
}

.chat-assistant-bubble--default {
    border: 0;
    background: transparent;
    box-shadow: none;
}

.chat-pending-pill {
    border: 1px solid rgb(var(--color-accent) / 0.1);
    background: rgb(var(--color-bg-surface) / 0.88);
    color: rgb(var(--color-accent));
}

.chat-pending-pill--default {
    border-color: transparent;
    background: #eef3f8;
    color: rgb(var(--color-text-body));
}

.chat-markdown {
    width: 100%;
    max-width: 100%;
    min-width: 0;
    overflow-x: hidden;
    color: rgb(var(--color-text-body));
    line-height: 1.7;
    word-break: break-word;
}

.chat-markdown :deep(> *:first-child) {
    margin-top: 0;
}

.chat-markdown :deep(p) {
    margin-top: 0.5rem;
}

.chat-markdown :deep(h1),
.chat-markdown :deep(h2),
.chat-markdown :deep(h3),
.chat-markdown :deep(h4) {
    margin-top: 1rem;
    font-weight: 600;
    line-height: 1.4;
    color: rgb(var(--color-text-strong));
}

.chat-markdown :deep(h1) {
    font-size: 1.25rem;
}

.chat-markdown :deep(h2) {
    font-size: 1.125rem;
}

.chat-markdown :deep(h3),
.chat-markdown :deep(h4) {
    font-size: 1rem;
}

.chat-markdown :deep(ul),
.chat-markdown :deep(ol) {
    margin: 0.5rem 0 0.5rem 1.25rem;
    padding-left: 0.5rem;
}

.chat-markdown :deep(ul) {
    list-style: disc;
}

.chat-markdown :deep(ol) {
    list-style: decimal;
}

.chat-markdown :deep(li) {
    margin-top: 0.25rem;
}

.chat-markdown :deep(li > p) {
    margin-top: 0.25rem;
}

.chat-markdown :deep(blockquote) {
    margin-top: 0.75rem;
    border-left: 3px solid rgb(var(--color-accent) / 0.48);
    background: rgb(var(--color-accent-soft) / 0.48);
    padding: 0.5rem 0.75rem;
    color: rgb(var(--color-text-body));
    border-radius: 0 0.5rem 0.5rem 0;
}

.chat-markdown :deep(hr) {
    margin: 1rem 0;
    border: 0;
    border-top: 1px solid rgb(var(--color-border-soft) / 0.8);
}

.chat-markdown :deep(a) {
    color: rgb(var(--color-accent));
    text-decoration: underline;
    text-decoration-thickness: 1px;
    text-underline-offset: 0.15em;
}

.chat-markdown :deep(code) {
    border: 1px solid rgb(var(--color-border-soft) / 0.78);
    background: rgb(var(--color-bg-surface-alt) / 0.88);
    border-radius: 0.25rem;
    padding: 0.125rem 0.375rem;
    font-size: 0.875em;
    color: rgb(var(--color-accent));
}

.chat-markdown :deep(pre) {
    margin-top: 0.75rem;
    max-width: 100%;
    overflow-x: auto;
    border: 1px solid rgba(15, 23, 42, 0.86);
    background: rgba(15, 23, 42, 0.96);
    color: #e2e8f0;
    border-radius: 0.5rem;
    padding: 0.75rem 1rem;
}

.chat-markdown :deep(pre code) {
    white-space: pre;
    border: 0;
    background: transparent;
    color: inherit;
    padding: 0;
    font-size: 0.875em;
}

.chat-markdown :deep(table) {
    width: 100%;
    min-width: 100%;
    max-width: 100%;
    margin-top: 0.75rem;
    border-collapse: collapse;
    border-radius: 0.5rem;
    border: 1px solid rgb(var(--color-border-soft) / 0.82);
    background: rgb(var(--color-bg-surface) / 0.96);
    table-layout: auto;
}

.chat-markdown :deep(.chat-markdown-table-scroll) {
    display: block;
    width: 100%;
    max-width: 100%;
    overflow-x: auto;
    overflow-y: hidden;
    -webkit-overflow-scrolling: touch;
}

.chat-markdown :deep(th),
.chat-markdown :deep(td) {
    border-bottom: 1px solid rgb(var(--color-border-soft) / 0.76);
    padding: 0.5rem 0.75rem;
    text-align: left;
    vertical-align: top;
    min-width: 0;
    white-space: normal;
    overflow-wrap: anywhere;
    word-break: break-word;
}

.chat-markdown :deep(th) {
    background: rgb(var(--color-bg-surface-alt) / 0.84);
    font-weight: 600;
    color: rgb(var(--color-text-strong));
}

.chat-markdown :deep(tr:last-child td) {
    border-bottom: 0;
}

.chat-markdown :deep(img) {
    max-width: 100%;
    border-radius: 0.5rem;
    margin-top: 0.75rem;
}

.code-block-wrapper {
    position: relative;
}

.code-block {
    overflow: auto;
    white-space: pre-wrap;
    word-break: break-word;
    border-radius: 0.375rem;
    background: rgb(var(--color-bg-surface-alt) / 0.78);
    padding: 0.625rem 0.875rem;
    font-size: 0.8125rem;
    color: rgb(var(--color-text-body));
    line-height: 1.65;
    transition: max-height 0.2s ease-out;
}

.code-block::-webkit-scrollbar {
    width: 5px;
    height: 5px;
}

.code-block::-webkit-scrollbar-track {
    background: transparent;
}

.code-block::-webkit-scrollbar-thumb {
    background: rgb(var(--color-border-strong) / 0.78);
    border-radius: 3px;
}

.code-block::-webkit-scrollbar-thumb:hover {
    background: rgb(var(--color-border-strong) / 1);
}

.expand-btn {
    display: inline-flex;
    align-items: center;
    gap: 0.375rem;
    margin-top: 0.375rem;
    padding: 0.25rem 0.625rem;
    border-radius: 0.25rem;
    background: rgb(var(--color-bg-surface-alt) / 0.84);
    color: rgb(var(--color-text-body));
    font-size: 0.75rem;
    font-weight: 500;
    cursor: pointer;
    transition: background 0.15s ease;
}

.expand-btn:hover {
    background: rgb(var(--color-accent-soft) / 0.52);
}

/* 骨架屏过渡动画 */
.skeleton-fade-enter-active,
.skeleton-fade-leave-active {
    transition: opacity 0.15s ease;
}

.skeleton-fade-enter-from,
.skeleton-fade-leave-to {
    opacity: 0;
}

.skeleton-container {
    animation: skeleton-pulse 0.2s ease-out;
}

@keyframes skeleton-pulse {
    0% {
        opacity: 0;
        transform: scale(0.98);
    }
    100% {
        opacity: 1;
        transform: scale(1);
    }
}

/* 骨架屏 shimmer 动画 */
@keyframes shimmer {
    0% {
        background-position: -200% 0;
    }
    100% {
        background-position: 200% 0;
    }
}

/* 骨架头像占位 */
.skeleton-avatar {
    background: linear-gradient(90deg, #e2e8f0 25%, #f1f5f9 50%, #e2e8f0 75%);
    background-size: 200% 100%;
    animation: shimmer 1.2s infinite linear;
}

/* 骨架消息气泡 - 用户（模拟真实用户气泡 #edf3fb） */
.skeleton-bubble-user {
    background: linear-gradient(90deg, #edf3fb 25%, #e5ebf5 50%, #edf3fb 75%);
    background-size: 200% 100%;
    animation: shimmer 1.2s infinite linear;
    border-radius: 1.25rem;
    min-width: 180px;
    max-width: 70%;
}

/* 骨架消息气泡 - 助手（透明背景，模拟真实助手消息） */
.skeleton-bubble-assistant {
    background: transparent;
    min-width: 240px;
    max-width: 75%;
}

.skeleton-bar {
    background: linear-gradient(90deg, #e2e8f0 25%, #f1f5f9 50%, #e2e8f0 75%);
    background-size: 200% 100%;
    animation: shimmer 1.2s infinite linear;
    border-radius: 0.25rem;
}
</style>

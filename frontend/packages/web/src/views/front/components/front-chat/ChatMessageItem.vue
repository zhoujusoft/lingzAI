<template>
    <div
        :class="[
            splitView
                ? 'flex w-full min-w-0 gap-3'
                : 'mx-auto flex w-full max-w-[800px] min-w-0 gap-3',
            message.kind === 'user' ? 'justify-end' : 'justify-start',
        ]"
    >
        <!-- Assistant Avatar -->
        <div
            v-if="message.kind !== 'user'"
            class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-blue-500 to-blue-600 text-white shadow-sm"
        >
            <span class="material-symbols-outlined text-lg">psychology</span>
        </div>

        <div
            :class="[
                'flex min-w-0 flex-col gap-1',
                resolveMessageContentWidthClass(message),
                message.kind === 'user' ? 'items-end' : 'items-start',
            ]"
        >
            <!-- User Message Content -->
            <template v-if="message.kind === 'user'">
                <div
                    v-if="message.attachments && message.attachments.length"
                    class="flex flex-col items-end gap-2"
                >
                    <div
                        v-for="file in message.attachments"
                        :key="file.id"
                        class="inline-flex max-w-full items-center gap-3 rounded-xl border border-blue-200 bg-blue-50 px-3 py-2 shadow-sm"
                    >
                        <div
                            class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-blue-100 text-blue-600"
                        >
                            <span class="material-symbols-outlined text-[20px]">description</span>
                        </div>
                        <div class="min-w-0 text-left">
                            <p class="truncate text-sm font-medium text-blue-700">
                                {{ file.name }}
                            </p>
                            <p class="text-[11px] text-blue-500">上传成功</p>
                        </div>
                        <button
                            type="button"
                            class="ml-1 inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-blue-400 transition hover:bg-white/70 hover:text-blue-600"
                            title="预览"
                            @click.stop="$emit('open-attachment-preview', file)"
                        >
                            <span class="material-symbols-outlined text-[20px]">visibility</span>
                        </button>
                    </div>
                </div>
                <div
                    class="min-h-[44px] w-fit break-words rounded-2xl bg-blue-100 px-4 py-3 text-sm leading-relaxed text-blue-800 ml-auto max-w-full"
                >
                    {{ message.text }}
                </div>
            </template>

            <!-- Assistant Message Content -->
            <div
                v-else-if="message.kind === 'assistant'"
                :class="[
                    'min-w-0 space-y-2 bg-transparent px-0 py-0 text-sm leading-relaxed text-slate-700',
                    hasFrontendRenderSegment(message) ? 'inline-block w-auto max-w-full' : 'w-full',
                ]"
            >
                <!-- Loading Placeholder -->
                <div
                    v-if="shouldShowPendingPlaceholder(message)"
                    class="inline-flex items-center gap-2 rounded-full bg-slate-100 px-3 py-1.5 text-slate-600"
                >
                    <span
                        class="h-3 w-3 animate-spin rounded-full border-2 border-slate-200 border-t-blue-500"
                    ></span>
                    <span class="text-sm font-medium">{{
                        resolveAssistantPendingText(message)
                    }}</span>
                </div>

                <!-- Segments Rendering -->
                <div
                    v-else-if="message.segments && message.segments.length"
                    class="grid min-w-0 max-w-full grid-cols-1 gap-0"
                >
                    <template
                        v-for="(segment, segIndex) in displaySegments"
                        :key="segment.key || segIndex"
                    >
                        <!-- Frontend Render Block (e.g. Artifacts) -->
                        <div
                            v-if="segment.type === 'tool' && segment.renderPayload"
                            class="w-full min-w-0"
                        >
                            <ChatFrontendRenderBlock
                                :payload="segment.renderPayload"
                                @action="$emit('frontend-render-action', $event)"
                            />
                        </div>

                        <!-- Tool Group (Merged Tool Calls) -->
                        <div
                            v-else-if="segment.type === 'tool-group'"
                            :class="[
                                'tool-group-container',
                                'chat-tool-card',
                                { 'attached-top': segment.attachedTop },
                            ]"
                        >
                            <button
                                type="button"
                                class="tool-group-header"
                                @click="toggleLocalOpen(segment.key, segment.open)"
                            >
                                <span class="material-symbols-outlined tool-group-icon"
                                    >terminal</span
                                >
                                <span class="tool-group-summary">{{
                                    getToolGroupSummary(segment)
                                }}</span>
                                <span
                                    class="material-symbols-outlined tool-group-expand"
                                    :class="{ expanded: isLocalOpen(segment.key, segment.open) }"
                                    >chevron_right</span
                                >
                            </button>
                            <div v-if="isLocalOpen(segment.key, segment.open)" class="mt-2">
                                <div class="tool-group-content">
                                    <div
                                        v-for="tool in segment.tools"
                                        :key="tool.key"
                                        class="tool-sequence-item chat-tool-card"
                                    >
                                        <button
                                            type="button"
                                            class="tool-header"
                                            @click="toggleLocalOpen(tool.key, tool.open)"
                                        >
                                            <span
                                                v-if="tool.status === 'running'"
                                                class="tool-icon tool-icon-running"
                                            >
                                                <span class="status-dot-animated"></span>
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
                                                :class="{
                                                    expanded: isLocalOpen(tool.key, tool.open),
                                                }"
                                                >chevron_right</span
                                            >
                                        </button>
                                        <div
                                            v-if="isLocalOpen(tool.key, tool.open)"
                                            class="tool-detail"
                                        >
                                            <div class="detail-section">
                                                <button
                                                    type="button"
                                                    class="detail-toggle"
                                                    @click.stop="
                                                        toggleLocalDetail(
                                                            tool.key,
                                                            'input',
                                                            tool.inputExpanded
                                                        )
                                                    "
                                                >
                                                    <span
                                                        class="material-symbols-outlined detail-toggle-icon"
                                                        :class="{
                                                            expanded: isLocalDetailOpen(
                                                                tool.key,
                                                                'input',
                                                                tool.inputExpanded
                                                            ),
                                                        }"
                                                        >chevron_right</span
                                                    >
                                                    参数
                                                </button>
                                                <div
                                                    v-if="
                                                        isLocalDetailOpen(
                                                            tool.key,
                                                            'input',
                                                            tool.inputExpanded
                                                        )
                                                    "
                                                    class="detail-content"
                                                >
                                                    <pre
                                                        v-if="!isEmptyContent(tool.inputText)"
                                                        class="detail-code"
                                                        >{{ formatBlock(tool.inputText) }}</pre
                                                    >
                                                    <div v-else class="detail-empty">无参数</div>
                                                </div>
                                            </div>
                                            <div class="detail-section">
                                                <button
                                                    type="button"
                                                    class="detail-toggle"
                                                    @click.stop="
                                                        toggleLocalDetail(
                                                            tool.key,
                                                            'output',
                                                            tool.outputExpanded
                                                        )
                                                    "
                                                >
                                                    <span
                                                        class="material-symbols-outlined detail-toggle-icon"
                                                        :class="{
                                                            expanded: isLocalDetailOpen(
                                                                tool.key,
                                                                'output',
                                                                tool.outputExpanded
                                                            ),
                                                        }"
                                                        >chevron_right</span
                                                    >
                                                    输出
                                                </button>
                                                <div
                                                    v-if="
                                                        isLocalDetailOpen(
                                                            tool.key,
                                                            'output',
                                                            tool.outputExpanded
                                                        )
                                                    "
                                                    class="detail-content"
                                                >
                                                    <template v-if="tool.response">
                                                        <pre
                                                            v-if="!isEmptyContent(tool.response)"
                                                            class="detail-code"
                                                            >{{ formatBlock(tool.response) }}</pre
                                                        >
                                                        <div v-else class="detail-empty">
                                                            无返回内容
                                                        </div>
                                                    </template>
                                                    <div v-else class="detail-waiting">
                                                        <span class="status-dot-animated"></span>
                                                        <span>等待返回...</span>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- Single Tool Call -->
                        <div
                            v-else-if="segment.type === 'tool'"
                            :class="[
                                'tool-sequence-item',
                                'chat-tool-card',
                                {
                                    'attached-top': segment.attachedTop,
                                    'attached-bottom': segment.attachedBottom,
                                },
                            ]"
                        >
                            <button
                                type="button"
                                class="tool-header"
                                @click="toggleLocalOpen(segment.key, segment.open)"
                            >
                                <span
                                    v-if="segment.status === 'running'"
                                    class="tool-icon tool-icon-running"
                                >
                                    <span class="status-dot-animated"></span>
                                </span>
                                <span
                                    v-else-if="
                                        segment.status === 'error' || segment.status === 'failed'
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
                                <span v-if="segment.status === 'running'" class="tool-status-text"
                                    >执行中</span
                                >
                                <span
                                    class="material-symbols-outlined expand-icon"
                                    :class="{ expanded: isLocalOpen(segment.key, segment.open) }"
                                    >chevron_right</span
                                >
                            </button>
                            <div v-if="isLocalOpen(segment.key, segment.open)" class="tool-detail">
                                <div class="detail-section">
                                    <button
                                        type="button"
                                        class="detail-toggle"
                                        @click.stop="
                                            toggleLocalDetail(
                                                segment.key,
                                                'input',
                                                segment.inputExpanded
                                            )
                                        "
                                    >
                                        <span
                                            class="material-symbols-outlined detail-toggle-icon"
                                            :class="{
                                                expanded: isLocalDetailOpen(
                                                    segment.key,
                                                    'input',
                                                    segment.inputExpanded
                                                ),
                                            }"
                                            >chevron_right</span
                                        >
                                        参数
                                    </button>
                                    <div
                                        v-if="
                                            isLocalDetailOpen(
                                                segment.key,
                                                'input',
                                                segment.inputExpanded
                                            )
                                        "
                                        class="detail-content"
                                    >
                                        <pre
                                            v-if="!isEmptyContent(segment.inputText)"
                                            class="detail-code"
                                            >{{ formatBlock(segment.inputText) }}</pre
                                        >
                                        <div v-else class="detail-empty">无参数</div>
                                    </div>
                                </div>
                                <div class="detail-section">
                                    <button
                                        type="button"
                                        class="detail-toggle"
                                        @click.stop="
                                            toggleLocalDetail(
                                                segment.key,
                                                'output',
                                                segment.outputExpanded
                                            )
                                        "
                                    >
                                        <span
                                            class="material-symbols-outlined detail-toggle-icon"
                                            :class="{
                                                expanded: isLocalDetailOpen(
                                                    segment.key,
                                                    'output',
                                                    segment.outputExpanded
                                                ),
                                            }"
                                            >chevron_right</span
                                        >
                                        输出
                                    </button>
                                    <div
                                        v-if="
                                            isLocalDetailOpen(
                                                segment.key,
                                                'output',
                                                segment.outputExpanded
                                            )
                                        "
                                        class="detail-content"
                                    >
                                        <template v-if="segment.response">
                                            <pre
                                                v-if="!isEmptyContent(segment.response)"
                                                class="detail-code"
                                                >{{ formatBlock(segment.response) }}</pre
                                            >
                                            <div v-else class="detail-empty">无返回内容</div>
                                        </template>
                                        <div v-else class="detail-waiting">
                                            <span class="status-dot-animated"></span>
                                            <span>等待返回...</span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- Runtime Engine Segment -->
                        <div
                            v-else-if="segment.type === 'runtime-engine'"
                            :class="[
                                'overflow-hidden rounded-xl border border-sky-200 bg-[linear-gradient(180deg,rgba(248,250,252,0.96),rgba(239,246,255,0.96))] shadow-sm',
                                {
                                    'attached-top': segment.attachedTop,
                                    'attached-bottom': segment.attachedBottom,
                                },
                            ]"
                        >
                            <button
                                type="button"
                                class="flex w-full items-start justify-between gap-3 px-4 py-3 text-left"
                                @click="toggleLocalOpen(segment.key, segment.open)"
                            >
                                <div class="min-w-0">
                                    <div
                                        class="flex items-center gap-2 text-[11px] font-bold uppercase tracking-[0.14em] text-sky-600"
                                    >
                                        <span class="material-symbols-outlined text-base"
                                            >account_tree</span
                                        >
                                        <span>{{ segment.engine || 'Runtime Graph' }}</span>
                                    </div>
                                    <div class="mt-1 text-sm font-semibold text-slate-800">
                                        {{ formatRuntimeEngineSummary(segment) }}
                                    </div>
                                    <div class="mt-1 text-xs text-slate-500">
                                        {{ formatRuntimePhaseLabel(segment.currentPhase) }}
                                    </div>
                                </div>
                                <div class="flex items-center gap-2">
                                    <span
                                        class="rounded-full px-2 py-0.5 text-[11px] font-semibold"
                                        :class="
                                            segment.status === 'completed'
                                                ? 'bg-emerald-100 text-emerald-700'
                                                : 'bg-sky-100 text-sky-700'
                                        "
                                    >
                                        {{ segment.status === 'completed' ? '已完成' : '运行中' }}
                                    </span>
                                    <span
                                        class="material-symbols-outlined text-slate-400 transition-transform"
                                        :class="{
                                            'rotate-90': isLocalOpen(segment.key, segment.open),
                                        }"
                                        >chevron_right</span
                                    >
                                </div>
                            </button>
                            <div
                                v-if="isLocalOpen(segment.key, segment.open)"
                                class="overflow-hidden border-t border-sky-100 px-4 py-3"
                            >
                                <div class="flex flex-wrap gap-2">
                                    <span
                                        v-for="item in runtimeEngineStateItems(segment)"
                                        :key="item.key"
                                        class="rounded-full bg-white px-2.5 py-1 text-[11px] font-medium text-slate-700 shadow-sm"
                                    >
                                        <span class="text-slate-400">{{ item.label }}</span>
                                        <span class="ml-1 text-slate-800">{{ item.value }}</span>
                                    </span>
                                </div>
                                <div v-if="segment.history && segment.history.length" class="mt-3">
                                    <div
                                        class="mb-2 text-[11px] font-bold uppercase tracking-[0.14em] text-slate-400"
                                    >
                                        节点轨迹
                                    </div>
                                    <div class="flex flex-wrap gap-2">
                                        <span
                                            v-for="item in segment.history"
                                            :key="`${item.sequence}-${item.node}-${item.status}`"
                                            class="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-[11px] font-medium text-slate-700"
                                        >
                                            {{ formatRuntimeNodeLabel(item.node) }}
                                            <span v-if="item.phase" class="ml-1 text-slate-400">{{
                                                formatRuntimePhaseLabel(item.phase)
                                            }}</span>
                                        </span>
                                    </div>
                                </div>
                                <div
                                    v-if="
                                        segment.state && !isEmptyContent(segment.state.lastDecision)
                                    "
                                    class="mt-3"
                                >
                                    <div
                                        class="mb-2 text-[11px] font-bold uppercase tracking-[0.14em] text-slate-400"
                                    >
                                        最后决策
                                    </div>
                                    <pre
                                        class="rounded-xl bg-slate-950 px-3 py-2 text-xs text-slate-100"
                                        >{{ formatBlock(segment.state.lastDecision) }}</pre
                                    >
                                </div>
                            </div>
                        </div>

                        <!-- Approval Segment -->
                        <div
                            v-else-if="segment.type === 'approval'"
                            :class="[
                                'overflow-hidden rounded-2xl border bg-white shadow-sm',
                                approvalCardClass(segment),
                                {
                                    'attached-top': segment.attachedTop,
                                    'attached-bottom': segment.attachedBottom,
                                },
                            ]"
                        >
                            <div class="flex items-start justify-between gap-3 px-4 py-3">
                                <div class="min-w-0">
                                    <div
                                        class="flex items-center gap-2 text-[11px] font-bold uppercase tracking-[0.14em] text-slate-500"
                                    >
                                        <span class="material-symbols-outlined text-base"
                                            >security</span
                                        >
                                        <span>人工审批</span>
                                    </div>
                                    <div class="mt-1 text-sm font-semibold text-slate-800">
                                        {{
                                            segment.toolDisplayName ||
                                            segment.toolName ||
                                            '重点工具审批'
                                        }}
                                    </div>
                                    <div class="mt-1 text-xs text-slate-500">
                                        {{ resolveApprovalSummary(segment) }}
                                    </div>
                                </div>
                                <div class="flex items-center gap-2">
                                    <span
                                        class="rounded-full px-2 py-0.5 text-[11px] font-semibold"
                                        :class="approvalStatusClass(segment)"
                                    >
                                        {{ approvalStatusLabel(segment) }}
                                    </span>
                                </div>
                            </div>
                            <div class="border-t border-slate-100 px-4 py-3">
                                <div class="grid gap-3 lg:grid-cols-2">
                                    <div class="space-y-2">
                                        <div
                                            class="text-[11px] font-bold uppercase tracking-[0.14em] text-slate-400"
                                        >
                                            参数
                                        </div>
                                        <pre
                                            v-if="
                                                !isEmptyContent(formatBlock(segment.toolArguments))
                                            "
                                            class="max-h-52 overflow-auto rounded-xl bg-slate-950 px-3 py-2 text-xs leading-5 text-slate-100"
                                            >{{ formatBlock(segment.toolArguments) }}</pre
                                        >
                                        <div
                                            v-else
                                            class="rounded-xl border border-dashed border-slate-200 px-3 py-3 text-xs text-slate-400"
                                        >
                                            无参数
                                        </div>
                                    </div>
                                    <div class="space-y-2">
                                        <div
                                            class="text-[11px] font-bold uppercase tracking-[0.14em] text-slate-400"
                                        >
                                            风险分析
                                        </div>
                                        <div
                                            class="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-xs leading-5 text-slate-600"
                                        >
                                            <div class="font-medium text-slate-800">
                                                {{
                                                    segment.analysis?.summary ||
                                                    segment.triggerReason ||
                                                    '等待人工确认。'
                                                }}
                                            </div>
                                            <div v-if="segment.riskLevel" class="mt-1">
                                                风险等级：{{ segment.riskLevel }}
                                            </div>
                                            <div
                                                v-if="
                                                    Array.isArray(segment.analysis?.riskItems) &&
                                                    segment.analysis.riskItems.length
                                                "
                                                class="mt-2 space-y-1"
                                            >
                                                <div
                                                    v-for="item in segment.analysis.riskItems"
                                                    :key="`${segment.key || segment.approvalCode}-${item.code}`"
                                                    class="rounded-lg bg-white px-2.5 py-2 text-[11px] text-slate-600"
                                                >
                                                    <span class="font-semibold text-slate-800">
                                                        {{ item.level || 'MEDIUM' }}
                                                    </span>
                                                    <span class="ml-1">{{ item.message }}</span>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                                <div v-if="segment.toolResult" class="mt-3 space-y-2">
                                    <div
                                        class="text-[11px] font-bold uppercase tracking-[0.14em] text-slate-400"
                                    >
                                        执行结果
                                    </div>
                                    <pre
                                        class="max-h-60 overflow-auto rounded-xl bg-emerald-50 px-3 py-2 text-xs leading-5 text-emerald-900"
                                        >{{ formatBlock(segment.toolResult) }}</pre
                                    >
                                </div>
                                <div
                                    v-if="segment.decisionComment"
                                    class="mt-3 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-600"
                                >
                                    审批备注：{{ segment.decisionComment }}
                                </div>
                                <div class="mt-3 flex flex-wrap gap-2">
                                    <button
                                        v-if="isApprovalPending(segment)"
                                        type="button"
                                        class="rounded-full bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-emerald-500 disabled:cursor-not-allowed disabled:bg-emerald-300"
                                        :disabled="segment.status === 'approving'"
                                        @click.stop="
                                            $emit('approval-action', {
                                                segment,
                                                decision: 'approve',
                                            })
                                        "
                                    >
                                        {{
                                            segment.status === 'approving'
                                                ? '审批中...'
                                                : '批准执行'
                                        }}
                                    </button>
                                    <button
                                        v-if="isApprovalPending(segment)"
                                        type="button"
                                        class="rounded-full border border-rose-200 bg-rose-50 px-3 py-1.5 text-xs font-semibold text-rose-700 transition hover:bg-rose-100 disabled:cursor-not-allowed disabled:bg-rose-50 disabled:text-rose-300"
                                        :disabled="segment.status === 'approving'"
                                        @click.stop="
                                            $emit('approval-action', {
                                                segment,
                                                decision: 'reject',
                                            })
                                        "
                                    >
                                        拒绝
                                    </button>
                                </div>
                            </div>
                        </div>

                        <!-- HTML Preview Segment -->
                        <div
                            v-else-if="segment.type === 'html'"
                            class="min-w-0 cursor-pointer overflow-hidden chat-tool-card transition-colors"
                            @click="$emit('open-html-preview', segment)"
                        >
                            <div class="chat-preview-inner">
                                <div class="chat-preview-title-row text-slate-700">
                                    <span
                                        class="material-symbols-outlined chat-preview-icon text-slate-500"
                                        >code</span
                                    >
                                    <span>{{ segment.title || 'HTML 预览' }}</span>
                                </div>
                                <span class="text-xs text-slate-500">{{ segment.size }}</span>
                            </div>
                        </div>

                        <!-- Artifact Segment -->
                        <div
                            v-else-if="segment.type === 'artifact'"
                            class="min-w-0 overflow-hidden chat-tool-card"
                        >
                            <div class="chat-preview-inner">
                                <div class="chat-preview-title-row text-slate-700">
                                    <span
                                        class="material-symbols-outlined chat-preview-icon text-slate-500"
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
                                        @click.stop="$emit('open-html-preview', segment)"
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

                        <!-- Citation Segment (Hidden in main stream, used via decoration) -->
                        <template v-else-if="segment.type === 'citation'"></template>

                        <!-- Fallback Notice Segment -->
                        <div v-else-if="segment.type === 'fallback_notice'" class="mt-1">
                            <div
                                class="inline-flex max-w-full items-center gap-1.5 rounded bg-amber-50 px-3 py-1.5 text-xs text-amber-700"
                            >
                                <span
                                    class="material-symbols-outlined shrink-0 text-base text-amber-500"
                                    >info</span
                                >
                                <span class="min-w-0 break-words">{{ segment.text }}</span>
                            </div>
                        </div>

                        <!-- Standard Text/Markdown Segment -->
                        <div
                            v-else
                            :class="[
                                'min-h-[40px] min-w-0 max-w-full rounded-lg px-3 py-2',
                                message.render === 'markdown'
                                    ? 'overflow-visible'
                                    : 'overflow-hidden',
                                {
                                    'attached-top': segment.attachedTop,
                                    'attached-bottom': segment.attachedBottom,
                                },
                            ]"
                        >
                            <div
                                v-if="message.render === 'markdown'"
                                class="chat-markdown"
                                @click="handleMarkdownClick($event)"
                                v-html="renderMarkdownWithCitations(segment.text, message)"
                            ></div>
                            <p v-else>{{ segment.text }}</p>
                        </div>
                    </template>
                </div>

                <!-- Fallback to full text if no segments -->
                <div
                    v-else
                    :class="[
                        'min-h-[40px] min-w-0 max-w-full rounded-lg px-3 py-2',
                        message.render === 'markdown' ? 'overflow-visible' : 'overflow-hidden',
                    ]"
                >
                    <div
                        v-if="message.render === 'markdown'"
                        class="chat-markdown"
                        @click="handleMarkdownClick($event)"
                        v-html="renderMarkdownWithCitations(message.text, message)"
                    ></div>
                    <p v-else>{{ message.text }}</p>
                </div>
            </div>

            <!-- Message Time / Pending Indicator -->
            <span
                v-if="
                    message.kind !== 'assistant' ||
                    !message.pending ||
                    shouldShowPendingPlaceholder(message)
                "
                class="text-xs text-slate-400"
                >{{ message.time }}</span
            >
            <span v-else class="inline-flex items-center gap-1.5 text-xs text-slate-600">
                <span
                    class="h-3 w-3 animate-spin rounded-full border-2 border-slate-200 border-t-blue-500"
                ></span>
                {{ resolveAssistantPendingText(message) }}
            </span>
        </div>
    </div>
</template>

<script>
import { marked } from 'marked';
import ChatFrontendRenderBlock from './ChatFrontendRenderBlock.vue';

marked.setOptions({
    breaks: true,
    gfm: true,
});

// Global Markdown cache shared across all instances of ChatMessageItem
const globalMarkdownCache = new Map();
const globalMarkdownCacheMaxSize = 1000;

export default {
    name: 'ChatMessageItem',
    components: {
        ChatFrontendRenderBlock,
    },
    props: {
        message: {
            type: Object,
            required: true,
        },
        splitView: {
            type: Boolean,
            default: false,
        },
        runtimePhasePayload: {
            type: Object,
            default: null,
        },
    },
    emits: [
        'toggle-segment',
        'open-html-preview',
        'open-attachment-preview',
        'open-citation',
        'frontend-render-action',
        'approval-action',
        'height-change',
    ],
    data() {
        return {
            // Local state to track which segments are expanded
            // This is essential when message objects are markRaw (non-reactive)
            localOpenStates: {},
            localDetailStates: {},
        };
    },
    computed: {
        displaySegments() {
            if (!this.message || !Array.isArray(this.message.segments)) {
                return [];
            }
            const segments = this.message.segments.filter(segment => segment != null);
            const result = [];
            let toolGroup = null;
            let toolGroupStartIndex = -1;

            const flushToolGroup = () => {
                if (toolGroup && toolGroup.tools.length >= 1) {
                    if (toolGroup.tools.length === 1) {
                        result.push(toolGroup.tools[0]);
                    } else {
                        // Ensure key is stable and unique
                        toolGroup.key = `group-${this.message.id || 'msg'}-${toolGroupStartIndex}`;
                        result.push(toolGroup);
                    }
                }
                toolGroup = null;
                toolGroupStartIndex = -1;
            };

            segments.forEach((segment, index) => {
                // Ensure every segment has a unique key for tracking state
                if (!segment.key) {
                    // We don't mutate the original markRaw segment, we just use this key for the UI
                    // But for the tool sequence, we might need a derived key
                }

                const segKey = segment.key || `seg-${this.message.id || 'msg'}-${index}`;

                // Add key to a shallow copy if it doesn't exist (only for our local tracking)
                const segmentWithKey = { ...segment, key: segKey };

                if (segment.type === 'tool' && !segment.renderPayload) {
                    const prevRawSegment = segments[index - 1];
                    if (this.isTextSegment(prevRawSegment)) {
                        flushToolGroup();
                        result.push(segmentWithKey);
                        return;
                    }
                    if (!toolGroup) {
                        toolGroup = {
                            type: 'tool-group',
                            tools: [],
                            open: segment.groupOpen || false,
                        };
                        toolGroupStartIndex = index;
                    }
                    toolGroup.tools.push(segmentWithKey);
                } else {
                    flushToolGroup();
                    result.push(segmentWithKey);
                }
            });

            flushToolGroup();

            // Handle attached styles
            for (let i = 0; i < result.length; i++) {
                const current = result[i];
                const prev = result[i - 1];
                if (
                    prev &&
                    this.isTextSegment(prev) &&
                    (current.type === 'tool-group' || current.type === 'tool')
                ) {
                    prev.attachedBottom = true;
                    current.attachedTop = true;
                } else if (
                    prev &&
                    (prev.type === 'tool-group' || prev.type === 'tool') &&
                    this.isTextSegment(current)
                ) {
                    prev.attachedBottom = true;
                    current.attachedTop = true;
                }
            }

            return result;
        },
    },
    methods: {
        isLocalOpen(key, defaultValue) {
            if (!key) return Boolean(defaultValue);
            if (Object.prototype.hasOwnProperty.call(this.localOpenStates, key)) {
                return this.localOpenStates[key];
            }
            return Boolean(defaultValue);
        },
        toggleLocalOpen(key, defaultValue) {
            if (!key) return;
            const currentState = this.isLocalOpen(key, defaultValue);
            this.localOpenStates = {
                ...this.localOpenStates,
                [key]: !currentState,
            };
            this.$emit('height-change');
        },
        isLocalDetailOpen(key, field, defaultValue) {
            const compositeKey = `${key}:${field}`;
            if (Object.prototype.hasOwnProperty.call(this.localDetailStates, compositeKey)) {
                return this.localDetailStates[compositeKey];
            }
            // Defaults: input usually open (if not false), output usually false
            if (defaultValue !== undefined) return Boolean(defaultValue);
            return field === 'input';
        },
        toggleLocalDetail(key, field, defaultValue) {
            const compositeKey = `${key}:${field}`;
            const currentState = this.isLocalDetailOpen(key, field, defaultValue);
            this.localDetailStates = {
                ...this.localDetailStates,
                [compositeKey]: !currentState,
            };
            this.$emit('height-change');
        },
        isTextSegment(segment) {
            if (!segment) return false;
            const type = segment.type;
            return ![
                'tool',
                'runtime-engine',
                'approval',
                'html',
                'artifact',
                'citation',
                'fallback_notice',
            ].includes(type);
        },
        hasFrontendRenderSegment(message) {
            if (!message || !Array.isArray(message.segments)) return false;
            return message.segments.some(s => s?.type === 'tool' && Boolean(s?.renderPayload));
        },
        hasAssistantRenderableContent(message) {
            if (!message) return false;
            if (String(message.text || '').trim()) return true;
            if (!Array.isArray(message.segments) || message.segments.length === 0) return false;
            return message.segments.some(
                s => s && (s.type !== 'citation' || String(s.text || '').trim().length > 0)
            );
        },
        shouldShowPendingPlaceholder(message) {
            return (
                message?.kind === 'assistant' &&
                Boolean(message?.pending) &&
                !this.hasAssistantRenderableContent(message)
            );
        },
        resolveAssistantPendingText(message) {
            if (this.hasAssistantRenderableContent(message)) {
                return this.hasPendingApprovalSegment(message) ? '等待人工审批' : '正在输入中';
            }
            return this.resolveRuntimePendingText(message) || '正在思考';
        },
        hasPendingApprovalSegment(message) {
            if (!message || !Array.isArray(message.segments)) return false;
            return message.segments.some(
                s =>
                    s?.type === 'approval' &&
                    ['pending', 'approving'].includes(String(s?.status || '').toLowerCase())
            );
        },
        resolveRuntimePendingText(message) {
            if (message?.kind !== 'assistant' || !message?.pending) return '';
            const payload = this.runtimePhasePayload || {};
            const subStage = String(payload.subStage || '').toUpperCase();
            if (subStage.includes('HEARTBEAT') || subStage.startsWith('CODE_PLAN')) return '';

            const progressMessage = String(payload.progressMessage || '').trim();
            if (progressMessage) {
                if (subStage.startsWith('CODE_SCRIPT'))
                    return progressMessage.replace(
                        /^正在生成 Python 脚本，?\s*/u,
                        '正在生成文件中 '
                    );
                return this.normalizeUserFacingRuntimeText(progressMessage, payload.phase);
            }
            return '';
        },
        normalizeUserFacingRuntimeText(text, phase) {
            const normalized = String(text || '').trim();
            if (!normalized) return '';
            const phaseText = String(phase || '').toUpperCase();
            if (phaseText === 'OBSERVATION') return '正在整理结果';
            if (phaseText === 'FINALIZING') return '正在整理答复';
            return normalized.replace(/观察/gu, '结果').replace(/流式输出答复/gu, '生成答复');
        },
        getToolGroupSummary(group) {
            if (!group || !group.tools) return '';
            const total = group.tools.length;
            const running = group.tools.filter(t => t.status === 'running').length;
            const done = group.tools.filter(
                t => t.status === 'done' || t.status === 'completed'
            ).length;
            const error = group.tools.filter(
                t => t.status === 'error' || t.status === 'failed'
            ).length;

            if (total === 1) {
                const tool = group.tools[0];
                const name = tool.actionLabel || tool.displayName || tool.name || '工具调用';
                if (running > 0) return `${name} · 执行中`;
                if (error > 0) return `${name} · 失败`;
                return name;
            }
            if (running > 0) return `${total} 个工具调用 · ${done} 完成 ${running} 执行中`;
            if (error > 0) return `${total} 个工具调用 · ${done} 完成 ${error} 失败`;
            return `${total} 个工具调用`;
        },
        resolveMessageContentWidthClass(message) {
            if (message?.kind === 'user') return 'max-w-[85%]';
            // Assistant messages should fill the available space (up to the 800px limit or full screen)
            return 'flex-1 min-w-0';
        },
        approvalStatusLabel(segment) {
            const status = String(segment?.approvalStatus || segment?.status || '').toLowerCase();
            const labels = {
                pending: '待审批',
                approving: '审批中',
                approved: '已批准',
                rejected: '已拒绝',
                failed: '失败',
            };
            return labels[status] || '待审批';
        },
        approvalStatusClass(segment) {
            const status = String(segment?.approvalStatus || segment?.status || '').toLowerCase();
            if (status === 'approved') return 'bg-emerald-100 text-emerald-700';
            if (status === 'rejected') return 'bg-rose-100 text-rose-700';
            return 'bg-slate-100 text-slate-600';
        },
        approvalCardClass(segment) {
            const status = String(segment?.approvalStatus || segment?.status || '').toLowerCase();
            return status === 'approved' ? 'border-emerald-200' : 'border-slate-200';
        },
        resolveApprovalSummary(segment) {
            const parts = [
                segment?.toolName,
                segment?.riskLevel ? `风险 ${segment.riskLevel}` : null,
            ].filter(Boolean);
            return parts.join(' · ') || '等待人工审批后继续执行。';
        },
        isApprovalPending(segment) {
            const status = String(segment?.status || '').toLowerCase();
            return status === 'pending' || status === 'approving';
        },
        formatBlock(value) {
            return typeof value === 'string' ? value : JSON.stringify(value, null, 2);
        },
        formatRuntimeNodeLabel(node) {
            const labels = {
                triageNode: '理解请求',
                reasoningNode: '分析问题',
                actionNode: '执行处理',
                observationNode: '整理结果',
                codeEscalationNode: '代码处理',
                finalAnswerNode: '生成答复',
            };
            return labels[node] || node || '-';
        },
        formatRuntimePhaseLabel(phase) {
            const labels = {
                TRIAGE: '理解请求',
                REASONING: '分析问题',
                ACTION: '执行处理',
                OBSERVATION: '整理结果',
                COMPLETED: '已完成',
            };
            return labels[phase] || phase || '-';
        },
        formatRuntimeEngineSummary(segment) {
            const label = this.formatRuntimeNodeLabel(segment.currentNode);
            return segment.status === 'completed' ? `执行完成 · ${label}` : `当前节点 · ${label}`;
        },
        runtimeEngineStateItems(segment) {
            const state = segment?.state || {};
            const items = [];
            if (state.mode) items.push({ key: 'mode', label: '模式', value: state.mode });
            if (state.iterationCount != null)
                items.push({ key: 'iteration', label: '迭代', value: state.iterationCount });
            return items;
        },
        isEmptyContent(value) {
            if (!value) return true;
            if (typeof value === 'string') return value.trim() === '' || value.trim() === '{}';
            return Object.keys(value).length === 0;
        },
        renderMarkdownWithCitations(text, message) {
            const cacheKey = `${text}:${message.id}`;
            if (globalMarkdownCache.has(cacheKey)) return globalMarkdownCache.get(cacheKey);

            const rendered = this.wrapMarkdownTables(marked.parse(text || ''));
            const citationMap = this.buildCitationMap(message);
            const result = this.decorateRenderedHtml(rendered, citationMap);

            if (globalMarkdownCache.size >= globalMarkdownCacheMaxSize) globalMarkdownCache.clear();
            globalMarkdownCache.set(cacheKey, result);
            return result;
        },
        buildCitationMap(message) {
            const map = new Map();
            if (!message.segments) return map;
            message.segments.forEach(s => {
                if (s?.type === 'citation' && s.ref) map.set(String(s.ref).trim(), s);
            });
            return map;
        },
        decorateRenderedHtml(html, citationMap) {
            if (!html || citationMap.size === 0 || typeof DOMParser === 'undefined') return html;
            const doc = new DOMParser().parseFromString(`<div>${html}</div>`, 'text/html');
            const root = doc.body.firstElementChild;
            const walker = doc.createTreeWalker(root, NodeFilter.SHOW_TEXT);
            const textNodes = [];
            let n;
            while ((n = walker.nextNode()))
                if (!n.parentElement.closest('pre, code, a')) textNodes.push(n);

            textNodes.forEach(node => {
                const content = node.textContent;
                if (!/\[(\d+)\]/.test(content)) return;
                const frag = doc.createDocumentFragment();
                let last = 0;
                content.replace(/\[(\d+)\]/g, (m, ref, offset) => {
                    if (offset > last)
                        frag.appendChild(doc.createTextNode(content.slice(last, offset)));
                    frag.appendChild(this.createCitationNode(doc, ref, citationMap));
                    last = offset + m.length;
                });
                if (last < content.length)
                    frag.appendChild(doc.createTextNode(content.slice(last)));
                node.parentNode.replaceChild(frag, node);
            });
            return root.innerHTML;
        },
        createCitationNode(doc, ref, map) {
            const key = ref.trim();
            const el = doc.createElement(map.has(key) ? 'button' : 'span');
            el.className = map.has(key)
                ? 'mx-0.5 inline-flex cursor-pointer items-center rounded-full border border-emerald-300 bg-emerald-100 px-1.5 py-0.5 text-[11px] font-semibold text-emerald-700'
                : 'mx-0.5 inline-flex items-center rounded-full border border-slate-200 bg-slate-100 px-1.5 py-0.5 text-[11px] font-semibold text-slate-400';
            el.textContent = `[${key}]`;
            if (map.has(key)) el.setAttribute('data-citation-ref', key);
            return el;
        },
        wrapMarkdownTables(html) {
            if (!html || typeof DOMParser === 'undefined') return html;
            const doc = new DOMParser().parseFromString(`<div>${html}</div>`, 'text/html');
            doc.querySelectorAll('table').forEach(table => {
                const div = doc.createElement('div');
                div.className = 'chat-markdown-table-scroll';
                table.parentNode.insertBefore(div, table);
                div.appendChild(table);
            });
            return doc.body.firstElementChild.innerHTML;
        },
        handleMarkdownClick(event) {
            const target = event?.target?.closest?.('[data-citation-ref]');
            if (!target) return;
            const key = target.getAttribute('data-citation-ref');
            const citation = this.buildCitationMap(this.message).get(key);
            if (citation) this.$emit('open-citation', citation);
        },
    },
};
</script>

<style scoped>
.chat-markdown {
    width: 100%;
    max-width: 100%;
    min-width: 0;
    overflow-x: hidden;
    color: #334155;
    line-height: 1.7;
    word-break: break-word;
}
.chat-markdown :deep(p) {
    margin-top: 0.5rem;
}
.chat-markdown :deep(pre) {
    margin-top: 0.75rem;
    border: 1px solid #1e293b;
    background: #1e293b;
    color: #e5e7eb;
    border-radius: 0.5rem;
    padding: 0.75rem 1rem;
    overflow-x: auto;
}
.chat-markdown :deep(code) {
    background: #f1f5f9;
    border-radius: 0.25rem;
    padding: 0.125rem 0.375rem;
    color: #0f172a;
}
.chat-markdown :deep(table) {
    min-width: 100%;
    width: 100%;
    max-width: 100%;
    border-collapse: collapse;
    table-layout: auto;
}
.chat-markdown :deep(th) {
    background: #f1f5f9;
    color: #1e293b;
    min-width: max-content;
    white-space: nowrap;
}
.chat-markdown :deep(td) {
    min-width: max-content;
    white-space: nowrap;
}
.chat-markdown :deep(.chat-markdown-table-scroll) {
    display: block;
    width: 100%;
    overflow-x: auto;
    overflow-y: hidden;
    max-width: 100%;
    -webkit-overflow-scrolling: touch;
    border: 1px solid #e5e7eb;
    border-radius: 0.5rem;
    margin-top: 0.75rem;
}
</style>

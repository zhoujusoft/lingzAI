<template>
    <div ref="chatWindow" class="custom-scrollbar flex-1 space-y-8 overflow-y-auto p-6">
        <div class="space-y-8">
            <div v-if="visibleMessages.length === 0" class="mx-auto max-w-3xl py-12 text-center">
                <div
                    class="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-blue-100 text-primary shadow-sm"
                >
                    <span class="material-symbols-outlined text-4xl">{{ emptyIcon }}</span>
                </div>
                <h3 class="text-2xl font-bold">{{ emptyTitle }}</h3>
                <p class="mt-2 text-slate-500">{{ emptyDescription }}</p>
            </div>
            <div
                v-for="(message, index) in visibleMessages"
                :key="index"
                :class="[
                    'mx-auto flex w-full max-w-4xl min-w-0 gap-3',
                    message.kind === 'user' ? 'justify-end' : 'justify-start',
                ]"
            >
                <div
                    v-if="message.kind !== 'user'"
                    class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-blue-600 text-white"
                >
                    <span class="material-symbols-outlined text-lg">psychology</span>
                </div>
                <div
                    :class="[
                        'flex min-w-0 flex-col gap-2',
                        resolveMessageContentWidthClass(message),
                        message.kind === 'user' ? 'items-end' : 'items-start',
                    ]"
                >
                    <template v-if="message.kind === 'user'">
                        <div
                            v-if="message.attachments && message.attachments.length"
                            class="grid gap-2"
                        >
                            <div
                                v-for="file in message.attachments"
                                :key="file.id"
                                class="flex items-center gap-4 rounded-xl border border-slate-200 bg-slate-50 p-3 shadow-sm"
                            >
                                <div class="flex h-10 w-10 items-center justify-center rounded bg-blue-50 text-primary">
                                    <span class="material-symbols-outlined">description</span>
                                </div>
                                <div class="text-left">
                                    <p class="text-sm font-medium text-slate-700">{{ file.name }}</p>
                                    <p class="text-xs text-slate-400">上传成功</p>
                                </div>
                                <span class="material-symbols-outlined ml-4 text-slate-300">visibility</span>
                            </div>
                        </div>
                        <div class="max-w-full break-words rounded-2xl rounded-tr-none bg-primary p-4 text-sm text-white shadow-md">
                            {{ message.text }}
                        </div>
                    </template>
                    <div
                        v-else-if="message.kind === 'assistant'"
                        :class="[
                            'min-w-0 space-y-4 rounded-2xl rounded-tl-none border border-slate-200 bg-slate-50 p-5 text-sm text-slate-800 shadow-sm',
                            hasFrontendRenderSegment(message) ? 'inline-block w-auto max-w-full' : 'w-full',
                        ]"
                    >
                        <div v-if="message.segments && message.segments.length" class="grid gap-2">
                            <template
                                v-for="(segment, segIndex) in message.segments"
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
                                <div
                                    v-else-if="segment.type === 'tool'"
                                    class="min-w-0 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm"
                                >
                                    <button
                                        type="button"
                                        class="flex w-full items-center justify-between border-b border-slate-100 bg-slate-100/50 px-3 py-3"
                                        @click="$emit('toggle-segment', segment)"
                                    >
                                        <div class="flex items-center gap-2 text-xs font-semibold text-primary">
                                            <span class="material-symbols-outlined text-base">
                                                {{ segment.renderPayload ? 'view_quilt' : 'terminal' }}
                                            </span>
                                            <span>{{ segment.renderPayload ? '前端渲染' : '工具调用' }}</span>
                                            <span class="rounded-full bg-white px-2 py-0.5 text-[11px] font-semibold text-slate-600">
                                                {{ segment.displayName || segment.name || 'unknown' }}
                                            </span>
                                        </div>
                                        <span class="material-symbols-outlined text-slate-400">
                                            {{ segment.open ? 'expand_less' : 'expand_more' }}
                                        </span>
                                    </button>
                                    <div v-if="segment.open" class="grid gap-2 p-2.5">
                                        <div class="min-w-0 overflow-hidden rounded-lg border border-slate-200">
                                            <div
                                                class="flex items-center justify-between border-b border-slate-100 bg-slate-50 px-2.5 py-1.5 text-[10px] font-bold uppercase tracking-wider text-slate-400"
                                            >
                                                <span>参数</span>
                                                <div class="flex gap-1">
                                                    <span class="material-symbols-outlined text-sm">view_headline</span>
                                                    <span class="material-symbols-outlined text-sm">content_copy</span>
                                                </div>
                                            </div>
                                            <div class="min-w-0 overflow-x-auto p-2.5">
                                                <pre class="w-full min-w-0 whitespace-pre-wrap break-words text-xs text-slate-600">{{ formatBlock(segment.inputText) }}</pre>
                                            </div>
                                        </div>
                                        <div class="min-w-0 overflow-hidden rounded-lg border border-slate-200">
                                            <div
                                                class="flex items-center justify-between border-b border-slate-100 bg-slate-50 px-2.5 py-1.5 text-[10px] font-bold uppercase tracking-wider text-slate-400"
                                            >
                                                <span>输出</span>
                                                <span class="material-symbols-outlined text-sm">content_copy</span>
                                            </div>
                                            <div class="min-w-0 overflow-x-auto p-2.5">
                                                <pre
                                                    v-if="segment.response"
                                                    class="w-full min-w-0 whitespace-pre-wrap break-words text-xs text-slate-600"
                                                >{{ formatBlock(segment.response) }}</pre>
                                                <div
                                                    v-else
                                                    class="inline-flex items-center gap-1.5 text-xs text-slate-400"
                                                >
                                                    <span class="material-symbols-outlined text-sm">pending</span>
                                                    <span>等待工具返回...</span>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                                <div
                                    v-else-if="segment.type === 'html'"
                                    class="min-w-0 cursor-pointer overflow-hidden rounded-xl border border-slate-200 bg-white"
                                    @click="$emit('open-html-preview', segment)"
                                >
                                    <div class="flex items-center justify-between bg-slate-50 px-3 py-2">
                                        <div class="flex items-center gap-2 text-xs font-semibold text-slate-700">
                                            <span class="material-symbols-outlined text-base">code</span>
                                            <span>{{ segment.title || 'HTML 预览' }}</span>
                                        </div>
                                        <span class="text-xs text-slate-400">点击预览</span>
                                    </div>
                                    <div class="p-2.5">
                                        <div class="min-w-0 overflow-hidden rounded-lg border border-slate-200">
                                            <div
                                                class="border-b border-slate-100 bg-slate-50 px-2.5 py-1.5 text-[10px] font-bold uppercase tracking-wider text-slate-400"
                                            >
                                                HTML
                                            </div>
                                            <div class="min-w-0 overflow-x-auto p-2.5">
                                                <pre class="w-full min-w-0 whitespace-pre-wrap break-words text-xs text-slate-600">{{ segment.size }}</pre>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                                <template v-else-if="segment.type === 'citation'"></template>
                                <div
                                    v-else-if="segment.type === 'fallback_notice'"
                                    class="mt-1"
                                >
                                    <div class="inline-flex max-w-full items-center gap-1.5 rounded-lg border border-amber-200 bg-amber-50 px-2.5 py-1.5 text-[11px] leading-5 text-amber-700">
                                        <span class="material-symbols-outlined shrink-0 text-sm text-amber-600">info</span>
                                        <span class="min-w-0 break-words">{{ segment.text }}</span>
                                    </div>
                                </div>
                                <div v-else>
                                    <div
                                        v-if="message.render === 'markdown'"
                                        class="chat-markdown"
                                        @click="handleMarkdownClick($event, message)"
                                        v-html="renderMarkdownWithCitations(segment.text, message)"
                                    ></div>
                                    <p v-else>{{ segment.text }}</p>
                                </div>
                            </template>
                        </div>
                        <div v-else>
                            <div
                                v-if="message.render === 'markdown'"
                                class="chat-markdown"
                                @click="handleMarkdownClick($event, message)"
                                v-html="renderMarkdownWithCitations(message.text, message)"
                            ></div>
                            <p v-else>{{ message.text }}</p>
                        </div>
                    </div>
                    <span v-if="message.kind !== 'assistant' || !message.pending" class="text-[10px] text-slate-400">{{ message.time }}</span>
                    <span
                        v-else
                        class="inline-flex items-center gap-1.5 text-[10px] text-slate-400"
                    >
                        <span
                            class="h-2.5 w-2.5 animate-spin rounded-full border-2 border-blue-100 border-t-primary"
                        ></span>
                        正在生成
                    </span>
                </div>
            </div>
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

export default {
    name: 'ChatMessageStream',
    components: {
        ChatFrontendRenderBlock,
    },
    emits: ['toggle-segment', 'open-html-preview', 'open-citation', 'frontend-render-action'],
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
    },
    methods: {
        isVisibleMessage(message) {
            const messageType = String(message?.messageType || 'normal').trim().toLowerCase();
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
        resolveMessageContentWidthClass(message) {
            if (message?.kind === 'user') {
                return 'max-w-[85%]';
            }
            if (this.hasFrontendRenderSegment(message)) {
                return 'max-w-full';
            }
            return 'max-w-[85%]';
        },
        renderMarkdown(text) {
            return marked.parse(text || '');
        },
        renderMarkdownWithCitations(text, message) {
            const citationMap = this.buildCitationMap(message);
            const rendered = this.renderMarkdown(String(text || ''));
            return this.decorateRenderedHtml(rendered, citationMap);
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
                    fragment.appendChild(this.createCitationNode(documentNode, refText, citationMap));
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
                    'mx-0.5 inline-flex items-center rounded-full border border-slate-200 bg-slate-100 px-1.5 py-0.5 text-[11px] font-semibold text-slate-400';
                badge.textContent = `[${key}]`;
                return badge;
            }
            const button = documentNode.createElement('button');
            button.type = 'button';
            button.setAttribute('data-citation-ref', key);
            button.className =
                'mx-0.5 inline-flex cursor-pointer items-center rounded-full border border-emerald-300 bg-emerald-100 px-1.5 py-0.5 text-[11px] font-semibold text-emerald-700 transition hover:bg-emerald-200';
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
        visibleMessages() {
            return Array.isArray(this.messages)
                ? this.messages.filter(message => this.isVisibleMessage(message))
                : [];
        },
    },
    watch: {
        scrollToken() {
            this.scrollChatToBottom(true);
        },
    },
    updated() {
        this.scrollChatToBottom();
    },
};
</script>

<style scoped>
.chat-markdown {
    max-width: 100%;
    min-width: 0;
    overflow: hidden;
    color: #1f2937;
    line-height: 1.75;
    word-break: break-word;
}

.chat-markdown :deep(> *:first-child) {
    margin-top: 0;
}

.chat-markdown :deep(p) {
    margin-top: 0.75rem;
}

.chat-markdown :deep(h1),
.chat-markdown :deep(h2),
.chat-markdown :deep(h3),
.chat-markdown :deep(h4) {
    margin-top: 1rem;
    font-weight: 700;
    line-height: 1.4;
    color: #0f172a;
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
    margin: 0.75rem 0 0.75rem 1.25rem;
    padding-left: 0.4rem;
}

.chat-markdown :deep(ul) {
    list-style: disc;
}

.chat-markdown :deep(ol) {
    list-style: decimal;
}

.chat-markdown :deep(li) {
    margin-top: 0.35rem;
}

.chat-markdown :deep(li > p) {
    margin-top: 0.25rem;
}

.chat-markdown :deep(blockquote) {
    margin-top: 0.9rem;
    border-left: 3px solid #93c5fd;
    background: #eff6ff;
    padding: 0.75rem 0.9rem;
    color: #334155;
    border-radius: 0 0.75rem 0.75rem 0;
}

.chat-markdown :deep(hr) {
    margin: 1rem 0;
    border: 0;
    border-top: 1px solid #e2e8f0;
}

.chat-markdown :deep(a) {
    color: #2563eb;
    text-decoration: underline;
    text-decoration-thickness: 1px;
    text-underline-offset: 0.18em;
}

.chat-markdown :deep(code) {
    border: 1px solid #dbeafe;
    background: #eff6ff;
    border-radius: 0.45rem;
    padding: 0.15rem 0.4rem;
    font-size: 0.85em;
    color: #1d4ed8;
}

.chat-markdown :deep(pre) {
    margin-top: 0.9rem;
    max-width: 100%;
    overflow-x: auto;
    border: 1px solid #dbe3f0;
    background: #0f172a;
    color: #e2e8f0;
    border-radius: 0.9rem;
    padding: 0.9rem 1rem;
}

.chat-markdown :deep(pre code) {
    white-space: pre;
    border: 0;
    background: transparent;
    color: inherit;
    padding: 0;
    font-size: 0.9em;
}

.chat-markdown :deep(table) {
    display: block;
    max-width: 100%;
    width: max-content;
    margin-top: 0.9rem;
    border-collapse: collapse;
    overflow-x: auto;
    overflow: hidden;
    border-radius: 0.9rem;
    border: 1px solid #dbe3f0;
    background: #fff;
}

.chat-markdown :deep(th),
.chat-markdown :deep(td) {
    border-bottom: 1px solid #e2e8f0;
    padding: 0.65rem 0.8rem;
    text-align: left;
    vertical-align: top;
}

.chat-markdown :deep(th) {
    background: #f8fafc;
    font-weight: 600;
    color: #334155;
}

.chat-markdown :deep(tr:last-child td) {
    border-bottom: 0;
}

.chat-markdown :deep(img) {
    max-width: 100%;
    border-radius: 0.9rem;
    margin-top: 0.9rem;
}
</style>

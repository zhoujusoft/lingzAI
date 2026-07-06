<template>
    <div
        ref="editorRef"
        class="mention-editor custom-scrollbar min-h-[92px] max-h-[220px] w-full resize-none overflow-y-auto bg-transparent px-5 py-4 text-[15px] leading-7 text-body focus:outline-none"
        :class="{ 'opacity-50': disabled }"
        :contenteditable="!disabled"
        :data-placeholder="placeholder"
        @beforeinput="handleBeforeInput"
        @input="handleInput"
        @keydown="handleKeydown"
        @paste="handlePaste"
        @focus="$emit('focus')"
        @blur="$emit('blur')"
    ></div>
</template>

<script>
/**
 * MentionEditor - 支持原子化 @mention 的 contenteditable 编辑器
 *
 * 特性：
 * - @技能名 渲染为不可编辑的 span，光标无法进入
 * - 删除时整体删除
 * - 支持多行输入
 */
export default {
    name: 'MentionEditor',
    emits: ['update:modelValue', 'focus', 'blur', 'keydown', 'mention-trigger'],
    props: {
        modelValue: {
            type: String,
            default: '',
        },
        placeholder: {
            type: String,
            default: '',
        },
        disabled: {
            type: Boolean,
            default: false,
        },
        // mention 解析函数：返回 { mentions: [{start, end, token, skill}] }
        parseMentions: {
            type: Function,
            default: null,
        },
    },
    data() {
        return {
            internalValue: '',
            lastRenderedMentionSignature: null,
            lastRenderSignature: null,
            isRendering: false,
        };
    },
    watch: {
        modelValue: {
            immediate: true,
            handler(newValue) {
                if (newValue !== this.internalValue) {
                    this.internalValue = newValue;
                    this.renderContent();
                }
            },
        },
    },
    mounted() {
        this.renderContent();
    },
    methods: {
        isMentionTextNode(node) {
            return node?.nodeType === Node.TEXT_NODE && node?.parentElement?.dataset?.mention;
        },
        normalizeExtractedText(text) {
            const value = String(text || '');
            return value.trim() ? value : '';
        },
        getMentionSignature(mentions = []) {
            return JSON.stringify(
                mentions.map(mention => ({
                    start: mention.start,
                    end: mention.end,
                    token: mention.token,
                }))
            );
        },

        /**
         * 渲染编辑器内容
         */
        renderContent() {
            const editor = this.$refs.editorRef;
            if (!editor) return;

            const text = this.internalValue;
            const mentions = this.parseMentions ? this.parseMentions(text) : [];
            const mentionSignature = this.getMentionSignature(mentions);
            const shouldRestoreCursor = document.activeElement === editor;
            const cursorOffset = shouldRestoreCursor ? this.getCursorOffset() : null;

            const renderSignature = JSON.stringify({
                text,
                mentions: JSON.parse(mentionSignature),
            });

            if (renderSignature === this.lastRenderSignature) {
                return;
            }
            this.lastRenderedMentionSignature = mentionSignature;
            this.lastRenderSignature = renderSignature;

            // 设置渲染标志，防止 DOM 变化触发 handleInput 回写
            this.isRendering = true;

            // 特殊处理：空文本直接清空编辑器
            if (!text) {
                editor.innerHTML = '';
                this.$nextTick(() => {
                    this.isRendering = false;
                });
                return;
            }

            const segments = this.buildSegments(text, mentions);

            const fragment = document.createDocumentFragment();
            for (const seg of segments) {
                if (seg.type === 'text') {
                    this.appendTextSegment(fragment, seg.content);
                } else if (seg.type === 'mention') {
                    this.appendMentionSegment(fragment, seg);
                }
            }

            editor.innerHTML = '';
            editor.appendChild(fragment);

            if (shouldRestoreCursor && cursorOffset !== null) {
                this.setCursorOffset(cursorOffset);
            }

            this.$nextTick(() => {
                this.isRendering = false;
            });
        },

        /**
         * 构建文本分段
         */
        buildSegments(text, mentions) {
            if (!mentions.length) {
                return text ? [{ type: 'text', content: text }] : [];
            }

            const segments = [];
            let cursor = 0;

            // 按位置排序
            const sorted = [...mentions].sort((a, b) => a.start - b.start);

            for (const mention of sorted) {
                // 前面的普通文本
                if (mention.start > cursor) {
                    segments.push({
                        type: 'text',
                        content: text.slice(cursor, mention.start),
                    });
                }
                // mention 段
                segments.push({
                    type: 'mention',
                    content: mention.token,
                    skill: mention.skill,
                });
                cursor = mention.end;
            }

            // 剩余文本
            if (cursor < text.length) {
                segments.push({
                    type: 'text',
                    content: text.slice(cursor),
                });
            }

            return segments;
        },

        /**
         * 添加普通文本段（处理换行）
         */
        appendTextSegment(fragment, content) {
            const lines = content.split('\n');
            for (let i = 0; i < lines.length; i++) {
                if (i > 0) {
                    fragment.appendChild(document.createElement('br'));
                }
                if (lines[i]) {
                    fragment.appendChild(document.createTextNode(lines[i]));
                }
            }
        },

        /**
         * 添加 mention 段（不可编辑的 span）
         */
        appendMentionSegment(fragment, mention) {
            const span = document.createElement('span');
            span.setAttribute('contenteditable', 'false');
            span.className = 'mention-chip';
            span.textContent = mention.content;
            span.dataset.mention = mention.content;
            fragment.appendChild(span);
        },

        /**
         * 从 DOM 提取纯文本
         */
        extractText() {
            const editor = this.$refs.editorRef;
            if (!editor) return '';

            let text = '';
            const walker = document.createTreeWalker(
                editor,
                NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT,
                {
                    acceptNode: node => {
                        if (this.isMentionTextNode(node)) {
                            return NodeFilter.FILTER_SKIP;
                        }
                        if (node.nodeType === Node.TEXT_NODE) {
                            return NodeFilter.FILTER_ACCEPT;
                        }
                        if (node.nodeType === Node.ELEMENT_NODE) {
                            if (node.dataset?.mention || node.tagName === 'BR') {
                                return NodeFilter.FILTER_ACCEPT;
                            }
                        }
                        return NodeFilter.FILTER_SKIP;
                    },
                }
            );

            while (walker.nextNode()) {
                const node = walker.currentNode;
                if (node.nodeType === Node.TEXT_NODE) {
                    text += node.textContent || '';
                } else if (node.dataset?.mention) {
                    text += node.textContent || '';
                } else if (node.tagName === 'BR') {
                    text += '\n';
                }
            }

            return this.normalizeExtractedText(text);
        },

        /**
         * 处理 beforeinput 事件
         */
        handleBeforeInput(event) {
            if (this.disabled) {
                event.preventDefault();
            }
        },

        /**
         * 处理 input 事件
         */
        handleInput() {
            // 如果是程序化渲染触发的，忽略此次 input 事件
            if (this.isRendering) {
                return;
            }
            const text = this.extractText();
            this.internalValue = text;
            this.$emit('update:modelValue', text);

            const mentions = this.parseMentions ? this.parseMentions(text) : [];
            const mentionSignature = this.getMentionSignature(mentions);
            if (mentionSignature !== this.lastRenderedMentionSignature) {
                this.renderContent();
            }

            // 检测是否触发了 @mention
            const lastAtIndex = text.lastIndexOf('@');
            if (lastAtIndex !== -1) {
                const afterAt = text.slice(lastAtIndex + 1);
                // 如果 @ 后面没有空格或另一个 @，可能是新的 mention
                if (!afterAt.includes(' ') && !afterAt.includes('@')) {
                    this.$emit('mention-trigger', {
                        position: lastAtIndex,
                        query: afterAt,
                    });
                }
            }
        },

        /**
         * 处理粘贴事件
         */
        handlePaste(event) {
            event.preventDefault();
            const text = event.clipboardData?.getData('text/plain') || '';
            document.execCommand('insertText', false, text);
        },

        /**
         * 处理键盘事件
         */
        handleKeydown(event) {
            if (event.key === 'Tab' || event.key === 'Enter') {
                event.preventDefault();
            }
            this.$emit('keydown', event);
        },

        clear() {
            const editor = this.$refs.editorRef;
            this.internalValue = '';
            this.lastRenderedMentionSignature = this.getMentionSignature([]);
            this.lastRenderSignature = JSON.stringify({
                text: '',
                mentions: [],
            });
            this.isRendering = false;
            if (editor) {
                editor.innerHTML = '';
            }
        },

        /**
         * 聚焦编辑器
         */
        focus() {
            const editor = this.$refs.editorRef;
            if (!editor) return;

            editor.focus();

            // 将光标移到末尾
            const range = document.createRange();
            const selection = window.getSelection();
            range.selectNodeContents(editor);
            range.collapse(false);
            selection?.removeAllRanges();
            selection?.addRange(range);
        },

        /**
         * 获取当前光标位置（文本偏移量）
         */
        getCursorOffset() {
            const editor = this.$refs.editorRef;
            const selection = window.getSelection();
            if (
                !selection ||
                selection.rangeCount === 0 ||
                !editor.contains(selection.anchorNode)
            ) {
                return null;
            }

            const range = selection.getRangeAt(0);
            const preRange = document.createRange();
            preRange.selectNodeContents(editor);
            preRange.setEnd(range.startContainer, range.startOffset);

            let offset = 0;
            const walker = document.createTreeWalker(
                preRange.cloneContents(),
                NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT,
                {
                    acceptNode: node => {
                        if (this.isMentionTextNode(node)) return NodeFilter.FILTER_SKIP;
                        if (node.nodeType === Node.TEXT_NODE) return NodeFilter.FILTER_ACCEPT;
                        if (node.dataset?.mention || node.tagName === 'BR') {
                            return NodeFilter.FILTER_ACCEPT;
                        }
                        return NodeFilter.FILTER_SKIP;
                    },
                }
            );

            while (walker.nextNode()) {
                const node = walker.currentNode;
                if (node.nodeType === Node.TEXT_NODE) {
                    offset += node.textContent?.length || 0;
                } else if (node.dataset?.mention) {
                    offset += node.textContent?.length || 0;
                } else if (node.tagName === 'BR') {
                    offset += 1;
                }
            }

            return offset;
        },

        /**
         * 设置光标位置
         */
        setCursorOffset(offset) {
            const editor = this.$refs.editorRef;
            if (!editor || offset === null) return;

            let current = 0;
            const walker = document.createTreeWalker(
                editor,
                NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT,
                {
                    acceptNode: node => {
                        if (this.isMentionTextNode(node)) return NodeFilter.FILTER_SKIP;
                        if (node.nodeType === Node.TEXT_NODE) return NodeFilter.FILTER_ACCEPT;
                        if (node.dataset?.mention || node.tagName === 'BR') {
                            return NodeFilter.FILTER_ACCEPT;
                        }
                        return NodeFilter.FILTER_SKIP;
                    },
                }
            );

            while (walker.nextNode()) {
                const node = walker.currentNode;
                let len = 0;
                if (node.nodeType === Node.TEXT_NODE) {
                    len = node.textContent?.length || 0;
                } else if (node.dataset?.mention) {
                    len = node.textContent?.length || 0;
                } else if (node.tagName === 'BR') {
                    len = 1;
                }

                if (current + len >= offset) {
                    const range = document.createRange();
                    const sel = window.getSelection();
                    const pos = offset - current;

                    if (node.nodeType === Node.TEXT_NODE) {
                        range.setStart(node, pos);
                        range.collapse(true);
                    } else {
                        range.selectNodeContents(node);
                        range.collapse(pos >= len);
                    }

                    sel?.removeAllRanges();
                    sel?.addRange(range);
                    return;
                }
                current += len;
            }

            // 超出范围，放在末尾
            const range = document.createRange();
            const sel = window.getSelection();
            range.selectNodeContents(editor);
            range.collapse(false);
            sel?.removeAllRanges();
            sel?.addRange(range);
        },

        /**
         * 强制重新渲染（用于外部更新后）
         */
        forceRender() {
            this.lastRenderedMentionSignature = null;
            this.lastRenderSignature = null;
            this.renderContent();
        },
    },
};
</script>

<style scoped>
.mention-editor {
    word-break: break-word;
    white-space: pre-wrap;
}

.mention-editor--default {
    min-height: 4.5rem;
    max-height: 12.5rem;
    padding: 1rem 1rem 0.625rem;
    color: rgb(var(--color-text-strong));
    line-height: 1.7;
}

/* placeholder */
.mention-editor:empty::before {
    content: attr(data-placeholder);
    color: rgb(var(--color-text-muted) / 0.94);
    pointer-events: none;
}

/* mention span 需要和正文共享同一套基线，避免看起来上下漂移 */
.mention-editor :deep(.mention-chip) {
    display: inline-flex;
    align-items: center;
    min-height: 1.375rem;
    margin: 0;
    padding: 0 0.375rem;
    border: 1px solid rgb(var(--color-accent) / 0.2);
    border-radius: 0.5rem;
    background: rgb(var(--color-accent-soft) / 0.9);
    color: rgb(var(--color-accent) / 0.92);
    box-sizing: border-box;
    font-size: inherit;
    font-weight: 600;
    line-height: 1.25rem;
    vertical-align: text-bottom;
    white-space: nowrap;
    cursor: default;
    user-select: all;
}

.mention-editor--default :deep(.mention-chip) {
    border-color: transparent;
    border-radius: 9999px;
    background: #edf3fb;
    color: rgb(var(--color-text-strong));
    font-weight: 500;
}

.mention-editor :deep(.mention-chip:hover) {
    border-color: rgb(var(--color-accent) / 0.28);
    background: rgb(var(--color-accent-soft) / 0.98);
}

.mention-editor--default :deep(.mention-chip:hover) {
    border-color: transparent;
    background: #e5edf8;
}

.mention-editor :deep(.mention-chip[contenteditable='false']) {
    cursor: default;
}
</style>

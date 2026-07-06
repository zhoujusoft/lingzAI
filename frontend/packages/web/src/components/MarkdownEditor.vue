<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { marked } from 'marked';

const props = defineProps({
    modelValue: {
        type: String,
        default: '',
    },
    placeholder: {
        type: String,
        default: '',
    },
    height: {
        type: String,
        default: '200px',
    },
    autoHeight: {
        type: Boolean,
        default: false,
    },
    label: {
        type: String,
        default: '',
    },
    hint: {
        type: String,
        default: '',
    },
});

const emit = defineEmits(['update:modelValue']);

const mode = ref('edit'); // 'edit' | 'preview'
const editorRootRef = ref(null);
const textareaRef = ref(null);
let resizeObserver = null;

const renderedContent = computed(() => {
    if (!props.modelValue) return '';
    return marked.parse(String(props.modelValue));
});
const contentContainerClass = computed(() => [
    'markdown-content-container',
    {
        'markdown-content-container--fixed': !props.autoHeight,
    },
]);
const contentContainerStyle = computed(() =>
    props.autoHeight ? { minHeight: props.height } : { height: props.height }
);
const textareaClass = computed(() => [
    'w-full rounded-xl border border-slate-200 bg-[#f8fafc] px-4 py-3 text-sm text-slate-700 outline-none transition-all placeholder:text-slate-400 focus:border-primary focus:ring-2 focus:ring-primary/20 resize-none',
    props.autoHeight ? 'overflow-hidden' : 'h-full',
]);
const textareaStyle = computed(() => (props.autoHeight ? { minHeight: props.height } : undefined));
const previewClass = computed(() => [
    'w-full rounded-xl border border-slate-200 bg-slate-50/80 px-4 py-3 text-sm text-slate-700',
    props.autoHeight ? '' : 'h-full overflow-auto',
]);
const previewStyle = computed(() => (props.autoHeight ? { minHeight: props.height } : undefined));
const emptyStateStyle = computed(() =>
    props.autoHeight ? { minHeight: props.height } : { height: '100%' }
);

function handleInput(event) {
    emit('update:modelValue', event.target.value);
    resizeTextarea(event.target);
}

function setEditMode() {
    mode.value = 'edit';
}

function setPreviewMode() {
    mode.value = 'preview';
}

function resizeTextarea(target = textareaRef.value) {
    if (!props.autoHeight || mode.value !== 'edit' || !target) {
        return;
    }
    target.style.height = 'auto';
    target.style.height = `${target.scrollHeight}px`;
}

watch(
    () => props.modelValue,
    () => {
        nextTick(() => resizeTextarea());
    }
);

watch(
    () => mode.value,
    value => {
        if (value !== 'edit') {
            return;
        }
        nextTick(() => resizeTextarea());
    }
);

watch(
    () => props.autoHeight,
    enabled => {
        if (!enabled) {
            if (textareaRef.value) {
                textareaRef.value.style.height = '';
            }
            return;
        }
        nextTick(() => resizeTextarea());
    }
);

onMounted(() => {
    nextTick(() => resizeTextarea());
    if (typeof ResizeObserver === 'undefined' || !editorRootRef.value) {
        return;
    }
    resizeObserver = new ResizeObserver(() => {
        resizeTextarea();
    });
    resizeObserver.observe(editorRootRef.value);
});

onBeforeUnmount(() => {
    resizeObserver?.disconnect();
    resizeObserver = null;
});
</script>

<template>
    <div ref="editorRootRef" class="markdown-editor">
        <!-- Tab 切换 -->
        <div class="flex items-center justify-between mb-2">
            <div v-if="label" class="text-sm font-semibold text-slate-700">
                {{ label }}
            </div>
            <div class="flex rounded-lg bg-slate-100 p-0.5">
                <button
                    type="button"
                    :class="[
                        'rounded-md px-3 py-1 text-xs font-medium transition-all',
                        mode === 'edit'
                            ? 'bg-white text-slate-800 shadow-sm'
                            : 'text-slate-500 hover:text-slate-700',
                    ]"
                    @click="setEditMode"
                >
                    编辑
                </button>
                <button
                    type="button"
                    :class="[
                        'rounded-md px-3 py-1 text-xs font-medium transition-all',
                        mode === 'preview'
                            ? 'bg-white text-slate-800 shadow-sm'
                            : 'text-slate-500 hover:text-slate-700',
                    ]"
                    @click="setPreviewMode"
                >
                    预览
                </button>
            </div>
        </div>

        <!-- 内容区域容器：固定高度，包含编辑和预览 -->
        <div :class="contentContainerClass" :style="contentContainerStyle">
            <!-- 编辑区域 -->
            <textarea
                ref="textareaRef"
                v-show="mode === 'edit'"
                :value="modelValue"
                :placeholder="placeholder"
                :class="textareaClass"
                :style="textareaStyle"
                @input="handleInput"
            />

            <!-- 预览区域 -->
            <div v-show="mode === 'preview'" :class="previewClass" :style="previewStyle">
                <div v-if="modelValue" class="markdown-preview" v-html="renderedContent"></div>
                <div
                    v-else
                    class="flex items-center justify-center text-slate-400 italic"
                    :style="emptyStateStyle"
                >
                    暂无内容
                </div>
            </div>
        </div>

        <!-- 提示文字 -->
        <p v-if="hint" class="mt-1 text-xs text-slate-400">{{ hint }}</p>
    </div>
</template>

<style scoped>
.markdown-content-container {
    position: relative;
}

.markdown-content-container--fixed {
    overflow: hidden;
}

.markdown-editor textarea {
    line-height: 1.6;
    font-size: 14px;
}

.markdown-preview {
    line-height: 1.6;
    font-size: 14px;
}

/* 标题样式：尽量接近普通文本，减少跳动 */
.markdown-preview :deep(h1),
.markdown-preview :deep(h2),
.markdown-preview :deep(h3),
.markdown-preview :deep(h4),
.markdown-preview :deep(h5),
.markdown-preview :deep(h6) {
    font-weight: 600;
    margin: 0;
    padding: 0;
    color: #1e293b;
    line-height: 1.6;
}

.markdown-preview :deep(h1) {
    font-size: 14px;
}

.markdown-preview :deep(h2) {
    font-size: 14px;
}

.markdown-preview :deep(h3) {
    font-size: 14px;
}

.markdown-preview :deep(h4),
.markdown-preview :deep(h5),
.markdown-preview :deep(h6) {
    font-size: 14px;
}

/* 段落：移除 margin */
.markdown-preview :deep(p) {
    margin: 0;
    padding: 0;
}

/* 列表：最小化间距 */
.markdown-preview :deep(ul),
.markdown-preview :deep(ol) {
    margin: 0;
    padding-left: 1.5rem;
}

.markdown-preview :deep(li) {
    margin: 0;
    line-height: 1.6;
}

/* 行内代码 */
.markdown-preview :deep(code) {
    background: #e2e8f0;
    padding: 0.1rem 0.3rem;
    border-radius: 0.25rem;
    font-size: 13px;
}

/* 代码块 */
.markdown-preview :deep(pre) {
    background: #e2e8f0;
    padding: 0.5rem 0.75rem;
    border-radius: 0.375rem;
    overflow-x: auto;
    margin: 0.5rem 0;
}

.markdown-preview :deep(pre code) {
    background: transparent;
    padding: 0;
    font-size: 13px;
}

/* 链接 */
.markdown-preview :deep(a) {
    color: #2563eb;
    text-decoration: underline;
}

/* 引用 */
.markdown-preview :deep(blockquote) {
    border-left: 3px solid #cbd5e1;
    padding-left: 0.75rem;
    margin: 0;
    color: #64748b;
}

/* 加粗和斜体 */
.markdown-preview :deep(strong) {
    font-weight: 600;
}

.markdown-preview :deep(em) {
    font-style: italic;
}

/* 分割线 */
.markdown-preview :deep(hr) {
    border: none;
    border-top: 1px solid #e2e8f0;
    margin: 0.5rem 0;
}

/* 表格 */
.markdown-preview :deep(table) {
    width: 100%;
    border-collapse: collapse;
    margin: 0.5rem 0;
}

.markdown-preview :deep(th),
.markdown-preview :deep(td) {
    border: 1px solid #e2e8f0;
    padding: 0.25rem 0.5rem;
    text-align: left;
}

.markdown-preview :deep(th) {
    background: #f1f5f9;
    font-weight: 600;
}

/* 首个元素无顶部间距 */
.markdown-preview :deep(:first-child) {
    margin-top: 0;
}

/* 最后元素无底部间距 */
.markdown-preview :deep(:last-child) {
    margin-bottom: 0;
}
</style>

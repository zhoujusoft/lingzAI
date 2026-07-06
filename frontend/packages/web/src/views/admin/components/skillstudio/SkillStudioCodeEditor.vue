<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { EditorView, basicSetup } from 'codemirror';
import { EditorState, Compartment } from '@codemirror/state';
import { defaultHighlightStyle, syntaxHighlighting } from '@codemirror/language';
import { markdown } from '@codemirror/lang-markdown';
import { python } from '@codemirror/lang-python';
import { json } from '@codemirror/lang-json';
import { yaml } from '@codemirror/lang-yaml';

const props = defineProps({
    modelValue: {
        type: String,
        default: '',
    },
    filePath: {
        type: String,
        default: '',
    },
});

const emit = defineEmits(['update:modelValue']);

const editorRoot = ref(null);
let editorView = null;
const languageCompartment = new Compartment();

function resolveLanguageExtension(path) {
    const lower = String(path || '').toLowerCase();
    if (lower.endsWith('.md')) {
        return markdown();
    }
    if (lower.endsWith('.py')) {
        return python();
    }
    if (lower.endsWith('.json')) {
        return json();
    }
    if (lower.endsWith('.yaml') || lower.endsWith('.yml')) {
        return yaml();
    }
    return [];
}

const editorTheme = EditorView.theme({
    '&': {
        height: '100%',
        minHeight: '100%',
        backgroundColor: '#ffffff',
        color: '#1a1a2e',
        fontSize: '13px',
    },
    '.cm-scroller': {
        overflow: 'auto',
        fontFamily:
            'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace',
        lineHeight: '26px',
    },
    '.cm-content': {
        padding: '0 20px 16px',
        minHeight: '100%',
    },
    '.cm-gutters': {
        borderRight: '1px solid #e4e6ef',
        backgroundColor: '#f8fafc',
        color: '#8a8fa3',
        paddingTop: '0',
    },
    '.cm-activeLine': {
        backgroundColor: '#f8fafc',
    },
    '.cm-activeLineGutter': {
        backgroundColor: '#eef0f5',
    },
    '.cm-selectionBackground': {
        backgroundColor: 'rgba(96, 165, 250, 0.22) !important',
    },
    '.cm-cursor': {
        borderLeftColor: '#2563eb',
    },
    '.cm-focused': {
        outline: 'none',
    },
});

function createEditor() {
    if (!editorRoot.value) {
        return;
    }
    editorView = new EditorView({
        state: EditorState.create({
            doc: props.modelValue || '',
            extensions: [
                basicSetup,
                syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
                editorTheme,
                languageCompartment.of(resolveLanguageExtension(props.filePath)),
                EditorView.lineWrapping,
                EditorView.updateListener.of(update => {
                    if (!update.docChanged) {
                        return;
                    }
                    emit('update:modelValue', update.state.doc.toString());
                }),
            ],
        }),
        parent: editorRoot.value,
    });
}

watch(
    () => props.modelValue,
    value => {
        if (!editorView) {
            return;
        }
        const current = editorView.state.doc.toString();
        if (current === (value || '')) {
            return;
        }
        editorView.dispatch({
            changes: {
                from: 0,
                to: current.length,
                insert: value || '',
            },
        });
    }
);

watch(
    () => props.filePath,
    value => {
        if (!editorView) {
            return;
        }
        editorView.dispatch({
            effects: languageCompartment.reconfigure(resolveLanguageExtension(value)),
        });
    }
);

onMounted(() => {
    createEditor();
});

onBeforeUnmount(() => {
    editorView?.destroy();
    editorView = null;
});
</script>

<template>
    <div ref="editorRoot" class="skillstudio-code-editor"></div>
</template>

<style scoped>
.skillstudio-code-editor {
    height: 100%;
    min-height: 100%;
    background: #ffffff;
}
</style>

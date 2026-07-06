<template>
    <div class="file-preview-renderer min-h-0 flex-1 overflow-hidden bg-surface">
        <VueOfficePdf
            v-if="previewKind === 'pdf'"
            class="h-full overflow-auto bg-surface"
            :src="file.src"
        />
        <VueOfficeDocx
            v-else-if="previewKind === 'docx'"
            class="h-full overflow-auto bg-surface"
            :src="file.src"
        />
        <VueOfficeExcel
            v-else-if="previewKind === 'excel'"
            class="h-full overflow-auto bg-surface"
            :src="file.src"
        />
        <VueOfficePptx
            v-else-if="previewKind === 'pptx'"
            class="h-full overflow-auto bg-surface"
            :src="file.src"
        />
        <div
            v-else-if="previewKind === 'image'"
            class="flex h-full items-center justify-center overflow-auto bg-surface-alt p-4"
        >
            <img
                :src="file.src"
                :alt="file.fileName || file.title || '预览'"
                class="max-h-full max-w-full object-contain"
            />
        </div>
        <div v-else-if="previewKind === 'markdown'" class="h-full overflow-hidden bg-surface">
            <div
                v-if="textLoading"
                class="flex h-full items-center justify-center text-sm text-muted"
            >
                加载中...
            </div>
            <div
                v-else-if="textError"
                class="flex h-full items-center justify-center text-sm text-danger"
            >
                {{ textError }}
            </div>
            <iframe
                v-else
                class="h-full w-full bg-surface"
                :srcdoc="markdownSrcdoc"
                sandbox=""
            ></iframe>
        </div>
        <div v-else-if="previewKind === 'text'" class="h-full overflow-auto bg-surface p-4">
            <div
                v-if="textLoading"
                class="flex h-full items-center justify-center text-sm text-muted"
            >
                加载中...
            </div>
            <div
                v-else-if="textError"
                class="flex h-full items-center justify-center text-sm text-danger"
            >
                {{ textError }}
            </div>
            <pre v-else class="m-0 whitespace-pre-wrap break-words text-sm leading-6 text-body">{{
                textContent
            }}</pre>
        </div>
        <iframe
            v-else
            class="h-full w-full bg-surface"
            :src="file.src || null"
            :srcdoc="file.src ? null : file.html"
            sandbox="allow-scripts"
        ></iframe>
    </div>
</template>

<script>
import { defineAsyncComponent } from 'vue';
import { marked } from 'marked';

marked.setOptions({
    breaks: true,
    gfm: true,
});

const VueOfficePdf = defineAsyncComponent(() => import('@vue-office/pdf'));
const VueOfficeDocx = defineAsyncComponent(async () => {
    await import('@vue-office/docx/lib/index.css');
    return import('@vue-office/docx');
});
const VueOfficeExcel = defineAsyncComponent(async () => {
    await import('@vue-office/excel/lib/index.css');
    return import('@vue-office/excel');
});
const VueOfficePptx = defineAsyncComponent(() => import('@vue-office/pptx'));

export default {
    name: 'FilePreviewRenderer',
    components: {
        VueOfficePdf,
        VueOfficeDocx,
        VueOfficeExcel,
        VueOfficePptx,
    },
    props: {
        file: {
            type: Object,
            required: true,
        },
    },
    data() {
        return {
            textContent: '',
            textLoading: false,
            textError: '',
        };
    },
    computed: {
        fileName() {
            return String(this.file?.fileName || this.file?.title || '').toLowerCase();
        },
        contentType() {
            return String(this.file?.contentType || '').toLowerCase();
        },
        previewKind() {
            if (this.contentType.includes('pdf') || this.fileName.endsWith('.pdf')) return 'pdf';
            if (this.fileName.endsWith('.docx')) return 'docx';
            if (
                this.contentType.includes('spreadsheet') ||
                this.fileName.endsWith('.xlsx') ||
                this.fileName.endsWith('.xls')
            ) {
                return 'excel';
            }
            if (this.fileName.endsWith('.pptx')) return 'pptx';
            if (this.contentType.startsWith('image/') || this.isImageFile) return 'image';
            if (this.isHtmlFile) return 'iframe';
            if (this.isMarkdownFile) return 'markdown';
            if (this.isTextFile) return 'text';
            return 'iframe';
        },
        isImageFile() {
            return ['.png', '.jpg', '.jpeg', '.gif', '.webp', '.svg'].some(ext =>
                this.fileName.endsWith(ext)
            );
        },
        isHtmlFile() {
            return (
                this.contentType.includes('text/html') ||
                this.fileName.endsWith('.html') ||
                this.fileName.endsWith('.htm')
            );
        },
        isMarkdownFile() {
            return (
                this.contentType.includes('markdown') ||
                this.fileName.endsWith('.md') ||
                this.fileName.endsWith('.markdown')
            );
        },
        isTextFile() {
            return (
                (this.contentType.startsWith('text/') && !this.isHtmlFile) ||
                this.contentType === 'application/json' ||
                ['.json', '.txt', '.log', '.csv'].some(ext => this.fileName.endsWith(ext))
            );
        },
        markdownSrcdoc() {
            const base = this.markdownBaseUrl
                ? `<base href="${this.escapeHtmlAttribute(this.markdownBaseUrl)}">`
                : '';
            const body = marked.parse(this.textContent || '');
            return `<!doctype html><html><head><meta charset="utf-8"><style>
                body{margin:0;padding:16px;font:14px/1.65 -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;color:#1f2937;background:#fff}
                pre{overflow:auto;background:#f8fafc;border:1px solid #e5e7eb;border-radius:6px;padding:12px}
                code{font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,monospace}
                img{max-width:100%}
                a{color:#2563eb;text-decoration:none}a:hover{text-decoration:underline}
                blockquote{margin:12px 0;padding:8px 12px;border-left:3px solid #cbd5e1;background:#f8fafc;color:#475569}
                table{border-collapse:collapse}td,th{border:1px solid #e5e7eb;padding:6px 8px}
            </style>${base}</head><body>${body}</body></html>`;
        },
        markdownBaseUrl() {
            if (!this.file?.src || typeof window === 'undefined') return '';
            try {
                return new URL(this.file.src, window.location.href).href;
            } catch {
                return '';
            }
        },
        textSourceKey() {
            return `${this.file?.src || ''}::${this.previewKind}`;
        },
    },
    watch: {
        textSourceKey: {
            immediate: true,
            handler() {
                this.loadTextIfNeeded();
            },
        },
    },
    methods: {
        async loadTextIfNeeded() {
            this.textContent = '';
            this.textError = '';
            if (!['text', 'markdown'].includes(this.previewKind)) {
                return;
            }
            if (this.file?.html && !this.file?.src) {
                this.textContent = String(this.file.html || '');
                return;
            }
            if (!this.file?.src) {
                return;
            }
            this.textLoading = true;
            try {
                const response = await fetch(this.file.src);
                if (!response.ok) {
                    throw new Error('预览内容加载失败');
                }
                this.textContent = await response.text();
            } catch (error) {
                this.textError = error?.message || '预览内容加载失败';
            } finally {
                this.textLoading = false;
            }
        },
        escapeHtmlAttribute(value) {
            return String(value)
                .replace(/&/g, '&amp;')
                .replace(/"/g, '&quot;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;');
        },
    },
};
</script>

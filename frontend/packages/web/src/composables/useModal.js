import {
    ModalActionsFooterSection,
    ModalHeaderSection,
    ModalMessageContentSection,
    ModalPromptContentSection,
} from '@/components/feedback/sections/modalSections';
import {
    MODAL_DANGEROUS_INTENTS,
    MODAL_FOOTER_LAYOUT,
} from '@/components/feedback/constants/modalEnums';
import { markRaw, reactive } from 'vue';

const defaultConfirmOptions = {
    title: '请确认操作',
    message: '',
    confirmText: '确认',
    cancelText: '取消',
    destructive: false,
};

const defaultAlertOptions = {
    title: '提示',
    message: '',
    confirmText: '确认',
    destructive: false,
};

const defaultPromptOptions = {
    title: '请输入内容',
    message: '',
    confirmText: '确认',
    cancelText: '取消',
    placeholder: '请输入',
    initialValue: '',
    destructive: false,
};

export const overlayState = reactive({
    stack: [],
});

const overlayQueue = [];
let nextOverlayId = 1;

function normalizeSection(section) {
    if (!section) {
        return section;
    }

    return {
        ...section,
        component: section.component ? markRaw(section.component) : section.component,
    };
}

function normalizeModal(modal) {
    if (!modal) {
        return modal;
    }

    return {
        ...modal,
        panelClass: modal.panelClass || '',
        constrainedHeight: Boolean(modal.constrainedHeight),
        contentScrollable: Boolean(modal.contentScrollable),
        header: normalizeSection(modal.header),
        content: normalizeSection(modal.content),
        footer: normalizeSection(modal.footer),
    };
}

function openNextOverlay() {
    if (overlayState.stack.length > 0 || !overlayQueue.length) {
        return;
    }

    const nextRequest = overlayQueue.shift();
    activateRequest(nextRequest);
}

function activateRequest(request) {
    request.open = true;
    overlayState.stack.push(request);
}

function enqueueOverlay(modal, { stackOnActive = false } = {}) {
    const normalizedModal = normalizeModal(modal);

    return new Promise(resolve => {
        const request = {
            id: nextOverlayId++,
            modal: normalizedModal,
            resolve,
            open: false,
            result: false,
        };
        if (stackOnActive && overlayState.stack.length > 0) {
            activateRequest(request);
            return;
        }
        overlayQueue.push(request);
        openNextOverlay();
    });
}

function resolveSection(section, fallback) {
    if (section === null) {
        return null;
    }

    if (section == null) {
        return fallback;
    }

    if (typeof section === 'object' && (section.component || section.props)) {
        return {
            component: section.component ?? fallback.component,
            props: {
                ...(fallback.props || {}),
                ...(section.props || {}),
            },
        };
    }

    return {
        component: section,
        props: {
            ...(fallback.props || {}),
        },
    };
}

function buildModalBySchema(schema) {
    return {
        context: schema.context || {},
        panelClass: schema.panelClass || '',
        constrainedHeight: Boolean(schema.constrainedHeight),
        contentScrollable: Boolean(schema.contentScrollable),
        header: schema.header,
        content: schema.content,
        footer: schema.footer,
    };
}

function inferDestructiveIntent(text = '') {
    return MODAL_DANGEROUS_INTENTS.some(keyword => text.includes(keyword));
}

export function openModal(options = {}) {
    /**
     * Generic modal entry.
     * - `content` is required: component or { component, props }.
     * - `header/footer` are optional: use `null` to hide, omit to use defaults.
     *
     * Example:
     * await openModal({
     *   title: '新增用户',
     *   content: { component: UserFormContent },
     *   context: { name: '' },
     *   resolveWith: ctx => ({ ...ctx }),
     * });
     */
    if (!options.content) {
        throw new Error('openModal requires `content` (component or { component, props }).');
    }

    const {
        content,
        header,
        footer,
        context = {},
        title = '提示',
        showClose = false,
        confirmText = '确认',
        cancelText = '取消',
        showCancel = true,
        destructive = false,
        resolveWith = null,
        panelClass = '',
        stackOnActive = false,
        replaceActive = false,
        constrainedHeight = false,
        contentScrollable = false,
    } = options;

    const defaultHeader = {
        component: ModalHeaderSection,
        props: {
            title,
            showClose,
        },
    };
    const defaultFooter = {
        component: ModalActionsFooterSection,
        props: {
            confirmText,
            cancelText,
            showCancel,
            destructive,
            layout: showClose ? MODAL_FOOTER_LAYOUT.PANEL : MODAL_FOOTER_LAYOUT.COMPACT,
            resolveWith,
        },
    };

    const modal = buildModalBySchema({
        context,
        panelClass,
        constrainedHeight,
        contentScrollable,
        header: resolveSection(header, defaultHeader),
        content: resolveSection(content, {
            component: ModalMessageContentSection,
            props: {},
        }),
        footer: resolveSection(footer, defaultFooter),
    });

    return enqueueOverlay(modal, {
        stackOnActive: stackOnActive || replaceActive,
    });
}

export function resolveActiveOverlay(result = true) {
    const topRequest = overlayState.stack[overlayState.stack.length - 1];
    if (!topRequest || !topRequest.open) {
        return;
    }
    resolveOverlayById(topRequest.id, result);
}

export function resolveOverlayById(requestId, result = true) {
    const request = overlayState.stack.find(item => item.id === requestId);
    if (!request || !request.open) {
        return;
    }
    request.result = result;
    request.open = false;
}

export function finalizeOverlayById(requestId) {
    const requestIndex = overlayState.stack.findIndex(item => item.id === requestId);
    if (requestIndex < 0) {
        return;
    }
    const [completedRequest] = overlayState.stack.splice(requestIndex, 1);
    completedRequest?.resolve?.(completedRequest.result);

    if (overlayState.stack.length === 0) {
        queueMicrotask(openNextOverlay);
    }
}

export function confirm(options = {}) {
    // Quick confirm dialog. Resolves: true on confirm, false on cancel/close.
    const resolved = {
        ...defaultConfirmOptions,
        ...options,
    };

    const hasDestructiveOption = Object.prototype.hasOwnProperty.call(options, 'destructive');
    const destructive = hasDestructiveOption
        ? resolved.destructive
        : inferDestructiveIntent(`${resolved.title}${resolved.confirmText}${resolved.message}`);

    return openModal({
        title: resolved.title,
        content: {
            component: ModalMessageContentSection,
            props: {
                message: resolved.message,
            },
        },
        confirmText: resolved.confirmText,
        cancelText: resolved.cancelText,
        destructive,
        showCancel: true,
        showClose: false,
    });
}

export function alert(options = {}) {
    // One-button alert dialog. Resolves true when acknowledged.
    const resolved = {
        ...defaultAlertOptions,
        ...options,
    };

    return openModal({
        title: resolved.title,
        content: {
            component: ModalMessageContentSection,
            props: {
                message: resolved.message,
            },
        },
        confirmText: resolved.confirmText,
        destructive: resolved.destructive,
        showCancel: false,
        showClose: false,
    });
}

export function prompt(options = {}) {
    // Input prompt dialog. Resolves input string on confirm, false on cancel/close.
    const resolved = {
        ...defaultPromptOptions,
        ...options,
    };

    return openModal({
        title: resolved.title,
        content: {
            component: ModalPromptContentSection,
            props: {
                placeholder: resolved.placeholder,
            },
        },
        confirmText: resolved.confirmText,
        cancelText: resolved.cancelText,
        destructive: resolved.destructive,
        showCancel: true,
        showClose: false,
        context: {
            value: resolved.initialValue || '',
        },
        resolveWith: ctx => ctx.value,
    });
}

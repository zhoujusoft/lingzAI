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

const defaultState = {
    open: false,
    modal: null,
};

export const overlayState = reactive({
    active: {
        ...defaultState,
    },
});

const overlayQueue = [];
let activeResolver = null;

function resetActiveState() {
    Object.assign(overlayState.active, defaultState);
}

function applyRequest(request) {
    overlayState.active.modal = request.modal;
    overlayState.active.open = true;
}

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
        header: normalizeSection(modal.header),
        content: normalizeSection(modal.content),
        footer: normalizeSection(modal.footer),
    };
}

function openNextOverlay() {
    if (overlayState.active.open || !overlayQueue.length) {
        return;
    }

    const nextRequest = overlayQueue.shift();
    activeResolver = nextRequest.resolve;
    applyRequest(nextRequest);
}

function enqueueOverlay(modal) {
    const normalizedModal = normalizeModal(modal);

    return new Promise(resolve => {
        overlayQueue.push({
            modal: normalizedModal,
            resolve,
        });
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
        header: resolveSection(header, defaultHeader),
        content: resolveSection(content, {
            component: ModalMessageContentSection,
            props: {},
        }),
        footer: resolveSection(footer, defaultFooter),
    });

    return enqueueOverlay(modal);
}

export function resolveActiveOverlay(result = true) {
    // Called by OverlayHost on confirm/cancel to close current modal and resolve caller's Promise.
    const resolver = activeResolver;
    activeResolver = null;
    resetActiveState();

    if (resolver) {
        resolver(result);
    }

    queueMicrotask(openNextOverlay);
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

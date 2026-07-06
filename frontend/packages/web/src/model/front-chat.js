export const ChatMessageKind = Object.freeze({
    user: 'user',
    assistant: 'assistant',
});

export const ChatMessageRender = Object.freeze({
    plain: 'plain',
    markdown: 'markdown',
});

export const ChatStreamEventType = Object.freeze({
    done: 'done',
    meta: 'meta',
    phasePlan: 'phase-plan',
    phaseProgress: 'phase-progress',
    runtimeEngine: 'runtime-engine',
    approval: 'approval',
    formArtifact: 'form-artifact',
    formPreview: 'form-preview',
    workflowPreview: 'workflow-preview',
    tool: 'tool',
    skill: 'skill',
    result: 'result',
    citation: 'citation',
    fallbackNotice: 'fallback_notice',
    error: 'error',
    message: 'message',
    answer: 'answer',
});

export const ChatToolStatus = Object.freeze({
    running: 'running',
    done: 'done',
    interrupted: 'interrupted',
});

export const ChatSegmentType = Object.freeze({
    tool: 'tool',
    runtimeEngine: 'runtime-engine',
    approval: 'approval',
    artifact: 'artifact',
    citation: 'citation',
    fallbackNotice: 'fallback_notice',
    text: 'text',
});

export const ChatApprovalStatus = Object.freeze({
    pending: 'pending',
    approving: 'approving',
    approved: 'approved',
    rejected: 'rejected',
    failed: 'failed',
});

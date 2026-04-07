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
    citation: 'citation',
    fallbackNotice: 'fallback_notice',
    text: 'text',
});

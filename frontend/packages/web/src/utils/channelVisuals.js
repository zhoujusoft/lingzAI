export const CHANNEL_LOGO_SOURCES = Object.freeze({
    weixin: '/channel-weixin.png',
    wecom: '/channel-wecom.png',
    dingtalk: '/channel-dingtalk.png',
});

const CHANNEL_LOGOS = [
    {
        aliases: ['wecom', 'wechat_work', 'work_wechat', '企业微信', '企微'],
        logo: CHANNEL_LOGO_SOURCES.wecom,
    },
    {
        aliases: ['dingtalk', 'dingding', '钉钉'],
        logo: CHANNEL_LOGO_SOURCES.dingtalk,
    },
    {
        aliases: ['weixin', 'wechat', '微信'],
        logo: CHANNEL_LOGO_SOURCES.weixin,
    },
];

function normalizeChannelText(value) {
    return String(value || '')
        .trim()
        .toLowerCase();
}

export function resolveChannelLogo(channelType, sourceLabel) {
    const candidates = [channelType, sourceLabel].map(normalizeChannelText).filter(Boolean);
    const match = CHANNEL_LOGOS.find(item =>
        item.aliases.some(alias =>
            candidates.some(candidate => candidate.includes(alias.toLowerCase()))
        )
    );
    return match?.logo || '';
}

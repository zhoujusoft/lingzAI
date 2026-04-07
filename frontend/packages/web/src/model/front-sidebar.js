import { ROUTE_PATHS } from '@/router/routePaths';

export const FRONT_HOME_VIEWS = Object.freeze({
    CHAT: 'chat',
    SKILLS: 'skills',
    INTELLIGENT_CHAT: 'intelligent-chat',
    DATASET_CHAT: 'dataset-chat',
});

export const FRONT_SIDEBAR_ITEMS = Object.freeze([
    {
        id: FRONT_HOME_VIEWS.CHAT,
        label: '聊天',
        icon: 'chat_bubble',
        view: FRONT_HOME_VIEWS.CHAT,
        path: ROUTE_PATHS.frontChat,
    },
    {
        id: FRONT_HOME_VIEWS.SKILLS,
        label: '技能市场',
        icon: 'widgets',
        view: FRONT_HOME_VIEWS.SKILLS,
        path: ROUTE_PATHS.frontSkills,
    },
    {
        id: FRONT_HOME_VIEWS.INTELLIGENT_CHAT,
        label: '智能问答',
        icon: 'chat',
        view: FRONT_HOME_VIEWS.INTELLIGENT_CHAT,
        path: ROUTE_PATHS.frontIntelligentChat,
    },
    {
        id: FRONT_HOME_VIEWS.DATASET_CHAT,
        label: '智能问数',
        icon: 'database',
        view: FRONT_HOME_VIEWS.DATASET_CHAT,
        path: ROUTE_PATHS.frontDatasetChat,
    },
]);

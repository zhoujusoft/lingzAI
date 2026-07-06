import {
    canChangeResourcePermission,
    canOperateResource,
    isAdminUser,
} from '@/model/resource-permissions';

export function canOperateKnowledgeBase(knowledge, profile) {
    return canOperateResource(knowledge, profile);
}

export function canChangeKnowledgePermission(knowledge, profile) {
    return canChangeResourcePermission(knowledge, profile);
}

export { isAdminUser };

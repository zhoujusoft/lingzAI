<script setup>
import { computed, ref, onMounted, onUnmounted, inject } from 'vue';
import { useRouter } from 'vue-router';
import { currentUserState, logoutCurrentUser } from '@/composables/useCurrentUser';
import { brandingState, ensureBrandingLoaded } from '@/composables/useBranding';
import { agentConfigState, ensureAgentConfigLoaded } from '@/composables/useAgentConfig';
import defaultAvatarSrc from '@/assets/images/default-avatar.svg';
import { resolveSkillIcon, getSkillIconGradientClass } from '@/utils/skillVisuals';
import {
    AGENT_INSIGHT_SECTIONS,
    formatAgentInsightBadgeCount,
    getAgentInsightBadgeCount,
} from '@/utils/agentInsight';
import { resolveUserAvatarUrl } from '@/utils/userAvatar';
import { ROUTE_PATHS } from '@/router/routePaths';
import { confirm } from '@/composables/useModal';
import { hasAnyAdminPermission } from '@/model/admin-menu-permissions';

const props = defineProps({
    insightOpen: {
        type: Boolean,
        default: false,
    },
});

const emit = defineEmits(['toggle-insight']);

const router = useRouter();

// inject: 用于通知 Workspace 选择技能
const pendingSkillSelect = inject('pendingSkillSelect', ref(null));

// 用户信息
const userName = computed(() => {
    return currentUserState.profile?.name || currentUserState.name || '用户';
});
const userAvatarUrl = computed(() =>
    resolveUserAvatarUrl(currentUserState.profile, defaultAvatarSrc)
);

// 是否显示跳转后台按钮
const showAdminSwitch = computed(() => {
    return hasAnyAdminPermission(currentUserState.profile);
});

// 技能下拉菜单状态
const showSkillMenu = ref(false);
const skillMenuRef = ref(null);

// 用户下拉菜单状态
const showUserMenu = ref(false);
const userMenuRef = ref(null);

const insightBadgeCount = computed(() => getAgentInsightBadgeCount(AGENT_INSIGHT_SECTIONS));
const insightBadgeLabel = computed(() => formatAgentInsightBadgeCount(insightBadgeCount.value));

// 切换技能菜单显示
function toggleSkillMenu() {
    showSkillMenu.value = !showSkillMenu.value;
    showUserMenu.value = false;
}

// 切换用户菜单显示
function toggleUserMenu() {
    showUserMenu.value = !showUserMenu.value;
    showSkillMenu.value = false;
}

// 跳转后台（新标签页）
function goToAdmin() {
    showUserMenu.value = false;
    const resolved = router.resolve(ROUTE_PATHS.adminHome);
    window.open(resolved.href, '_blank', 'noopener');
}

// 退出登录
async function handleLogout() {
    const shouldLogout = await confirm({
        title: '退出登录',
        message: '确认退出当前账号吗？',
        confirmText: '退出',
        cancelText: '取消',
    });
    if (!shouldLogout) {
        return;
    }
    showUserMenu.value = false;
    await logoutCurrentUser();
    router.replace(ROUTE_PATHS.login);
}

// 点击技能：通知 Workspace 选中该技能
function handleSkillClick(skill) {
    if (!skill?.id) {
        return;
    }
    // 设置待选技能，Workspace 会监听并处理
    pendingSkillSelect.value = {
        id: skill.id,
        runtimeSkillName: skill.runtimeSkillName,
        displayName: skill.displayName || skill.runtimeSkillName,
    };
    // 关闭下拉菜单
    showSkillMenu.value = false;
}

// 点击外部关闭菜单
function handleClickOutside(event) {
    if (skillMenuRef.value && !skillMenuRef.value.contains(event.target)) {
        showSkillMenu.value = false;
    }
    if (userMenuRef.value && !userMenuRef.value.contains(event.target)) {
        showUserMenu.value = false;
    }
}

onMounted(() => {
    ensureBrandingLoaded();
    ensureAgentConfigLoaded();
    document.addEventListener('click', handleClickOutside);
});

onUnmounted(() => {
    document.removeEventListener('click', handleClickOutside);
});
</script>

<template>
    <header class="agent-chat-header shrink-0 px-4 h-16 flex items-center justify-between">
        <!-- Left Section: Brand -->
        <div class="flex items-center gap-3 shrink-0">
            <img
                :src="brandingState.logoUrl"
                :alt="brandingState.systemName"
                class="h-9 w-9 object-contain"
            />
            <h1 class="text-[20px] font-bold text-[#16216a] tracking-tight">AI 工作台</h1>
        </div>

        <!-- Right Section: Actions -->
        <div class="flex items-center gap-4">
            <!-- Skills Dropdown -->
            <div ref="skillMenuRef" class="skill-dropdown-wrapper">
                <button
                    type="button"
                    class="compact-top-btn"
                    :class="showSkillMenu ? 'compact-top-btn--active' : ''"
                    @click.stop="toggleSkillMenu"
                >
                    <span
                        class="material-symbols-outlined text-[18px] transition-colors duration-150"
                        :class="showSkillMenu ? 'text-[#6da8ff]' : 'text-slate-500'"
                        >grid_view</span
                    >
                    <span
                        class="text-[13px] font-medium transition-colors duration-150"
                        :class="showSkillMenu ? 'text-[#5b4fcf]' : 'text-slate-700'"
                        >技能</span
                    >
                    <span
                        class="material-symbols-outlined text-[14px] text-slate-400 transition-all duration-150"
                        :class="{ 'rotate-180 text-[#7b5cff]': showSkillMenu }"
                        >expand_more</span
                    >
                </button>

                <!-- Dropdown Menu -->
                <Transition name="skill-menu">
                    <div v-if="showSkillMenu" class="skill-dropdown-menu">
                        <!-- Loading State -->
                        <div v-if="agentConfigState.loading" class="skill-menu-loading">
                            <span class="material-symbols-outlined animate-spin text-[18px]"
                                >progress_activity</span
                            >
                            <span>加载中...</span>
                        </div>

                        <!-- Skill List -->
                        <template v-else-if="agentConfigState.skills.length > 0">
                            <div
                                v-for="skill in agentConfigState.skills"
                                :key="skill.id"
                                class="skill-menu-item"
                                @click.stop="handleSkillClick(skill)"
                            >
                                <div
                                    class="skill-item-icon"
                                    :class="getSkillIconGradientClass(skill?.iconColor)"
                                >
                                    <span
                                        class="material-symbols-outlined text-[16px] text-white"
                                        >{{ resolveSkillIcon(skill?.icon) }}</span
                                    >
                                </div>
                                <div class="skill-item-content">
                                    <span class="skill-item-name">{{
                                        skill.displayName || skill.runtimeSkillName || '未命名技能'
                                    }}</span>
                                    <span v-if="skill.category" class="skill-item-category">{{
                                        skill.category
                                    }}</span>
                                </div>
                            </div>
                        </template>

                        <!-- Empty State -->
                        <div v-else class="skill-menu-empty">
                            <span class="material-symbols-outlined text-[20px] text-slate-300"
                                >widgets</span
                            >
                            <span>暂无配置技能</span>
                        </div>
                    </div>
                </Transition>
            </div>

            <button
                type="button"
                class="compact-top-btn relative"
                :class="insightOpen ? 'compact-top-btn--active' : ''"
                @click="emit('toggle-insight')"
            >
                <span
                    class="material-symbols-outlined text-[18px] transition-colors duration-150"
                    :class="insightOpen ? 'text-[#6da8ff]' : 'text-slate-500'"
                    >notifications</span
                >
                <span
                    class="text-[13px] font-medium transition-colors duration-150"
                    :class="insightOpen ? 'text-[#5b4fcf]' : 'text-slate-700'"
                    >AI洞察提醒</span
                >
                <span v-if="insightBadgeLabel" class="compact-notification-badge">{{
                    insightBadgeLabel
                }}</span>
            </button>

            <!-- User Avatar Box -->
            <div ref="userMenuRef" class="user-dropdown-wrapper">
                <div
                    class="compact-top-btn cursor-pointer group"
                    :class="showUserMenu ? 'compact-top-btn--active' : ''"
                    @click.stop="toggleUserMenu"
                >
                    <img
                        alt="User avatar"
                        class="w-6 h-6 rounded-full object-cover ring-1 ring-slate-200 transition-all duration-150"
                        :class="showUserMenu ? 'ring-[#6da8ff]' : ''"
                        :src="userAvatarUrl"
                    />
                    <span
                        class="text-[13px] font-medium transition-colors duration-150"
                        :class="showUserMenu ? 'text-[#5b4fcf]' : 'text-slate-700'"
                        >{{ userName }}</span
                    >
                    <span
                        class="material-symbols-outlined text-[14px] text-slate-400 transition-all duration-150"
                        :class="{ 'rotate-180 text-[#7b5cff]': showUserMenu }"
                        >expand_more</span
                    >
                </div>

                <!-- User Dropdown Menu -->
                <Transition name="user-menu">
                    <div v-if="showUserMenu" class="user-dropdown-menu">
                        <!-- 跳转后台 -->
                        <button
                            v-if="showAdminSwitch"
                            type="button"
                            class="user-menu-item"
                            @click="goToAdmin"
                        >
                            <span class="material-symbols-outlined text-[18px] text-slate-500"
                                >dashboard</span
                            >
                            <span class="user-menu-item-text">跳转后台</span>
                        </button>

                        <!-- 分割线 -->
                        <div v-if="showAdminSwitch" class="user-menu-divider"></div>

                        <!-- 退出登录 -->
                        <button
                            type="button"
                            class="user-menu-item user-menu-item-danger"
                            @click="handleLogout"
                        >
                            <span class="material-symbols-outlined text-[18px]">logout</span>
                            <span class="user-menu-item-text">退出登录</span>
                        </button>
                    </div>
                </Transition>
            </div>
        </div>
    </header>
</template>

<style scoped>
.agent-chat-header {
    background: #ffffff;
    border-bottom: 1px solid #e2e8f0;
    z-index: 50;
}

/* 简约商务按钮：与页面整体风格协调 */
.compact-top-btn {
    position: relative;
    background: #ffffff;
    border-radius: 10px;
    height: 36px;
    padding: 0 12px;
    display: flex;
    align-items: center;
    gap: 6px;
    cursor: pointer;
    border: 1px solid #e2e8f0;
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
    transition: all 0.15s ease;
}

.compact-top-btn:hover {
    background: #f8fafc;
    border-color: #cbd5e1;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.06);
}

/* 激活状态：使用页面统一的蓝紫渐变主题 */
.compact-top-btn--active {
    background: linear-gradient(135deg, rgba(109, 168, 255, 0.12), rgba(123, 92, 255, 0.12));
    border-color: rgba(109, 168, 255, 0.5);
    box-shadow: 0 1px 3px rgba(109, 168, 255, 0.2);
}

.compact-top-btn--active:hover {
    background: linear-gradient(135deg, rgba(109, 168, 255, 0.18), rgba(123, 92, 255, 0.18));
    border-color: rgba(109, 168, 255, 0.6);
}

.compact-notification-badge {
    position: absolute;
    top: -4px;
    right: -4px;
    min-width: 16px;
    height: 16px;
    background: linear-gradient(135deg, #fb7185, #f43f5e);
    color: #ffffff;
    font-size: 10px;
    font-weight: 600;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 2px solid #ffffff;
    box-shadow: 0 2px 4px rgba(244, 63, 94, 0.3);
}

.skill-dropdown-wrapper {
    position: relative;
}

.skill-dropdown-menu {
    position: absolute;
    top: calc(100% + 8px);
    right: 0;
    min-width: 240px;
    max-width: 320px;
    background: #ffffff;
    border-radius: 16px;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
    border: 1px solid #e8ecf4;
    padding: 8px;
    z-index: 100;
    overflow: hidden;
}

.skill-menu-loading,
.skill-menu-empty {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 16px;
    color: #94a3b8;
    font-size: 13px;
}

.skill-menu-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 12px;
    border-radius: 10px;
    cursor: pointer;
    transition: background 0.15s ease;
}

.skill-menu-item:hover {
    background: #f7f8fc;
}

.skill-item-icon {
    width: 32px;
    height: 32px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.skill-item-content {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
}

.skill-item-name {
    font-size: 14px;
    font-weight: 600;
    color: #1e275f;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.skill-item-category {
    font-size: 12px;
    color: #6b7280;
}

.user-dropdown-wrapper {
    position: relative;
}

.user-dropdown-menu {
    position: absolute;
    top: calc(100% + 8px);
    right: 0;
    min-width: 180px;
    background: #ffffff;
    border-radius: 16px;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
    border: 1px solid #e8ecf4;
    padding: 8px;
    z-index: 100;
    overflow: hidden;
}

.user-menu-item {
    display: flex;
    align-items: center;
    gap: 12px;
    width: 100%;
    padding: 10px 12px;
    border-radius: 10px;
    border: none;
    background: transparent;
    cursor: pointer;
    transition: background 0.15s ease;
}

.user-menu-item:hover {
    background: #f7f8fc;
}

.user-menu-item-text {
    font-size: 14px;
    font-weight: 500;
    color: #1e275f;
}

.user-menu-item-danger .user-menu-item-text {
    color: #ef4444;
}

.user-menu-item-danger:hover {
    background: #fef2f2;
}

.user-menu-divider {
    height: 1px;
    background: #e8ecf4;
    margin: 6px 4px;
}

.skill-menu-enter-active,
.skill-menu-leave-active {
    transition: all 0.2s ease;
}

.skill-menu-enter-from,
.skill-menu-leave-to {
    opacity: 0;
    transform: translateY(-8px);
}

.user-menu-enter-active,
.user-menu-leave-active {
    transition: all 0.2s ease;
}

.user-menu-enter-from,
.user-menu-leave-to {
    opacity: 0;
    transform: translateY(-8px);
}

@media (max-width: 1024px) {
    .agent-chat-header {
        padding-left: 1rem;
        padding-right: 1rem;
        height: 56px;
    }

    .compact-top-btn {
        padding: 0 10px;
        height: 32px;
    }

    .compact-top-btn span:not(.material-symbols-outlined) {
        display: none;
    }
}
</style>

export const AGENT_INSIGHT_SECTIONS = [
    {
        id: 'focus',
        index: 1,
        title: '重点事项',
        tone: 'blue',
        groups: [
            {
                title: '会议提醒',
                icon: 'calendar_month',
                items: [
                    {
                        msg: '9:30在308会议室参加”数字化经营分析会”',
                        query: '请查询“数字化经营分析会”的会议详情，包括会议时间、会议室、参会人员和会议内容。',
                    },
                    {
                        msg: '11:00在305会议室参加”信息安全与网络安全专题会”。',
                        query: '请查询“信息安全与网络安全专题会”的会议详情，包括会议时间、会议室、参会人员和会议内容。',
                    },
                ],
                skill: '会议助手',
            },
            {
                title: '客户拜访',
                icon: 'person',
                items: [{ msg: '14:30有”湖北工建”客户到公司拜访。' }],
            },
        ],
    },
    {
        id: 'approval',
        index: 2,
        title: '重点审批',
        tone: 'amber',
        groups: [
            {
                title: '合同审批',
                icon: 'assignment',
                items: [
                    {
                        msg: '”湖北工建数据中台项目”销售合同待审批。',
                        query: '请查询“湖北工建数据中台项目”的销售合同审批详情。',
                    },
                ],
                skill: '项目管理助手',
            },
            {
                title: '费用审批',
                icon: 'currency_yen',
                items: [
                    {
                        msg: '”智慧园区项目”付款申请待审批。',
                        query: '请查询“智慧园区项目”的付款申请审批详情。',
                    },
                    {
                        msg: '云资源采购申请待审批。',
                        query: '请查询当前待审批的云资源采购申请详情。',
                    },
                ],
                skill: '项目管理助手',
            },
        ],
    },
    {
        id: 'warning',
        index: 3,
        title: '事项预警',
        tone: 'rose',
        groups: [
            {
                title: '招标预警',
                icon: 'warning',
                skill: '项目管理助手',
                items: [
                    {
                        msg: '湖北工建工程项目全生命周期管理系统预计下周招标。',
                        query: '工程项目全生命周期管理系统 项目现在跟进情况如何',
                    },
                    { msg: '武汉船舶集团财务共享中心数字化升级预计3天后招标。' },
                ],
            },
            {
                title: '回款预警',
                icon: 'currency_yen',
                items: [
                    {
                        msg: '湖北工建智慧园区项目超45天未回款，应收120万元。',
                        query: '请查询“湖北工建智慧园区项目”的超期回款详情，并判断是否需要创建催款提醒。',
                    },
                ],
                skill: '销售助手',
            },
            {
                title: '成本预警',
                icon: 'money_bag',
                items: [
                    {
                        msg: '武汉市智慧城市项目实施成本已超过计划10%。',
                        query: '请查询“武汉市智慧城市项目”的成本预警详情，说明超支项和当前状态。',
                    },
                ],
                skill: '项目管理助手',
            },
            {
                title: '项目风险',
                icon: 'warning',
                items: [
                    {
                        msg: '武汉市一网通办项目需求频繁变更，存在延期风险。',
                        query: '请查询“武汉市一网通办项目”的项目风险详情，重点说明延期风险原因和当前影响。',
                    },
                ],
                skill: '项目管理助手',
            },
        ],
    },
];

export function getAgentInsightBadgeCount(sections = AGENT_INSIGHT_SECTIONS) {
    return (Array.isArray(sections) ? sections : []).reduce((sectionTotal, section) => {
        const groupTotal = (Array.isArray(section?.groups) ? section.groups : []).reduce(
            (itemTotal, group) =>
                itemTotal + (Array.isArray(group?.items) ? group.items.length : 0),
            0
        );
        return sectionTotal + groupTotal;
    }, 0);
}

export function formatAgentInsightBadgeCount(count) {
    const normalizedCount = Number.isFinite(Number(count))
        ? Math.max(0, Math.trunc(Number(count)))
        : 0;
    if (normalizedCount > 99) {
        return '99+';
    }
    return normalizedCount > 0 ? String(normalizedCount) : '';
}

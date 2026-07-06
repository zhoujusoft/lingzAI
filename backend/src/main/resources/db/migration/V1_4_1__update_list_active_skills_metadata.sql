UPDATE `tool_catalog`
SET `display_name` = '查看可用技能',
    `description` = '列出当前用户可用的全部技能，并标记哪些技能已在当前 runtime 中加载。'
WHERE `tool_name` = 'listActiveSkills';

INSERT INTO `skill_catalog` (
    `runtime_skill_name`,
    `display_name`,
    `description`,
    `category`,
    `source`,
    `version`,
    `author`,
    `icon`,
    `icon_color`,
    `tool_binding_status`,
    `tool_binding_message`,
    `visible`,
    `sort_order`,
    `created_at`,
    `updated_at`
)
SELECT
    'sales-info-assistant',
    '销售信息助手',
    '面向销售角色，动态组织客户、商机、联系人与跟进历史等信息，基于通用页面 schema 生成可预览 HTML 页面。',
    '销售',
    'filesystem',
    '1.0',
    'zhouju',
    'support_agent',
    'blue',
    'NEEDS_REBIND',
    '请绑定销售信息助手所需的 5 个低代码 API 工具',
    1,
    194,
    NOW(),
    NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1
    FROM `skill_catalog`
    WHERE `runtime_skill_name` = 'sales-info-assistant'
);

UPDATE `skill_catalog`
SET
    `display_name` = '销售信息助手',
    `description` = '面向销售角色，动态组织客户、商机、联系人与跟进历史等信息，基于通用页面 schema 生成可预览 HTML 页面。',
    `category` = '销售',
    `source` = 'filesystem',
    `version` = '1.0',
    `author` = 'zhouju',
    `icon` = 'support_agent',
    `icon_color` = 'blue',
    `visible` = 1,
    `sort_order` = 194,
    `updated_at` = NOW()
WHERE `runtime_skill_name` = 'sales-info-assistant';

INSERT INTO `skill_tool_binding` (`skill_id`, `tool_name`, `binding_type`, `created_at`)
SELECT sc.`id`, tool_map.`tool_name`, 'MANUAL', NOW()
FROM `skill_catalog` sc
JOIN (
    SELECT 'lowcode.18986126297.jyzbkb_kehda-jcxx' AS `tool_name`
    UNION ALL SELECT 'lowcode.18986126297.jyzbkb_kehxsxx'
    UNION ALL SELECT 'lowcode.18986126297.jyzbkb_kehlxrxx'
    UNION ALL SELECT 'lowcode.18986126297.jyzbkb_shangjgjxx'
    UNION ALL SELECT 'lowcode.18986126297.jyzbkb_genjxszkhxx'
) tool_map
JOIN `tool_catalog` tc
    ON tc.`tool_name` = tool_map.`tool_name`
   AND tc.`bindable` = 1
WHERE sc.`runtime_skill_name` = 'sales-info-assistant'
  AND NOT EXISTS (
    SELECT 1
    FROM `skill_tool_binding` stb
    WHERE stb.`skill_id` = sc.`id`
      AND stb.`tool_name` = tool_map.`tool_name`
);

UPDATE `skill_tool_binding` stb
JOIN `skill_catalog` sc ON sc.`id` = stb.`skill_id`
SET stb.`binding_type` = 'MANUAL'
WHERE sc.`runtime_skill_name` = 'sales-info-assistant'
  AND stb.`tool_name` IN (
    'lowcode.18986126297.jyzbkb_kehda-jcxx',
    'lowcode.18986126297.jyzbkb_kehxsxx',
    'lowcode.18986126297.jyzbkb_kehlxrxx',
    'lowcode.18986126297.jyzbkb_shangjgjxx',
    'lowcode.18986126297.jyzbkb_genjxszkhxx'
  );

UPDATE `skill_catalog` sc
SET
    `tool_binding_status` = CASE
        WHEN (
            SELECT COUNT(*)
            FROM `skill_tool_binding` stb
            WHERE stb.`skill_id` = sc.`id`
              AND stb.`tool_name` IN (
                'lowcode.18986126297.jyzbkb_kehda-jcxx',
                'lowcode.18986126297.jyzbkb_kehxsxx',
                'lowcode.18986126297.jyzbkb_kehlxrxx',
                'lowcode.18986126297.jyzbkb_shangjgjxx',
                'lowcode.18986126297.jyzbkb_genjxszkhxx'
              )
        ) = 5 THEN 'READY'
        WHEN (
            SELECT COUNT(*)
            FROM `tool_catalog` tc
            WHERE tc.`tool_name` IN (
                'lowcode.18986126297.jyzbkb_kehda-jcxx',
                'lowcode.18986126297.jyzbkb_kehxsxx',
                'lowcode.18986126297.jyzbkb_kehlxrxx',
                'lowcode.18986126297.jyzbkb_shangjgjxx',
                'lowcode.18986126297.jyzbkb_genjxszkhxx'
            )
              AND tc.`bindable` = 1
        ) = 5 THEN 'NEEDS_REBIND'
        ELSE 'MISSING_DEPENDENCY'
    END,
    `tool_binding_message` = CASE
        WHEN (
            SELECT COUNT(*)
            FROM `skill_tool_binding` stb
            WHERE stb.`skill_id` = sc.`id`
              AND stb.`tool_name` IN (
                'lowcode.18986126297.jyzbkb_kehda-jcxx',
                'lowcode.18986126297.jyzbkb_kehxsxx',
                'lowcode.18986126297.jyzbkb_kehlxrxx',
                'lowcode.18986126297.jyzbkb_shangjgjxx',
                'lowcode.18986126297.jyzbkb_genjxszkhxx'
              )
        ) = 5 THEN NULL
        WHEN (
            SELECT COUNT(*)
            FROM `tool_catalog` tc
            WHERE tc.`tool_name` IN (
                'lowcode.18986126297.jyzbkb_kehda-jcxx',
                'lowcode.18986126297.jyzbkb_kehxsxx',
                'lowcode.18986126297.jyzbkb_kehlxrxx',
                'lowcode.18986126297.jyzbkb_shangjgjxx',
                'lowcode.18986126297.jyzbkb_genjxszkhxx'
            )
              AND tc.`bindable` = 1
        ) = 5 THEN '请重新绑定销售信息助手所需的 5 个低代码 API 工具'
        ELSE '请先注册并开放销售信息助手所需的 5 个低代码 API 工具'
    END,
    `tool_binding_details` = NULL,
    `updated_at` = NOW()
WHERE sc.`runtime_skill_name` = 'sales-info-assistant';

INSERT INTO `agent_template_skill_binding` (`template_id`, `skill_id`, `sort_order`, `created_at`)
SELECT at.`id`, sc.`id`, 221, NOW()
FROM `agent_template` at
JOIN `skill_catalog` sc
    ON sc.`runtime_skill_name` = 'sales-info-assistant'
WHERE at.`agent_code` = 'sales-assistant'
  AND NOT EXISTS (
    SELECT 1
    FROM `agent_template_skill_binding` atsb
    WHERE atsb.`template_id` = at.`id`
      AND atsb.`skill_id` = sc.`id`
  );

UPDATE `agent_template_skill_binding` atsb
JOIN `agent_template` at ON at.`id` = atsb.`template_id`
JOIN `skill_catalog` sc ON sc.`id` = atsb.`skill_id`
SET atsb.`sort_order` = 221
WHERE at.`agent_code` = 'sales-assistant'
  AND sc.`runtime_skill_name` = 'sales-info-assistant';

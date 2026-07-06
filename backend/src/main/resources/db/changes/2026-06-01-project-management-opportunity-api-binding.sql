INSERT INTO `skill_tool_binding` (`skill_id`, `tool_name`, `binding_type`, `created_at`)
SELECT sc.`id`, tool_map.`tool_name`, 'MANUAL', NOW()
FROM `skill_catalog` sc
JOIN (
  SELECT 'lowcode.11290.jyzbkb_kehxsxx' AS `tool_name`
  UNION ALL SELECT 'lowcode.11290.jyzbkb_shangjgjxx'
  UNION ALL SELECT 'lowcode.11290.jyzbkb_kehlxrxx'
  UNION ALL SELECT 'lowcode.11290.jyzbkb_genjxszkhxx'
) tool_map
JOIN `tool_catalog` tc
  ON tc.`tool_name` = tool_map.`tool_name`
 AND tc.`bindable` = 1
WHERE sc.`runtime_skill_name` = 'project-management-assistant'
  AND NOT EXISTS (
    SELECT 1
    FROM `skill_tool_binding` stb
    WHERE stb.`skill_id` = sc.`id`
      AND stb.`tool_name` = tool_map.`tool_name`
  );

UPDATE `skill_catalog`
SET
  `description` = '基于项目管理数据集查询云资源采购申请、云采购资源申请、采购申请审批、销售合同审批、合同付款、回款预警、成本预警和项目风险；线索/商机信息、联系人和跟进记录通过低代码 API 查询，并生成字段表格 HTML 右侧预览。',
  `updated_at` = NOW()
WHERE `runtime_skill_name` = 'project-management-assistant';

UPDATE `skill_catalog` sc
SET
  `tool_binding_status` = CASE
    WHEN (
      SELECT COUNT(*)
      FROM `skill_tool_binding` stb
      WHERE stb.`skill_id` = sc.`id`
        AND stb.`tool_name` IN (
          'dataset.DS202605191535356JGP.search_dataset_summary',
          'dataset.DS202605191535356JGP.get_dataset_schema',
          'dataset.DS202605191535356JGP.execute_dataset_sql',
          'lowcode.11290.jyzbkb_liuctj',
          'lowcode.11290.jyzbkb_kehxsxx',
          'lowcode.11290.jyzbkb_shangjgjxx',
          'lowcode.11290.jyzbkb_kehlxrxx',
          'lowcode.11290.jyzbkb_genjxszkhxx'
        )
    ) = 8 THEN 'READY'
    ELSE 'NEEDS_REBIND'
  END,
  `tool_binding_message` = CASE
    WHEN (
      SELECT COUNT(*)
      FROM `skill_tool_binding` stb
      WHERE stb.`skill_id` = sc.`id`
        AND stb.`tool_name` IN (
          'dataset.DS202605191535356JGP.search_dataset_summary',
          'dataset.DS202605191535356JGP.get_dataset_schema',
          'dataset.DS202605191535356JGP.execute_dataset_sql',
          'lowcode.11290.jyzbkb_liuctj',
          'lowcode.11290.jyzbkb_kehxsxx',
          'lowcode.11290.jyzbkb_shangjgjxx',
          'lowcode.11290.jyzbkb_kehlxrxx',
          'lowcode.11290.jyzbkb_genjxszkhxx'
        )
    ) = 8 THEN NULL
    ELSE '请绑定项目管理助手所需的项目管理数据集工具、流程提交工具以及4个线索/商机低代码 API 工具'
  END,
  `tool_binding_details` = NULL,
  `updated_at` = NOW()
WHERE sc.`runtime_skill_name` = 'project-management-assistant';

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
  'order-inventory-fulfillment',
  '订单履约协同助手',
  '面向销售与供应链协同场景，帮助业务人员根据客户采购需求联动库存校验，分步完成订单申请与发货申请确认，提升下单与履约效率。',
  '供应链',
  'filesystem',
  '1.0',
  'zhouju',
  'inventory_2',
  'green',
  'NEEDS_REBIND',
  '请绑定订单申请 API、发货申请 API 以及库存数据集摘要/结构/SQL 工具',
  1,
  191,
  NOW(),
  NOW()
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1
  FROM `skill_catalog`
  WHERE `runtime_skill_name` = 'order-inventory-fulfillment'
);

UPDATE `skill_catalog`
SET
  `display_name` = '订单履约协同助手',
  `description` = '面向销售与供应链协同场景，帮助业务人员根据客户采购需求联动库存校验，分步完成订单申请与发货申请确认，提升下单与履约效率。',
  `category` = '供应链',
  `source` = 'filesystem',
  `version` = '1.0',
  `author` = 'zhouju',
  `icon` = 'inventory_2',
  `icon_color` = 'green',
  `tool_binding_status` = 'NEEDS_REBIND',
  `tool_binding_message` = '请绑定订单申请 API、发货申请 API 以及库存数据集摘要/结构/SQL 工具',
  `visible` = 1,
  `sort_order` = 191,
  `updated_at` = NOW()
WHERE `runtime_skill_name` = 'order-inventory-fulfillment';

-- 手动绑定建议：
-- 1. 一个订单申请低代码 API 工具
-- 2. 一个发货申请低代码 API 工具
-- 3. 目标库存数据集的三类工具：
--    - <dataset-tool-prefix>.search_dataset_summary
--    - <dataset-tool-prefix>.get_dataset_schema
--    - <dataset-tool-prefix>.execute_dataset_sql

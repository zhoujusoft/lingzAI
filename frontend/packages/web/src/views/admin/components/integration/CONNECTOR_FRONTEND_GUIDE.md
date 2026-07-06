# 连接器前端规范说明

## 目的

这份文档用于约束连接器功能的后续前端修改，避免因为 AI 或人工对页面意图理解偏差，导致布局密度、控件风格、状态表达和交互结构被整体改乱。

规范来源有两部分：

- 项目前端规范：`.trellis/spec/frontend/execution-checklist.md`、`component-guidelines.md`、`style-guidelines.md`、`ui-style-cheatsheet.md`
- 当前连接器实现：本目录下 `AdminIntegrationConnector*`、`Integration*` 系列组件以及 `integrationConnectorShared.js`

## 适用范围

本规范覆盖以下文件：

- `AdminIntegrationConnectorListPanel.vue`
- `AdminIntegrationConnectorDetailPanel.vue`
- `AdminIntegrationConnectorAuthSection.vue`
- `AdminIntegrationConnectorApiSection.vue`
- `IntegrationParamTable.vue`
- `IntegrationVariableInput.vue`
- `IntegrationVariableNamedValueEditor.vue`
- `IntegrationJsonResultPicker.vue`
- `integrationConnectorShared.js`

## 组件职责边界

### 必须保持的职责拆分

- `AdminIntegrationConnectorListPanel.vue` 负责连接器列表、筛选、创建/编辑弹层态切换。
- `AdminIntegrationConnectorDetailPanel.vue` 负责详情页数据加载、状态编排、子组件通信，不再承载鉴权/API 的大段展示模板。
- `AdminIntegrationConnectorAuthSection.vue` 负责鉴权列表、鉴权配置、鉴权调试三栏布局。
- `AdminIntegrationConnectorApiSection.vue` 负责 API 列表态、API 编辑态、API 调试侧栏。
- `IntegrationParamTable.vue` 负责树形参数结构编辑。
- `IntegrationVariableInput.vue` 负责“输入值 + 变量插入面板”。
- `IntegrationVariableNamedValueEditor.vue` 负责 header/form 这类“名称 + 值”成组编辑。
- `IntegrationJsonResultPicker.vue` 负责从调试结果中挑选出参结构。
- `integrationConnectorShared.js` 负责共享 option、状态文案、输入框 class、发布/启用状态 badge。

### 禁止回退

- 不要把 `AuthSection` 和 `ApiSection` 再塞回 `DetailPanel` 里。
- 不要把共享 option、badge class、输入框 class 再复制回各个页面内部。
- 不要在展示子组件里重新引入请求、副作用、路由跳转。

## 整体布局基线

### 页面级骨架

- 页面外层统一保持后台高密度容器：`flex h-full min-h-0 flex-col gap-5 overflow-y-auto p-6`
- 主内容以白底卡片为主，不引入新的彩色大背景、Hero 区、装饰性横幅。
- 该功能是后台配置页，不追求营销页式留白，重点是可扫描性和可操作性。

### 圆角与卡片层级

- 一级主卡片使用 `rounded-3xl border border-slate-200 bg-white shadow-sm`
- 二级内容区、表格容器、提示区使用 `rounded-2xl`
- 按钮、输入框、页签使用 `rounded-xl` 或 `rounded-lg`
- 不要无理由把整页统一改成更硬的直角，也不要全部放大成更夸张的圆角体系

### 颜色体系

- 主色只沿用现有 `primary` 体系
- 中性色只沿用 `slate-*`
- 状态色只沿用当前语义：
    - 成功/启用：`emerald`
    - 警示/草稿：`amber`
    - 错误/删除：`rose`
    - 发布：`blue`
- 禁止引入新的主视觉色系去重绘整个连接器页面，尤其不要改成紫色系、深色系或渐变大背景

## 列表页规范

### 页面结构

- 顶部是“标题说明 + 新建按钮 + 筛选区”的单卡片结构，不拆成多排独立面板。
- 下方是卡片网格列表，不回退成普通表格。
- 列表卡片保持信息密度，核心信息顺序固定：
    - 状态 badge
    - 权限 badge
    - 名称
    - 编码
    - 基础地址
    - 鉴权数量/状态说明
    - 操作按钮

### 卡片样式

- 单卡片保留悬浮轻抬升：`hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-lg`
- 卡片是信息卡，不要加入插画、头像、无意义图标块
- 编码字段保持 `font-mono text-xs text-slate-400`
- 基础地址和说明保留 `text-sm text-slate-500` 的弱化层级

### 列表交互

- “配置”是主入口，继续进入详情页
- “编辑”只打开当前页面的编辑态，不跳转
- “删除”继续使用危险按钮风格，不与普通次按钮混色

## 列表页编辑态规范

- 列表页创建/编辑连接器仍是同页切换，不改成弹窗、不拆成独立路由
- 顶部保留返回箭头 + 标题 + 编码提示的窄头部
- 主表单保留单卡片双列表单布局，避免再次嵌套多层分组
- 表单项顺序固定：
    - 连接器名称
    - 连接器编码
    - 接口基础地址
    - 状态
    - 权限范围

## 详情页规范

### 头部结构

- 详情页头部保持：
    - 返回按钮
    - 标题
    - 状态 badge
    - 权限 badge
    - 副标题（基础地址）
    - 下方页签条
- 页签是同页切换，不改成二级路由，不改成左侧导航

### 页签范围

- 只保留三个一级页签：
    - `基础信息`
    - `鉴权`
    - `API 管理`
- 不要新增第四层包装页签，不要把少量配置拆成更多二级标签页

### 基础信息页

- 维持单卡片、双列表单、底部右对齐保存按钮
- 保持“权限范围说明文案”紧跟控件下方
- 编码字段继续只读显示，不回退成可编辑

## 鉴权区规范

### 布局

- 保持三栏布局：`300px` 左侧列表 + 中间主编辑区 + `340px` 右侧调试区
- 三栏容器最小高度保持 `680px`
- 中间区和右侧区保留 `max-h-[calc(100vh-300px)]` 的滚动上限，避免内容过长撑爆页面

### 左侧鉴权列表

- 仅承载“列表 + 新建”，不要混入编辑表单
- 选中态通过 `border-primary bg-primary/5 text-primary` 表达
- 列表项保留“名称 + 接口地址”两行结构

### 中间鉴权配置区

- 顶部只保留标题说明和删除按钮
- 基础字段保持双列排布
- 请求配置继续使用三个页签：
    - `header`
    - `form`
    - `body`
- `header/form` 使用 `IntegrationVariableNamedValueEditor`
- `body` 使用 `IntegrationVariableInput` 的多行模式
- 出参结构继续使用 `IntegrationParamTable`

### 右侧调试区

- 调试区必须独立成侧栏，不与主配置区混排
- 调试按钮保持深色高对比按钮
- 调试变量输入继续使用 `font-mono`
- 调试返回继续使用深色 `pre` 容器，不改成普通浅色文本块

## API 区规范

### 列表态

- API 默认先展示列表态，不默认直接打开编辑器
- 列表使用类表格网格，不改成卡片瀑布流
- 维持 `min-w-[980px]` 的横向滚动策略，保证列结构稳定
- 固定列语义：
    - 名称
    - 方法
    - 状态
    - 接口地址
    - 鉴权
    - 操作

### 编辑态

- 保持主编辑区 + 右侧调试区的两栏布局
- 主编辑区保留以下顺序：
    - API 名称
    - API 编码
    - 请求方式
    - 接口地址
    - 选择鉴权
    - 内容类型
    - API 说明
    - 请求配置页签
    - 入参结构
    - 出参结构
- 调试区继续独立，不嵌回主表单底部

### 状态表达

- 发布状态使用 `getIntegrationPublishStatusMeta`
- 不要在 API 区再引入另一套发布 badge 文案或颜色
- “发布 / 停用 / 删除”三个动作必须保持明确区分

## 表单控件规范

### 统一控件

- 后台业务表单中的单值下拉优先使用 `AppSelect`
- 统一使用 `integrationConnectorShared.js` 中的按钮样式 class
- 文本输入继续复用 `buildIntegrationInputClass`
- 错误态继续使用 `rose` 边框和 `focus:ring`
- 只读态继续使用 `bg-slate-100 text-slate-500`

### 变量输入

- 任何支持变量占位的 URL、body、header/form 值，都必须继续使用 `IntegrationVariableInput`
- 变量面板继续维持右上角触发、小浮层展开，不改成独立弹窗
- 变量 key 保持 `font-mono` 展示

### 参数结构编辑

- 树形入参/出参统一走 `IntegrationParamTable`
- 缩进关系通过左 padding 和短横线辅助表达，不改成树组件或折叠面板
- 表格型高密度编辑仍可保留行内原生 `select` 作为特例，前提是不能破坏行高、对齐和批量编辑效率

## 共享常量与样式复用

- 以下内容只允许在 `integrationConnectorShared.js` 维护：
    - 状态选项
    - 权限选项
    - 请求方式选项
    - 内容类型选项
    - 请求页签
    - 发布/启用状态 badge
    - 通用输入框 class
    - `AppSelect` 按钮样式 class
- 同类 option、badge、输入框 class 第 2 次出现就必须抽离，不能继续散写

## 交互约束

- `DetailPanel` 只做 `props down / events up` 编排
- 删除动作必须继续走确认弹窗
- 自动生成编码的逻辑保留在父组件，不下沉到纯展示组件
- 调试结果选择出参时，继续通过 `IntegrationJsonResultPicker` 完成，不要直接改成手输 JSON 路径
- 密钥/令牌相关返回信息不做额外高亮装饰，只保留当前后台工具式表达

## 当前例外与待整改项

### 已经符合规范的部分

- 详情页已经完成“父组件编排 + 子组件拆分”
- 共享 option 和输入框 class 已经开始收敛到 `integrationConnectorShared.js`
- 鉴权区和 API 区已统一为高密度后台配置页风格

### 当前仍存在的例外

- `AdminIntegrationConnectorListPanel.vue` 里仍有原生 `select`
    - 筛选状态
    - 编辑态状态
    - 编辑态权限范围
- 这些位置不应再作为后续新增样式的参考模板；后续触达时应优先向 `AppSelect + shared options` 收敛

### 允许保留的特例

- `IntegrationParamTable.vue` 的行内类型选择器当前是高密度表格编辑特例，可以暂不强制换成 `AppSelect`
- 前提是后续修改不能拉高整行高度、不能破坏横向对齐、不能削弱批量录入效率

## 禁止修改清单

- 禁止整体重绘连接器页面的视觉语言
- 禁止把列表卡片改成低信息密度大卡片
- 禁止引入新的大标签页、分步器、左侧菜单树来替换现有结构
- 禁止为“更美观”增加无意义留白、说明块、装饰块
- 禁止在多个文件中复制粘贴同一套 option 和 class
- 禁止把调试侧栏改成弹窗流或折叠抽屉
- 禁止把变量输入能力删回普通 input/textarea

## 编码与文案要求

- 所有文件统一使用 UTF-8 文本，中文必须是正常可读中文
- 禁止通过可能污染编码的终端写回方式直接覆盖 `.vue`/`.js`/`.md` 文件
- 不要把中文文案替换成问号、乱码、同音英文占位或拼音
- 修改连接器相关文案后，必须再次检查关键中文是否可正常读取

## 后续修改检查清单

修改连接器功能前，至少先核对下面几项：

1. 先读本文件，再读 `integrationConnectorShared.js`
2. 如果改的是详情页，先确认是否应该改父组件编排还是改子组件展示
3. 如果新增下拉，先判断是否必须用 `AppSelect`
4. 如果新增同类状态、option、badge、input class，先抽到 shared 文件
5. 如果改布局，先确认是否会破坏列表高密度、鉴权三栏、API 双栏、调试侧栏
6. 如果改文案，确认文件仍是 UTF-8 且中文无乱码

## 结论

连接器功能的正确方向不是“重新设计”，而是沿着当前后台配置页风格继续做收敛：

- 页面负责编排，复杂区块拆子组件
- 表单控件统一，重复样式统一
- 维持高密度、强可扫描、弱装饰的后台工具界面
- 局部优化可以做，整体视觉体系不要推倒重来

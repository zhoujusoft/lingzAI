package lingzhou.agent.backend.common.lzException;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ExceptionCode {
    Default(500000),

    /**
     * 类型转换不匹配,请检查数据与模型字段类型是否匹配
     */
    ConvertorTypeNotMaching(500001),

    /**
     * 重复的主键ID,请检查表或视图数据
     */
    ReduplicatedObjectID(500002),

    /**
     * 应用不存在或已被删除
     */
    ApplicationNotExsit(500101),
    /**
     * 功能菜单不存在或已被删除
     */
    AppFunctionNotExsit(500102),

    /**
     * 数据源没有设置或已被删除
     */
    DataSourceNotExist(500103),

    /**
     * 表单不存在或已被删除
     */
    FormSheetNotExsit(500201),

    /**
     * 数据源字段缺失，请对比模型与数据源字段
     */
    SchemaFieldNotExist(500202),

    /**
     * 数据源字段序列化方法为None,请检查模型中SerializeMethod的值
     */
    SchemaFieldSerializeNone(500203),

    /**
     * 模型不存在，请确认SchemaCode对应的模型是否存在
     */
    SchemaNotExist(500204),

    /**
     * 列表设置不存在，请确认SchemaCode对应的模型是否存在
     */
    ListSettingNotExist(500205),

    /**
     * 表达式转换失败,请检查表达式内容
     */
    FormulaResolveError(500401),

    /**
     * 业务集成业务方法校验异常
     */
    BizServiceExecuteValidate(500501),

    /**
     * 业务集成业务方法非预期
     */
    BizServiceExecuteUnexpected(500502),

    /**
     * 业务集成业务方法执行异常
     */
    BizServiceExecuteError(500503),

    /**
     * sql语句执行错误
     */
    SqlExecuteError(500910),

    /**
     * 功能限制超出上限
     */
    FunctionLimitError(40001);

    public static final int SIZE = Integer.SIZE;

    private int intValue;
    private static java.util.HashMap<Integer, ExceptionCode> mappings;

    private static java.util.HashMap<Integer, ExceptionCode> getMappings() {
        if (mappings == null) {
            synchronized (ExceptionCode.class) {
                if (mappings == null) {
                    mappings = new java.util.HashMap<Integer, ExceptionCode>();
                }
            }
        }
        return mappings;
    }

    private ExceptionCode(int value) {
        intValue = value;
        getMappings().put(value, this);
    }

    @JsonValue
    public int getValue() {
        return intValue;
    }

    @JsonCreator
    public static ExceptionCode forValue(int value) {
        return getMappings().get(value);
    }
}

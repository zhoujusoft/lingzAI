package lingzhou.agent.backend.capability.dataset.runtime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import lingzhou.agent.backend.business.skill.service.LowcodePlatformConfigService;
import lingzhou.agent.backend.business.skill.service.LowcodeTokenService;
import lingzhou.agent.backend.capability.api.client.LowcodePlatformClient;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.junit.jupiter.api.Test;

class LowcodeDatasetSqlValidatorTest {

    private final LowcodeDatasetSqlValidator validator = new LowcodeDatasetSqlValidator();

    @Test
    void acceptsSimpleSelect() {
        assertThatCode(() -> validator.validate("SELECT name FROM demo WHERE state = '已通过' LIMIT 100"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsFieldNamesContainingDelete() {
        assertThatCode(() -> validator.validate("SELECT name FROM demo WHERE IsDelete = 0 AND deleted_at IS NULL"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWithQueryUnsupportedByLowcodePlatform() {
        assertThatThrownBy(() -> validator.validate("WITH filtered AS (SELECT name FROM demo) SELECT * FROM filtered"))
                .isInstanceOf(TaskException.class)
                .hasMessageContaining("仅支持以 SELECT 开头")
                .hasMessageContaining("不支持 WITH");
    }

    @Test
    void rejectsIndependentWriteKeyword() {
        assertThatThrownBy(() -> validator.validate("SELECT name FROM demo WHERE id IN (delete)"))
                .isInstanceOf(TaskException.class)
                .hasMessageContaining("独立的 update、insert 或 delete");
    }

    @Test
    void executorRejectsInvalidSqlBeforeAnyPlatformInteraction() {
        LowcodePlatformConfigService platformConfigService = mock(LowcodePlatformConfigService.class);
        LowcodeTokenService tokenService = mock(LowcodeTokenService.class);
        LowcodePlatformClient platformClient = mock(LowcodePlatformClient.class);
        LowcodeSqlCryptoService cryptoService = mock(LowcodeSqlCryptoService.class);
        LowcodeDatasetSqlExecutor executor =
                new LowcodeDatasetSqlExecutor(platformConfigService, tokenService, platformClient, cryptoService, validator);

        assertThatThrownBy(() -> executor.execute(null, "SELECT name FROM demo WHERE id IN (delete)"))
                .isInstanceOf(TaskException.class)
                .hasMessageContaining("低代码 sqlSelect 兼容性校验失败");

        verifyNoInteractions(platformConfigService, tokenService, platformClient, cryptoService);
    }
}

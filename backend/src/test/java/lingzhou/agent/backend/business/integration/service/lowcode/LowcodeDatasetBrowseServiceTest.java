package lingzhou.agent.backend.business.integration.service.lowcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.skill.service.LowcodePlatformConfigService;
import lingzhou.agent.backend.business.skill.service.LowcodeTokenService;
import lingzhou.agent.backend.business.system.model.PlatformEndpointItem;
import lingzhou.agent.backend.capability.api.client.LowcodePlatformClient;
import org.junit.jupiter.api.Test;

class LowcodeDatasetBrowseServiceTest {

    @Test
    void ignoresSubtableChildFields() throws Exception {
        LowcodePlatformConfigService platformConfigService = mock(LowcodePlatformConfigService.class);
        LowcodeTokenService tokenService = mock(LowcodeTokenService.class);
        LowcodePlatformClient platformClient = mock(LowcodePlatformClient.class);
        LowcodeDatasetBrowseService service =
                new LowcodeDatasetBrowseService(platformConfigService, tokenService, platformClient);

        PlatformEndpointItem platform = new PlatformEndpointItem();
        platform.setKey("lowcode");
        Map<String, Object> duplicatedField =
                Map.of("RelateName", "Amount", "DisplayName", "金额", "LogicTypeName", "Decimal");
        Map<String, Object> subtable = Map.of(
                "BizTableName",
                "order_detail",
                "Item",
                Map.of(
                        "RelFormName",
                        "订单明细",
                        "UserFields",
                        Map.of("Result", List.of(duplicatedField)),
                        "children",
                        List.of(
                                duplicatedField,
                                Map.of(
                                        "ItemName",
                                        "ChildOnlyField",
                                        "DisplayName",
                                        "仅 children 返回的字段",
                                        "LogicTypeName",
                                        "String"))));

        when(platformConfigService.requirePlatform("lowcode")).thenReturn(platform);
        when(tokenService.getTokenIfConfigured(platform)).thenReturn("");
        when(platformClient.getDataSourceNew(platform, "", "order_form"))
                .thenReturn(Map.of("SubFields", List.of(subtable)));

        List<LowcodeDatasetBrowseService.FieldView> fields =
                service.listFields("lowcode", "order_app", "order", "order_form");

        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).fieldName()).isEqualTo("Amount");
        assertThat(fields.get(0).subObjectCode()).isEqualTo("order_detail");
        assertThat(fields.get(0).fieldScope()).isEqualTo("SUB_USER");
        assertThat(fields).noneMatch(field -> "SUB_CHILD".equals(field.fieldScope()));
        assertThat(fields).noneMatch(field -> "ChildOnlyField".equals(field.fieldName()));
    }
}

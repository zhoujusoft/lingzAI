package lingzhou.agent.backend.capability.agentruntime.v2.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RuntimeV2EngineTypeTest {

    @Test
    void shouldTreatLegacyGraphPreviewAsGraphAlias() {
        assertThat(RuntimeV2EngineType.fromConfigValue("graph-preview")).isEqualTo(RuntimeV2EngineType.GRAPH);
        assertThat(RuntimeV2EngineType.fromConfigValue("graph")).isEqualTo(RuntimeV2EngineType.GRAPH);
    }

    @Test
    void shouldTreatClassicAndUnknownValuesAsGraph() {
        assertThat(RuntimeV2EngineType.fromConfigValue("classic")).isEqualTo(RuntimeV2EngineType.GRAPH);
        assertThat(RuntimeV2EngineType.fromConfigValue("unknown")).isEqualTo(RuntimeV2EngineType.GRAPH);
        assertThat(RuntimeV2EngineType.fromConfigValue(null)).isEqualTo(RuntimeV2EngineType.GRAPH);
    }
}

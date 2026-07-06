package lingzhou.agent.backend.capability.agentruntime.v2.code;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeV2CodePlanProtocolTest {

    private final RuntimeV2CodePlanProtocol protocol = new RuntimeV2CodePlanProtocol(
            new lingzhou.agent.backend.capability.agentruntime.v2.react.RuntimeV2ReactDecisionProtocol());

    @Test
    void shouldFillDefaultsForValidCodePlan() {
        String raw =
                """
                {
                  "scriptPath": "/workspace/task.py",
                  "outputPath": "/outputs/result.html"
                }
                """;

        var validation = protocol.validate(raw, List.of("/uploads/a.xlsx"), "生成报告");

        assertThat(validation.valid()).isTrue();
        assertThat(validation.plan()).isNotNull();
        assertThat(validation.plan().inputPaths()).containsExactly("/uploads/a.xlsx");
        assertThat(validation.plan().outputFileName()).isEqualTo("result.html");
        assertThat(validation.plan().outputMimeType()).isEqualTo("text/html");
        assertThat(validation.plan().timeoutSeconds()).isEqualTo(600);
        assertThat(validation.plan().goal()).isEqualTo("生成报告");
    }

    @Test
    void shouldPreserveModelChosenOutputWhenUserDidNotExplicitlyRequestTableFormat() {
        String raw =
                """
                {
                  "scriptPath": "/workspace/task.py",
                  "inputPaths": ["/uploads/list.xlsx"],
                  "outputPath": "/outputs/hubei_companies.html",
                  "outputFileName": "hubei_companies.html",
                  "outputMimeType": "text/html"
                }
                """;

        var validation = protocol.validate(raw, List.of(), "筛选出所有湖北的企业并输出");

        assertThat(validation.valid()).isTrue();
        assertThat(validation.plan().outputPath()).isEqualTo("/outputs/hubei_companies.html");
        assertThat(validation.plan().outputFileName()).isEqualTo("hubei_companies.html");
        assertThat(validation.plan().outputMimeType()).isEqualTo("text/html");
    }

    @Test
    void shouldPreferZipOutputForArchiveExtractionTask() {
        String raw =
                """
                {
                  "scriptPath": "/workspace/task.py",
                  "inputPaths": ["/uploads/invoices.zip"],
                  "outputPath": "/outputs/extracted_invoices.html",
                  "outputFileName": "extracted_invoices.html",
                  "outputMimeType": "text/html"
                }
                """;

        var validation = protocol.validate(raw, List.of(), "帮我提取其中 PDF 发票文件 然后给我压缩一下");

        assertThat(validation.valid()).isTrue();
        assertThat(validation.plan().outputPath()).isEqualTo("/outputs/extracted_invoices.zip");
        assertThat(validation.plan().outputFileName()).isEqualTo("extracted_invoices.zip");
        assertThat(validation.plan().outputMimeType()).isEqualTo("application/zip");
    }

    @Test
    void shouldNotForceZipOutputOnlyBecauseInputIsArchive() {
        String raw =
                """
                {
                  "scriptPath": "/workspace/task.py",
                  "inputPaths": ["/uploads/invoices.zip"],
                  "outputPath": "/outputs/result.html",
                  "outputFileName": "result.html",
                  "outputMimeType": "text/html"
                }
                """;

        var validation = protocol.validate(raw, List.of(), "帮我看看这个压缩包里都有什么内容");

        assertThat(validation.valid()).isTrue();
        assertThat(validation.plan().outputPath()).isEqualTo("/outputs/result.html");
        assertThat(validation.plan().outputFileName()).isEqualTo("result.html");
        assertThat(validation.plan().outputMimeType()).isEqualTo("text/html");
    }

    @Test
    void shouldRejectOutputPathOutsideOutputsDirectory() {
        String raw =
                """
                {
                  "scriptPath": "/workspace/task.py",
                  "inputPaths": ["/uploads/a.xlsx"],
                  "outputPath": "/workspace/result.html"
                }
                """;

        var validation = protocol.validate(raw, List.of(), "生成报告");

        assertThat(validation.valid()).isFalse();
        assertThat(validation.errorMessage()).contains("outputPath 必须位于 /outputs");
    }
}

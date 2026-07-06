/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package lingzhou.agent.backend.app;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.chat.attachment.FileParseToolProvider;
import lingzhou.agent.backend.business.chat.execution.tool.RuntimeSystemToolProvider;
import lingzhou.agent.backend.business.chat.execution.workspace.RuntimeExecutionProperties;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import lingzhou.agent.backend.business.datasets.service.MinioService;
import lingzhou.agent.backend.capability.skillruntime.registry.SkillRuntimeRegistry;
import lingzhou.agent.backend.capability.tool.registry.GlobalToolRegistry;
import lingzhou.agent.backend.capability.tool.runtime.FrontendRenderToolService;
import lingzhou.agent.backend.capability.webfetch.WebFetchToolProvider;
import lingzhou.agent.spring.ai.skill.core.DefaultSkillKit;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import lingzhou.agent.spring.ai.skill.core.SkillPoolManager;
import lingzhou.agent.spring.ai.skill.spi.SkillAwareToolCallbackResolver;
import lingzhou.agent.spring.ai.skill.spi.SkillAwareToolCallingManager;
import lingzhou.agent.spring.ai.skill.support.DefaultSkillPoolManager;
import lingzhou.agent.spring.ai.skill.support.SimpleSkillBox;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;

@Configuration
@EnableConfigurationProperties({
    ChatModelProperties.class,
    ChatContextProperties.class,
    SkillProperties.class,
    ModelProviderProperties.class,
    RuntimeExecutionProperties.class
})
public class ClothingSkillConfig {

    @Bean
    public SkillPoolManager skillPoolManager() {
        return new DefaultSkillPoolManager();
    }

    @Bean
    public SimpleSkillBox skillBox() {
        return new SimpleSkillBox();
    }

    @Bean
    public GlobalToolRegistry globalToolRegistry(
            ChatFileService chatFileService,
            FrontendRenderToolService frontendRenderToolService,
            MinioService minioService,
            RuntimeSystemToolProvider runtimeSystemToolProvider,
            FileParseToolProvider fileParseToolProvider,
            WebFetchToolProvider webFetchToolProvider) {
        ClothingSkillTools.setChatUploadReader(chatFileService::readFileAsString);
        ClothingSkillTools.setChatUploadMaterializer(chatFileService::materializeToLocalPath);
        ClothingSkillTools.setArtifactWriter((folder, fileName, content, sourcePath, contentType) -> {
            if (sourcePath != null && !sourcePath.isBlank()) {
                return minioService.uploadArtifact(
                        Path.of(sourcePath).toAbsolutePath().normalize(), folder, fileName, contentType);
            }
            return minioService.uploadArtifact(
                    folder,
                    fileName,
                    content == null ? new byte[0] : content.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    contentType);
        });
        return new GlobalToolRegistry(
                buildBaseTools(
                        frontendRenderToolService, runtimeSystemToolProvider, fileParseToolProvider, webFetchToolProvider));
    }

    @Bean
    public SkillKit skillKit(
            SimpleSkillBox skillBox,
            SkillPoolManager poolManager,
            SkillRuntimeRegistry skillRuntimeRegistry,
            SkillProperties skillProperties) {
        SkillFilesystemSupport.configureRoot(skillProperties.getRootDir());
        SkillKit skillKit = DefaultSkillKit.builder()
                .skillBox(skillBox)
                .poolManager(poolManager)
                .build();
        skillRuntimeRegistry.registerAll(skillKit);
        return skillKit;
    }

    @Bean
    public SkillAwareToolCallingManager toolCallingManager(SkillKit skillKit, GlobalToolRegistry globalToolRegistry) {
        ToolCallingManager delegate = DefaultToolCallingManager.builder()
                .toolCallbackResolver(new DelegatingToolCallbackResolver(List.of(
                        new StaticToolCallbackResolver(globalToolRegistry.getToolCallbacks()),
                        SkillAwareToolCallbackResolver.builder()
                                .skillKit(skillKit)
                                .build())))
                .build();
        return SkillAwareToolCallingManager.builder()
                .skillKit(skillKit)
                .delegate(delegate)
                .build();
    }

    private static List<GlobalToolRegistry.ToolRegistration> buildBaseTools(
            FrontendRenderToolService frontendRenderToolService,
            RuntimeSystemToolProvider runtimeSystemToolProvider,
            FileParseToolProvider fileParseToolProvider,
            WebFetchToolProvider webFetchToolProvider) {
        List<GlobalToolRegistry.ToolRegistration> registrations = new ArrayList<>();
        for (ToolCallback callback : ToolCallbacks.from(runtimeSystemToolProvider)) {
            String toolName = callback.getToolDefinition() == null
                    ? null
                    : callback.getToolDefinition().name();
            if ("runtime_tool".equals(toolName)) {
                continue;
            }
            registrations.add(new GlobalToolRegistry.ToolRegistration(callback, false, true));
        }
        for (ToolCallback callback : ToolCallbacks.from(fileParseToolProvider)) {
            registrations.add(new GlobalToolRegistry.ToolRegistration(callback, false, true));
        }
        for (ToolCallback callback : ToolCallbacks.from(webFetchToolProvider)) {
            registrations.add(new GlobalToolRegistry.ToolRegistration(callback, false, true));
        }
        for (ToolCallback callback : ToolCallbacks.from(new DetoxHealthRiskToolProvider())) {
            registrations.add(new GlobalToolRegistry.ToolRegistration(callback, true, false));
        }
        registrations.add(new GlobalToolRegistry.ToolRegistration(
                FunctionToolCallback.builder(
                                "get_render_template",
                                (Map<String, Object> arguments,
                                        org.springframework.ai.chat.model.ToolContext toolContext) ->
                                        frontendRenderToolService.getRenderTemplate(arguments))
                        .description("根据模板编码返回前端渲染模板定义，可选结合目标 API 工具生成有效 dataSchema。")
                        .inputType(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .inputSchema(frontendRenderToolService.getTemplateInputSchema())
                        .build(),
                false,
                true));
        registrations.add(new GlobalToolRegistry.ToolRegistration(
                FunctionToolCallback.builder(
                                "build_frontend_render_payload",
                                (Map<String, Object> arguments,
                                        org.springframework.ai.chat.model.ToolContext toolContext) ->
                                        frontendRenderToolService.buildRenderPayload(arguments))
                        .description("根据模板、业务数据和组件配置封装前端渲染结果。")
                        .inputType(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .inputSchema(frontendRenderToolService.buildPayloadInputSchema())
                        .build(),
                false,
                true));
        registrations.add(new GlobalToolRegistry.ToolRegistration(
                FunctionToolCallback.builder(
                                "generate_frontend_render",
                                (Map<String, Object> arguments,
                                        org.springframework.ai.chat.model.ToolContext toolContext) ->
                                        frontendRenderToolService.generate(arguments))
                        .description("兼容旧链路：根据模板和业务数据生成前端渲染结果。")
                        .inputType(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .inputSchema(frontendRenderToolService.buildPayloadInputSchema())
                        .build(),
                false,
                true));
        return List.copyOf(registrations);
    }
}

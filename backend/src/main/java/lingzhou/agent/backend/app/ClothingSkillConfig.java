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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lingzhou.agent.backend.capability.skillruntime.registry.SkillRuntimeRegistry;
import lingzhou.agent.backend.capability.tool.registry.GlobalToolRegistry;
import lingzhou.agent.backend.business.chat.service.ChatFileService;
import lingzhou.agent.spring.ai.skill.core.DefaultSkillKit;
import lingzhou.agent.spring.ai.skill.core.SkillKit;
import lingzhou.agent.spring.ai.skill.core.SkillPoolManager;
import lingzhou.agent.spring.ai.skill.spi.SkillAwareToolCallbackResolver;
import lingzhou.agent.spring.ai.skill.spi.SkillAwareToolCallingManager;
import lingzhou.agent.spring.ai.skill.support.DefaultSkillPoolManager;
import lingzhou.agent.spring.ai.skill.support.SimpleSkillBox;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ChatModelProperties.class, ChatMemoryProperties.class, SkillProperties.class})
public class ClothingSkillConfig {

    @Bean
    public SkillPoolManager skillPoolManager() {
        return new DefaultSkillPoolManager();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.chat.memory", name = "type", havingValue = "redis")
    public ChatMemoryRepository redisChatMemoryRepository(
            ObjectProvider<RedisChatMemoryRepositoryFactory> redisFactoryProvider) {
        RedisChatMemoryRepositoryFactory redisFactory = redisFactoryProvider.getIfAvailable();
        if (redisFactory == null) {
            throw new IllegalStateException(
                    "app.chat.memory.type=redis is enabled, but no RedisChatMemoryRepositoryFactory bean was found.");
        }
        return redisFactory.create();
    }

    @Bean
    @ConditionalOnMissingBean(ChatMemoryRepository.class)
    public ChatMemoryRepository inMemoryChatMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository repository, ChatMemoryProperties properties) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(properties.getMaxMessages())
                .build();
    }

    @Bean
    public SimpleSkillBox skillBox() {
        return new SimpleSkillBox();
    }

    @Bean
    public GlobalToolRegistry globalToolRegistry(ChatFileService chatFileService) {
        ClothingSkillTools.setChatUploadReader(chatFileService::readFileAsString);
        ClothingSkillTools.setChatUploadMaterializer(chatFileService::materializeToLocalPath);
        return new GlobalToolRegistry(buildBaseTools());
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
                        SkillAwareToolCallbackResolver.builder().skillKit(skillKit).build())))
                .build();
        return SkillAwareToolCallingManager.builder()
                .skillKit(skillKit)
                .delegate(delegate)
                .build();
    }

    private static List<ToolCallback> buildBaseTools() {
        Object toolProvider = new Object() {
            @Tool(description = "Read a local file or chat-upload virtual path as UTF-8 text.")
            public String readFile(@ToolParam(description = "Absolute or relative file path") String path) {
                return ClothingSkillTools.readFileAsString(path);
            }

            @Tool(description = "Write UTF-8 text to a local file path, creating parent directories if needed.")
            public String writeFile(
                    @ToolParam(description = "Absolute or relative file path") String path,
                    @ToolParam(description = "UTF-8 text content") String content) {
                return ClothingSkillTools.writeFileAsString(path, content);
            }

            @Tool(description = "Execute a python script with arguments and return stdout as text.")
            public String runPython(
                    @ToolParam(description = "Absolute or relative path to a python script") String scriptPath,
                    @ToolParam(description = "Command arguments joined by spaces") String args) {
                return ClothingSkillTools.runPythonScript(scriptPath, args);
            }
        };

        List<ToolCallback> callbacks = new ArrayList<>();
        callbacks.addAll(List.of(ToolCallbacks.from(toolProvider)));
        callbacks.addAll(List.of(ToolCallbacks.from(new DetoxHealthRiskToolProvider())));
        return List.copyOf(callbacks);
    }

}

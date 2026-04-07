package lingzhou.agent.backend.business.datasets.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lingzhou.agent.backend.business.chat.service.ChatConversationService;
import lingzhou.agent.backend.business.datasets.service.IntegrationDatasetPublishService;
import lingzhou.agent.backend.business.datasets.service.IntegrationDatasetService;
import lingzhou.agent.backend.common.lzException.TaskException;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/integration/datasets")
public class IntegrationDatasetController {

    private final IntegrationDatasetService integrationDatasetService;
    private final IntegrationDatasetPublishService integrationDatasetPublishService;
    private final ChatConversationService chatConversationService;

    public IntegrationDatasetController(
            IntegrationDatasetService integrationDatasetService,
            IntegrationDatasetPublishService integrationDatasetPublishService,
            ChatConversationService chatConversationService) {
        this.integrationDatasetService = integrationDatasetService;
        this.integrationDatasetPublishService = integrationDatasetPublishService;
        this.chatConversationService = chatConversationService;
    }

    @GetMapping
    public List<IntegrationDatasetService.DatasetSummary> listDatasets(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sourceKind", required = false) String sourceKind,
            @RequestParam(value = "aiDataSourceId", required = false) Long aiDataSourceId,
            @RequestParam(value = "lowcodePlatformKey", required = false) String lowcodePlatformKey) {
        return integrationDatasetService.listDatasets(keyword, sourceKind, aiDataSourceId, lowcodePlatformKey);
    }

    @GetMapping("/{id}")
    public IntegrationDatasetService.DatasetDetail getDataset(@PathVariable("id") Long id) throws TaskException {
        return integrationDatasetService.getDataset(id);
    }

    @PostMapping(value = "/{id}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ChatConversationService.DatasetChatRequest request,
            HttpServletRequest httpRequest) {
        Long userId = chatConversationService.resolveUserId(httpRequest);
        return chatConversationService.streamDataset(id, request, userId);
    }

    @PostMapping
    public IntegrationDatasetService.DatasetDetail create(
            @RequestBody IntegrationDatasetService.UpsertDatasetRequest request) throws TaskException {
        return integrationDatasetService.create(request);
    }

    @PutMapping("/{id}")
    public IntegrationDatasetService.DatasetDetail update(
            @PathVariable("id") Long id, @RequestBody IntegrationDatasetService.UpsertDatasetRequest request)
            throws TaskException {
        return integrationDatasetService.update(id, request);
    }

    @PostMapping("/generate-description")
    public IntegrationDatasetService.DescriptionGenerateResult generateDescription(
            @RequestBody IntegrationDatasetService.DescriptionGenerateRequest request) throws TaskException {
        return integrationDatasetService.generateDescription(request);
    }

    @GetMapping("/{id}/publish-status")
    public IntegrationDatasetPublishService.PublishStatusView getPublishStatus(@PathVariable("id") Long id)
            throws TaskException {
        return integrationDatasetPublishService.getPublishStatus(id);
    }

    @PostMapping("/{id}/publish")
    public IntegrationDatasetPublishService.PublishStatusView publish(@PathVariable("id") Long id)
            throws TaskException {
        return integrationDatasetPublishService.publish(id);
    }

    @PostMapping("/{id}/disable")
    public IntegrationDatasetPublishService.PublishStatusView disable(@PathVariable("id") Long id)
            throws TaskException {
        return integrationDatasetPublishService.disable(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) throws TaskException {
        integrationDatasetService.delete(id);
    }
}

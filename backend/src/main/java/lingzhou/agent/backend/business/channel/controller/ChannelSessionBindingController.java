package lingzhou.agent.backend.business.channel.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lingzhou.agent.backend.business.channel.domain.ChannelSessionBinding;
import lingzhou.agent.backend.business.channel.service.ChannelSessionBindingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/channel/sessions")
public class ChannelSessionBindingController {

    private final ChannelSessionBindingService channelSessionBindingService;

    public ChannelSessionBindingController(ChannelSessionBindingService channelSessionBindingService) {
        this.channelSessionBindingService = channelSessionBindingService;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(value = "channelId", required = false) Long channelId,
            @RequestParam(value = "limit", required = false) Integer limit) {
        List<ChannelSessionBinding> items = channelSessionBindingService.listByChannelId(channelId, limit);
        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", items.size());
        return result;
    }

    @GetMapping("/{channelId}/{externalSessionKey}")
    public ChannelSessionBinding detail(
            @PathVariable("channelId") Long channelId, @PathVariable("externalSessionKey") String externalSessionKey) {
        ChannelSessionBinding binding =
                channelSessionBindingService.findByExternalSessionKey(channelId, externalSessionKey);
        if (binding == null) {
            throw new IllegalArgumentException("渠道会话绑定不存在");
        }
        return binding;
    }
}

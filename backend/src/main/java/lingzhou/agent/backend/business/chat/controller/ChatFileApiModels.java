package lingzhou.agent.backend.business.chat.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import lingzhou.agent.backend.business.chat.service.RuntimeFileAssetService;

public final class ChatFileApiModels {

    private ChatFileApiModels() {}

    public static FileAssetListResponse fileAssetList(IPage<RuntimeFileAssetService.RuntimeFileAssetItemView> page) {
        List<RuntimeFileAssetService.RuntimeFileAssetItemView> items =
                page == null || page.getRecords() == null ? List.of() : page.getRecords();
        long current = page == null ? 1 : page.getCurrent();
        long size = page == null ? items.size() : page.getSize();
        long total = page == null ? items.size() : page.getTotal();
        long pages = page == null ? 1 : page.getPages();
        return new FileAssetListResponse(items, current, size, total, pages);
    }

    public record FileAssetListResponse(
            List<RuntimeFileAssetService.RuntimeFileAssetItemView> items,
            long current,
            long size,
            long total,
            long pages) {}
}

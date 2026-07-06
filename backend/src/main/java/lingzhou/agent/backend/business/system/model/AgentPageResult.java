package lingzhou.agent.backend.business.system.model;

import java.util.List;

/**
 * Agent 分页查询结果
 */
public class AgentPageResult {

    private List<AgentListItemDto> items;
    private Long total;
    private Integer page;
    private Integer pageSize;

    public List<AgentListItemDto> getItems() {
        return items;
    }

    public void setItems(List<AgentListItemDto> items) {
        this.items = items;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}

package lingzhou.agent.backend.business.system.model;

import java.util.List;

public class BatchBindRoleUsersInput {

    private List<Long> userIds;

    public List<Long> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<Long> userIds) {
        this.userIds = userIds;
    }
}

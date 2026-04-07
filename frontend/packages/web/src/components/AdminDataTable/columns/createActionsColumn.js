import { h } from 'vue';
import ActionsCell from '../cells/ActionsCell.vue';

export function createActionsColumn(columnHelper, options = {}) {
    const {
        id = 'actions',
        header = '操作',
        width = 150,
        minWidth = 150,
        maxWidth = 150,
        align = 'right',
        stickyRight = true,
        actions = [],
        containerClass = '',
    } = options;

    return columnHelper.display({
        id,
        header,
        cell: info =>
            h(ActionsCell, {
                row: info.row.original,
                actions,
                containerClass,
            }),
        meta: {
            width,
            minWidth,
            maxWidth,
            align,
            stickyRight,
        },
    });
}

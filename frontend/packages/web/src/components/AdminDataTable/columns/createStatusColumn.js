import { h } from 'vue';
import StatusCell from '../cells/StatusCell.vue';

export function createStatusColumn(columnHelper, options = {}) {
    const {
        accessorKey = 'state',
        header = '状态',
        width = 150,
        minWidth = 150,
        maxWidth = 150,
    } = options;

    return columnHelper.accessor(accessorKey, {
        header,
        cell: info =>
            h(StatusCell, {
                value: info.getValue(),
            }),
        meta: {
            width,
            minWidth,
            maxWidth,
        },
    });
}

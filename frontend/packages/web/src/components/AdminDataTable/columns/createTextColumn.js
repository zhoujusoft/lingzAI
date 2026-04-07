import { h } from 'vue';

export function createTextColumn(columnHelper, options) {
    const {
        accessorKey,
        header,
        maxWidth,
        minWidth,
        textTone = 'muted',
    } = options;

    return columnHelper.accessor(accessorKey, {
        header,
        cell: info =>
            h(
                'div',
                {
                    class: 'truncate',
                    style: maxWidth ? { maxWidth: `${maxWidth}px` } : undefined,
                },
                info.getValue(),
            ),
        meta: {
            minWidth,
            textTone,
        },
    });
}

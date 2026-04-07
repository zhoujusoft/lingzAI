import { h } from 'vue';
import UserTypeCell from '../cells/UserTypeCell.vue';
import { USER_TYPES } from '@/model/enums/user-type';

export function createUserTypeColumn(columnHelper, options = {}) {
    const {
        accessorKey = 'userType',
        header = '用户类型',
        minWidth = 160,
        adminValue = USER_TYPES.ADMIN,
        adminClass,
        defaultClass,
    } = options;

    return columnHelper.accessor(accessorKey, {
        header,
        cell: info =>
            h(UserTypeCell, {
                value: info.getValue(),
                adminValue,
                adminClass,
                defaultClass,
            }),
        meta: {
            minWidth,
        },
    });
}

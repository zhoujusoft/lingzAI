<script setup>
import { getCoreRowModel, useVueTable, FlexRender } from '@tanstack/vue-table';

const props = defineProps({
    columns: {
        type: Array,
        required: true,
    },
    data: {
        type: Array,
        required: true,
    },
    minTableWidth: {
        type: String,
        default: '1240px',
    },
});

const table = useVueTable({
    get data() {
        return props.data;
    },
    get columns() {
        return props.columns;
    },
    getCoreRowModel: getCoreRowModel(),
});

const BASE_TH_CLASS = 'whitespace-nowrap px-6 py-4 text-sm font-semibold text-slate-600';
const BASE_TD_CLASS = 'whitespace-nowrap px-6 py-4 align-middle text-sm';
const ALIGN_CLASS_MAP = {
    left: 'text-left',
    center: 'text-center',
    right: 'text-right',
};
const TEXT_TONE_CLASS_MAP = {
    dark: 'text-slate-700',
    muted: 'text-slate-500',
};

function normalizeSize(value) {
    if (value == null) return undefined;
    return typeof value === 'number' ? `${value}px` : value;
}

function getColumnMeta(column) {
    return column.columnDef.meta ?? {};
}

function getSemanticClasses(meta, type) {
    const classes = [];
    const align = type === 'th' ? (meta.thAlign ?? meta.align) : (meta.tdAlign ?? meta.align);
    const alignClass = align ? ALIGN_CLASS_MAP[align] : undefined;
    if (alignClass) classes.push(alignClass);

    if (type === 'td') {
        const toneClass = meta.textTone ? TEXT_TONE_CLASS_MAP[meta.textTone] : undefined;
        if (toneClass) classes.push(toneClass);
    }

    if (meta.stickyRight) {
        if (type === 'th') {
            classes.push('sticky-action-col sticky right-0 z-30 bg-slate-50');
        } else {
            classes.push('sticky-action-col sticky right-0 z-20 bg-white');
        }
    }

    return classes;
}

function getColumnClass(column, type) {
    const meta = getColumnMeta(column);
    const customClass = type === 'th' ? meta.thClass : meta.tdClass;
    return [type === 'th' ? BASE_TH_CLASS : BASE_TD_CLASS, getSemanticClasses(meta, type), customClass];
}

function getColumnStyle(column) {
    const meta = getColumnMeta(column);
    return {
        width: normalizeSize(meta.width),
        minWidth: normalizeSize(meta.minWidth),
        maxWidth: normalizeSize(meta.maxWidth),
    };
}
</script>

<template>
    <div
        class="admin-data-table table-scroll h-fit max-h-full min-h-[120px] w-full self-start overflow-x-auto overflow-y-auto rounded-2xl border border-slate-200 bg-white shadow-sm"
        data-component="AdminDataTable"
    >
        <table
            class="w-full whitespace-nowrap border-collapse text-left"
            :style="{ minWidth: minTableWidth }"
        >
            <thead>
                <tr
                    v-for="headerGroup in table.getHeaderGroups()"
                    :key="headerGroup.id"
                    class="bg-slate-50/50"
                >
                    <th
                        v-for="header in headerGroup.headers"
                        :key="header.id"
                        :class="[getColumnClass(header.column, 'th'), 'border-b border-slate-100 align-middle']"
                        :style="getColumnStyle(header.column)"
                    >
                        <FlexRender
                            v-if="!header.isPlaceholder"
                            :render="header.column.columnDef.header"
                            :props="header.getContext()"
                        />
                    </th>
                </tr>
            </thead>
            <tbody>
                <tr
                    v-for="row in table.getRowModel().rows"
                    :key="row.id"
                    class="group transition-colors hover:bg-slate-50/50"
                >
                    <td
                        v-for="cell in row.getVisibleCells()"
                        :key="cell.id"
                        :class="[getColumnClass(cell.column, 'td'), 'border-b border-slate-50']"
                        :style="getColumnStyle(cell.column)"
                    >
                        <FlexRender
                            :render="cell.column.columnDef.cell"
                            :props="cell.getContext()"
                        />
                    </td>
                </tr>
            </tbody>
        </table>
    </div>
</template>

<style scoped>
.table-scroll {
    position: relative;
    scrollbar-width: thin;
    scrollbar-color: #cbd5e1 transparent;
}

.table-scroll::-webkit-scrollbar {
    width: 6px;
    height: 6px;
}

.table-scroll::-webkit-scrollbar-track {
    background: transparent;
}

.table-scroll::-webkit-scrollbar-thumb {
    background: #cbd5e1;
    border-radius: 999px;
}

:deep(.sticky-action-col) {
    position: sticky;
}

:deep(.sticky-action-col)::before {
    content: '';
    position: absolute;
    left: -12px;
    top: 0;
    bottom: 0;
    width: 12px;
    pointer-events: none;
    background: linear-gradient(
        to left,
        rgba(15, 23, 42, 0.14),
        rgba(15, 23, 42, 0.05) 45%,
        rgba(15, 23, 42, 0)
    );
}
</style>

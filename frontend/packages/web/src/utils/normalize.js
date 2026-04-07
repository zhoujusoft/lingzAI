export function normalizeNullableString(value) {
    return value == null ? null : String(value);
}

export function normalizeNullableNumber(value) {
    if (value == null || value === '') {
        return null;
    }
    const parsed = Number(value);
    return Number.isNaN(parsed) ? null : parsed;
}

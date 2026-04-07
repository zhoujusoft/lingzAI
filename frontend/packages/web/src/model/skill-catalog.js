export function buildSkillCatalogExportPayload(skills = []) {
    return {
        version: 1,
        exportedAt: new Date().toISOString(),
        skills: skills.map(skill => ({
            runtimeSkillName: skill.runtimeSkillName || '',
            displayName: skill.displayName || '',
            description: skill.description || '',
            category: skill.category || '',
            visible: Boolean(skill.visible),
            sortOrder: Number(skill.sortOrder || 0),
        })),
    };
}

export function parseSkillCatalogImportPayload(text) {
    const parsed = JSON.parse(text);
    const source = Array.isArray(parsed) ? parsed : Array.isArray(parsed?.skills) ? parsed.skills : [];
    return source
        .map(item => ({
            runtimeSkillName: typeof item?.runtimeSkillName === 'string' ? item.runtimeSkillName.trim() : '',
            displayName: typeof item?.displayName === 'string' ? item.displayName.trim() : '',
            description: typeof item?.description === 'string' ? item.description.trim() : '',
            category: typeof item?.category === 'string' ? item.category.trim() : '',
            visible: typeof item?.visible === 'boolean' ? item.visible : undefined,
            sortOrder: Number.isFinite(Number(item?.sortOrder)) ? Number(item.sortOrder) : undefined,
        }))
        .filter(item => item.runtimeSkillName);
}

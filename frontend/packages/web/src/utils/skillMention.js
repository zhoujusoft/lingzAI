const SKILL_MENTION_TERMINATOR_PATTERN = /[\s@,.;:!?，。；：！？、()[\]{}<>《》"'“”‘’`]/;

function normalizeText(value) {
    return String(value || '').trim();
}

function normalizeMentionKey(value) {
    return normalizeText(value).toLowerCase();
}

function isSkillMentionPrefix(text, index) {
    return index === 0 || /\s/.test(text[index - 1] || '');
}

function isSkillMentionTerminator(char) {
    return !char || SKILL_MENTION_TERMINATOR_PATTERN.test(char);
}

function buildLookup(skillsOrLookup) {
    if (skillsOrLookup instanceof Map) {
        return skillsOrLookup;
    }
    return buildSkillMentionLookup(skillsOrLookup);
}

function collectResolvedSkillMentions(text, skillsOrLookup, options = {}) {
    const lookup = buildLookup(skillsOrLookup);
    const aliases = Array.from(lookup.keys()).sort((left, right) => right.length - left.length);
    const mentions = [];
    const includeActiveEnd = options.includeActiveEnd === true;

    let index = 0;
    while (index < text.length) {
        if (text[index] !== '@' || !isSkillMentionPrefix(text, index)) {
            index += 1;
            continue;
        }

        const mentionStart = index;
        const mentionBody = text.slice(mentionStart + 1);
        let matchedMention = null;

        for (const alias of aliases) {
            if (!mentionBody.toLowerCase().startsWith(alias)) {
                continue;
            }

            const mentionEnd = mentionStart + 1 + alias.length;
            const nextChar = text[mentionEnd];
            if (!isSkillMentionTerminator(nextChar)) {
                continue;
            }
            if (!includeActiveEnd && mentionEnd === text.length) {
                continue;
            }

            matchedMention = {
                start: mentionStart,
                end: mentionEnd,
                token: text.slice(mentionStart, mentionEnd),
                query: text.slice(mentionStart + 1, mentionEnd),
                skill: lookup.get(alias) || null,
            };
            break;
        }

        if (matchedMention?.skill) {
            mentions.push(matchedMention);
            index = matchedMention.end;
            continue;
        }

        index += 1;
    }

    return mentions;
}

function resolveMentionSkill(label, skillsOrLookup) {
    const key = normalizeMentionKey(label);
    if (!key) {
        return null;
    }
    const lookup = buildLookup(skillsOrLookup);
    return lookup.get(key) || null;
}

export function getSkillMentionText(skill) {
    const displayName = normalizeText(skill?.displayName);
    const runtimeSkillName = normalizeText(skill?.runtimeSkillName);
    if (displayName && !/\s/.test(displayName)) {
        return displayName;
    }
    return runtimeSkillName || displayName;
}

export function getSkillMentionAliases(skill) {
    const displayName = normalizeText(skill?.displayName);
    const runtimeSkillName = normalizeText(skill?.runtimeSkillName);
    const mentionText = getSkillMentionText(skill);
    return Array.from(
        new Set(
            [displayName, runtimeSkillName, mentionText].filter(alias => alias && !/\s/.test(alias))
        )
    );
}

export function buildSkillMentionLookup(skills = []) {
    const lookup = new Map();
    (Array.isArray(skills) ? skills : []).forEach(skill => {
        getSkillMentionAliases(skill).forEach(alias => {
            const key = normalizeMentionKey(alias);
            if (key && !lookup.has(key)) {
                lookup.set(key, skill);
            }
        });
    });
    return lookup;
}

export function getActiveSkillMentionContext(draft, skillsOrLookup) {
    const text = String(draft || '');
    if (!text) {
        return null;
    }

    const start = text.lastIndexOf('@');
    if (start < 0 || !isSkillMentionPrefix(text, start)) {
        return null;
    }

    const token = text.slice(start);
    const query = token.slice(1);
    if ([...query].some(char => isSkillMentionTerminator(char))) {
        return null;
    }

    return {
        start,
        end: start + token.length,
        prefix: start > 0 ? text[start - 1] : '',
        token,
        query: normalizeText(query),
        skill: resolveMentionSkill(query, skillsOrLookup),
    };
}

export function replaceActiveSkillMention(draft, skill) {
    const mentionText = getSkillMentionText(skill);
    if (!mentionText) {
        return String(draft || '');
    }
    const text = String(draft || '');
    const activeMention = getActiveSkillMentionContext(text);
    if (!activeMention) {
        return text;
    }
    return `${text.slice(0, activeMention.start)}@${mentionText} `;
}

export function applySkillMentionSelection(draft, skill) {
    const mentionText = getSkillMentionText(skill);
    if (!mentionText) {
        return String(draft || '');
    }
    const text = String(draft || '');
    if (getActiveSkillMentionContext(text)) {
        return replaceActiveSkillMention(text, skill);
    }
    const separator = text && !/\s$/.test(text) ? ' ' : '';
    return `${text}${separator}@${mentionText} `;
}

export function appendSkillMentionMessage(draft, skill, messageContent = '') {
    const mentionText = getSkillMentionText(skill);
    if (!mentionText) {
        return String(draft || '');
    }
    const text = String(draft || '');
    const trimmedText = text.trim();
    const normalizedMessage = String(messageContent || '').trim();
    const mentionToken = `@${mentionText}`;
    const appendedMessage = normalizedMessage
        ? `${mentionToken} ${normalizedMessage}`
        : mentionToken;
    if (!trimmedText) {
        return appendedMessage;
    }
    if (trimmedText === mentionToken) {
        return appendedMessage;
    }
    const separator = /\s$/.test(text) ? '' : '\n';
    return `${text}${separator}${appendedMessage}`;
}

export function resolveSkillMentionState(draft, skillsOrLookup) {
    const text = String(draft || '');
    const lookup = buildLookup(skillsOrLookup);
    const mentions = collectResolvedSkillMentions(text, lookup);
    const activeMention = getActiveSkillMentionContext(text, lookup);
    if (activeMention) {
        return {
            mentions,
            activeMention,
            primarySkill: activeMention.skill || null,
        };
    }

    const lastResolvedMention = [...mentions].reverse().find(mention => mention.skill);
    return {
        mentions,
        activeMention: null,
        primarySkill: lastResolvedMention?.skill || null,
    };
}

export function stripResolvedSkillMentions(draft, skillsOrLookup) {
    const text = String(draft || '');
    if (!text) {
        return '';
    }

    const { mentions } = resolveSkillMentionState(text, skillsOrLookup);
    const resolvedMentions = mentions.filter(mention => mention.skill);
    if (!resolvedMentions.length) {
        return text;
    }

    let cursor = 0;
    let result = '';
    resolvedMentions.forEach(mention => {
        if (mention.start > cursor) {
            result += text.slice(cursor, mention.start);
        }
        cursor = mention.end;
    });
    if (cursor < text.length) {
        result += text.slice(cursor);
    }

    return result;
}

export function hasSubstantiveSkillMessage(draft, skillsOrLookup) {
    const plainText = stripResolvedSkillMentions(draft, skillsOrLookup);
    return normalizeText(plainText).length > 0;
}

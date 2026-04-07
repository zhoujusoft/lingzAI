function hasLetter(value) {
    return /[A-Za-z]/.test(value);
}

function hasNumber(value) {
    return /\d/.test(value);
}

function hasSymbol(value) {
    return /[^A-Za-z0-9]/.test(value);
}

export function validatePasswordPolicy(password) {
    const value = typeof password === 'string' ? password.trim() : '';
    if (!value) {
        return '密码不能为空';
    }
    if (value.length < 6) {
        return '密码至少6位';
    }

    const categories = [hasLetter(value), hasNumber(value), hasSymbol(value)].filter(Boolean).length;
    if (categories < 2) {
        return '密码需至少包含字母、数字、符号中的两种';
    }

    return '';
}

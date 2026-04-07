import { normalizeNullableNumber, normalizeNullableString } from '@/utils/normalize';

export class UserBean {
    constructor(source = {}) {
        this.id = null;
        this.name = null;
        this.code = null;
        this.userType = null;
        this.mobile = null;
        this.email = null;
        this.state = null;
        this.parentId = null;

        this.assign(source);
    }

    assign(source = {}) {
        this.id = normalizeNullableNumber(source.id);
        this.name = normalizeNullableString(source.name);
        this.code = normalizeNullableString(source.code);
        this.userType = normalizeNullableNumber(source.userType);
        this.mobile = normalizeNullableString(source.mobile);
        this.email = normalizeNullableString(source.email);
        this.state = normalizeNullableNumber(source.state);
        this.parentId = normalizeNullableString(source.parentId);
        return this;
    }

    clone() {
        return new UserBean(this);
    }

    toApiObject() {
        return {
            id: this.id,
            name: this.name,
            code: this.code,
            userType: this.userType,
            mobile: this.mobile,
            email: this.email,
            state: this.state,
            parentId: this.parentId,
        };
    }

    static from(source = {}) {
        return new UserBean(source);
    }

    static fromApi(source = {}) {
        return new UserBean(source);
    }

    static empty() {
        return new UserBean();
    }
}

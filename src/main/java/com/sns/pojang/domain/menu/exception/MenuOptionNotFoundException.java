package com.sns.pojang.domain.menu.exception;

import com.sns.pojang.global.error.ErrorCode;
import com.sns.pojang.global.error.exception.EntityNotFoundException;

public class MenuOptionNotFoundException extends EntityNotFoundException {
    public MenuOptionNotFoundException() {
        super(ErrorCode.MENU_OPTION_NOT_FOUND);
    }
}

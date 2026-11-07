package com.skyrealms.economy.model;

/**
 * Categorization for audit and double-entry transaction journals.
 */
public enum TransactionType {
    DEPOSIT,
    WITHDRAW,
    TRANSFER,
    ADMIN_GIVE,
    ADMIN_TAKE,
    ADMIN_SET
}

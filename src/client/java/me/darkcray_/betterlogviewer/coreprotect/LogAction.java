package me.darkcray_.betterlogviewer.coreprotect;

public enum LogAction {
    PLACE, BREAK, ADD, REMOVE, CLICK, KILL, PICKUP, DROP, DEPOSIT, WITHDRAW, THROW, SHOOT;

    public boolean container() { return this == ADD || this == REMOVE; }
    public boolean itemChange() { return container() || this == PICKUP || this == DROP || this == DEPOSIT || this == WITHDRAW || this == THROW || this == SHOOT; }
}

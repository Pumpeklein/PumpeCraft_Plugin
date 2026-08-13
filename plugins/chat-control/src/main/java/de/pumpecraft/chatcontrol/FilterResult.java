package de.pumpecraft.chatcontrol;

record FilterResult(boolean allowed, String reason) {
    static FilterResult allow() {
        return new FilterResult(true, "");
    }

    static FilterResult block(String reason) {
        return new FilterResult(false, reason);
    }
}

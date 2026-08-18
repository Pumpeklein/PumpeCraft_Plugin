package de.pumpecraft.chatcontrol;

record FilterResult(boolean allowed, boolean reviewRequired, String reason) {
    static FilterResult allow() {
        return new FilterResult(true, false, "");
    }

    static FilterResult review(String reason) {
        return new FilterResult(false, true, reason);
    }

    static FilterResult block(String reason) {
        return new FilterResult(false, false, reason);
    }
}

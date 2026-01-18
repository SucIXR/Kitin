package me.sucixr.kitin.other.old.retention;

/**
 * Policy 只负责“决策”，不负责“怎么对接 chunk 系统”
 */
public record RetentionDecision(
        boolean retain,
        int ticketRadius
) {
    public static RetentionDecision no() {
        return new RetentionDecision(false, 0);
    }
    public static RetentionDecision yesRadius(int radius) {
        return new RetentionDecision(true, Math.max(1, radius));
    }
}

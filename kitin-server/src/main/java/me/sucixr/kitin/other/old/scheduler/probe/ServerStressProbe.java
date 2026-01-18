package me.sucixr.kitin.network.misc;

public class ServerStressProbe {

    private static final ServerStressProbe INSTANCE = new ServerStressProbe();
    public static ServerStressProbe getInstance() { return INSTANCE; }

    private double stressLevel = 1.0; // 1.0 = 轻松, 0.0 = 爆炸

    /**
     * 在 tick 开始时调用
     */
    public void onTickStart(double lastMspt) {
        // EMA
        double target = 1.0 - (Math.max(0, lastMspt - 5.0) / 15.0);
        this.stressLevel = Math.max(0.05, target);
    }

    /**
     * 只提供状态，不提供决策
     */
    public double getStressLevel() {
        return stressLevel;
    }

    public boolean isOverloaded() {
        return stressLevel < 0.5;
    }
}

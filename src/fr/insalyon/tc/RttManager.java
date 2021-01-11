package fr.insalyon.tc;

public class RttManager {

    private int sRtt = 40;
    private int rttVariance = 5;

    private int MAX_TIMEOUT = 300;
    private int MIN_TIMEOUT = 5;

    private long[] startTimestamps = new long[999999];
    private double alpha = 0.125;
    private double beta = 0.25;

    public RttManager() {
    }

    private int updateSmoothedRTT(long sampleRTT) {
        this.sRtt = (int) ((1-alpha) * sRtt + alpha * sampleRTT);
        this.rttVariance = (int) ((1-beta) * rttVariance + beta * Math.abs(sampleRTT - sRtt));
        return this.sRtt;
    }

    public int getTimeoutDelay() {
        return Math.max(MIN_TIMEOUT, Math.min(sRtt + 4 * rttVariance, MAX_TIMEOUT));
    }

    public void startTimecounter(int segNumber) {
        this.startTimestamps[segNumber] = System.currentTimeMillis();
    }

    public long calculateRtt(int segNumber) {
        long currentRtt = System.currentTimeMillis() - startTimestamps[segNumber];
        updateSmoothedRTT(currentRtt);
        //System.out.println("sRTT = " + sRtt + " +/- " + rttVariance + " ms." );
        return currentRtt;
    }

    public long getSRtt() {
        return this.sRtt;
    }

}

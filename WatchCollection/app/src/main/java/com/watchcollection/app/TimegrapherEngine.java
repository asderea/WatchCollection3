package com.watchcollection.app;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.MediaRecorder;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 스마트폰 마이크 기반 timegrapher 엔진 v2.
 *
 * v0.8 핵심
 * - 3개 주파수 대역을 동시에 분석해 시계/휴대폰 조합에 유리한 대역을 자동 사용
 * - envelope autocorrelation + 이벤트 간격 점수를 결합해 일반 BPH를 빠르게 lock
 * - BPH lock 후 예상 beat 시점 주변의 threshold를 낮춰 약한 tick/tock도 추적
 * - 최소 8초부터 저장 가능하고, 2~4초 사이부터 잠정 BPH/Rate를 표시
 * - AudioTimestamp가 제공되면 실제 audio sample clock 편차를 rate 계산에 보정
 *
 * 전문 contact microphone timegrapher를 대체하는 계측 장비는 아닙니다.
 * 신호가 약하거나 impulse 내부 구조가 불명확하면 amplitude는 NaN으로 남깁니다.
 */
public final class TimegrapherEngine {
    public static final int[] COMMON_BPH = {18000, 19800, 21600, 25200, 28800, 36000};

    private static final int PREFERRED_SAMPLE_RATE = 48000;
    private static final int FALLBACK_SAMPLE_RATE = 44100;
    private static final int MAX_BEATS = 1200;

    // 멀티밴드: 저역 시계 / 일반 / 고역 impulse를 동시에 커버합니다.
    private static final double[] BAND_LOW_HZ = {320.0, 850.0, 1800.0};
    private static final double[] BAND_HIGH_HZ = {1300.0, 3000.0, 6200.0};
    private static final int BAND_COUNT = 3;

    // autocorrelation은 beat 주기(약 5~10 Hz)만 보면 되므로 envelope를 500 Hz로 다운샘플합니다.
    private static final int ENERGY_RATE = 500;
    private static final int ENERGY_SECONDS = 8;
    private static final int ENERGY_CAPACITY = ENERGY_RATE * ENERGY_SECONDS;

    public interface Listener {
        void onSnapshot(Snapshot snapshot);
        void onError(String message);
    }

    public static final class Snapshot {
        public final boolean running;
        public final int bph;
        public final boolean bphLocked;
        public final double rateSecDay;
        public final double beatErrorMs;
        public final double amplitudeDeg;
        public final double signalQuality;
        public final int beatCount;
        public final double elapsedSeconds;
        public final List<Double> traceMs;
        public final String status;

        Snapshot(boolean running, int bph, boolean bphLocked,
                 double rateSecDay, double beatErrorMs, double amplitudeDeg,
                 double signalQuality, int beatCount, double elapsedSeconds,
                 List<Double> traceMs, String status) {
            this.running = running;
            this.bph = bph;
            this.bphLocked = bphLocked;
            this.rateSecDay = rateSecDay;
            this.beatErrorMs = beatErrorMs;
            this.amplitudeDeg = amplitudeDeg;
            this.signalQuality = signalQuality;
            this.beatCount = beatCount;
            this.elapsedSeconds = elapsedSeconds;
            this.traceMs = traceMs;
            this.status = status;
        }

        /** 8초 이후부터 저장 허용. 약한 신호에서도 지나치게 오래 기다리지 않도록 완화했습니다. */
        public boolean isUsable() {
            return bph > 0 && bphLocked && !Double.isNaN(rateSecDay)
                    && beatCount >= 18 && elapsedSeconds >= 8.0 && signalQuality >= 20.0;
        }

        public String qualityLabel() {
            if (signalQuality >= 78) return "Excellent";
            if (signalQuality >= 56) return "Good";
            if (signalQuality >= 30) return "Fair";
            return "Weak";
        }
    }

    private static final class Beat {
        final double timeSec;
        final double liftTimeSec;
        Beat(double timeSec, double liftTimeSec) {
            this.timeSec = timeSec;
            this.liftTimeSec = liftTimeSec;
        }
    }

    private static final class Fit {
        int bph;
        double slope;
        double parityOffset;
        double rate;
        double beatErrorMs;
        double rmsMs;
        double quality;
        List<Double> trace = new ArrayList<>();
        List<Beat> accepted = new ArrayList<>();
    }

    private static final class BphDecision {
        int bph;
        double score;
        double secondScore;
        double autocorr;
        double intervalScore;
    }

    private final Listener listener;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;
    private AudioRecord recorder;
    private final List<Beat> beats = new ArrayList<>();
    private volatile double liftAngleDeg = 52.0;
    private volatile Snapshot latest = idleSnapshot("대기 중");
    private long startElapsedMs;

    // DSP state: 각 대역마다 HP -> LP로 간단한 band-pass를 구성합니다.
    private final double[] prevX = new double[BAND_COUNT];
    private final double[] prevHp = new double[BAND_COUNT];
    private final double[] prevLp = new double[BAND_COUNT];
    private final double[] bandEnvelope = new double[BAND_COUNT];
    private final double[] bandNoise = {0.0010, 0.0010, 0.0010};

    private long globalSample = 0;
    private long lastBeatSample = Long.MIN_VALUE / 4;
    private int activeSampleRate = PREFERRED_SAMPLE_RATE;
    private volatile double effectiveSampleRate = PREFERRED_SAMPLE_RATE;

    // Event cluster state
    private boolean inEvent = false;
    private long eventStartSample;
    private long eventPeakSample;
    private double eventPeakValue;
    private double eventNoiseAtStart;
    private boolean eventPredicted;
    private int eventQuietSamples;
    private int eventLength;
    private final double[] eventEnvelope = new double[(int) (PREFERRED_SAMPLE_RATE * 0.040) + 96];

    // Envelope history for autocorrelation
    private final float[] energyHistory = new float[ENERGY_CAPACITY];
    private int energyWrite = 0;
    private int energyCount = 0;
    private int energyStride = PREFERRED_SAMPLE_RATE / ENERGY_RATE;
    private int energyStrideCounter = 0;
    private double energyAccumulator = 0.0;

    // BPH lock / hysteresis
    private volatile int lockedBph = 0;
    private int tentativeBph = 0;
    private int tentativeHits = 0;
    private int switchCandidate = 0;
    private int switchHits = 0;

    private long lastPublishMs;
    private long audioClockBaseFrame = -1;
    private long audioClockBaseNs = -1;

    public TimegrapherEngine(Listener listener) {
        this.listener = listener;
    }

    public synchronized void setLiftAngleDeg(double value) {
        if (!Double.isNaN(value) && value >= 30 && value <= 70) liftAngleDeg = value;
    }

    public Snapshot getLatest() { return latest; }
    public boolean isRunning() { return running.get(); }

    @SuppressLint("MissingPermission")
    public synchronized void start() {
        if (running.get()) return;
        resetState();
        try {
            recorder = createRecorder();
            if (recorder == null || recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                releaseRecorder();
                notifyError("마이크를 초기화할 수 없습니다.");
                return;
            }
            recorder.startRecording();
            running.set(true);
            startElapsedMs = SystemClock.elapsedRealtime();
            worker = new Thread(this::recordLoop, "WatchTimegrapherV2");
            worker.start();
        } catch (Exception e) {
            releaseRecorder();
            notifyError("측정을 시작하지 못했습니다: " + safeMessage(e));
        }
    }

    public synchronized void stop() {
        running.set(false);
        if (recorder != null) {
            try { recorder.stop(); } catch (Exception ignored) {}
        }
        Thread t = worker;
        worker = null;
        if (t != null && t != Thread.currentThread()) {
            try { t.join(350); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        releaseRecorder();
        Snapshot s = analyze(false);
        latest = s;
        if (listener != null) listener.onSnapshot(s);
    }

    @SuppressLint("MissingPermission")
    private AudioRecord createRecorder() {
        int[] rates = {PREFERRED_SAMPLE_RATE, FALLBACK_SAMPLE_RATE};
        // 약한 기계음은 일반 MIC의 하드웨어 gain이 유리한 기기가 많아 MIC를 우선합니다.
        // 기기가 불필요한 처리를 강하게 하는 경우 UNPROCESSED로 fallback합니다.
        int[] sources = {MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.UNPROCESSED};
        for (int rate : rates) {
            int min = AudioRecord.getMinBufferSize(rate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) continue;
            int bufferBytes = Math.max(min * 3, rate);
            for (int source : sources) {
                AudioRecord r = null;
                try {
                    r = new AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT, bufferBytes);
                    if (r.getState() == AudioRecord.STATE_INITIALIZED) {
                        activeSampleRate = rate;
                        effectiveSampleRate = rate;
                        energyStride = Math.max(1, rate / ENERGY_RATE);
                        return r;
                    }
                    r.release();
                } catch (Exception ignored) {
                    if (r != null) try { r.release(); } catch (Exception ignored2) {}
                }
            }
        }
        return null;
    }

    private void recordLoop() {
        short[] buffer = new short[2048];
        while (running.get()) {
            int n;
            try {
                n = recorder.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
            } catch (Exception e) {
                if (running.get()) notifyError("마이크 입력 오류: " + safeMessage(e));
                break;
            }
            if (n <= 0) continue;
            process(buffer, n);
            updateAudioClock();
            long now = SystemClock.elapsedRealtime();
            if (now - lastPublishMs >= 180) {
                lastPublishMs = now;
                Snapshot s = analyze(true);
                latest = s;
                if (listener != null) listener.onSnapshot(s);
            }
        }
        running.set(false);
    }

    private void updateAudioClock() {
        AudioRecord r = recorder;
        if (r == null) return;
        try {
            AudioTimestamp ts = new AudioTimestamp();
            int result = r.getTimestamp(ts, AudioTimestamp.TIMEBASE_MONOTONIC);
            if (result != AudioRecord.SUCCESS) return;
            if (audioClockBaseFrame < 0) {
                audioClockBaseFrame = ts.framePosition;
                audioClockBaseNs = ts.nanoTime;
                return;
            }
            long df = ts.framePosition - audioClockBaseFrame;
            long dn = ts.nanoTime - audioClockBaseNs;
            if (df < activeSampleRate * 3L || dn <= 0) return;
            double measured = df / (dn / 1_000_000_000.0);
            if (measured > activeSampleRate * 0.96 && measured < activeSampleRate * 1.04) {
                effectiveSampleRate = measured;
            }
        } catch (Exception ignored) {}
    }

    private void process(short[] input, int count) {
        final double dt = 1.0 / activeSampleRate;
        final double[] hpAlpha = new double[BAND_COUNT];
        final double[] lpAlpha = new double[BAND_COUNT];
        for (int b = 0; b < BAND_COUNT; b++) {
            double rcHp = 1.0 / (2.0 * Math.PI * BAND_LOW_HZ[b]);
            hpAlpha[b] = rcHp / (rcHp + dt);
            double rcLp = 1.0 / (2.0 * Math.PI * BAND_HIGH_HZ[b]);
            lpAlpha[b] = dt / (rcLp + dt);
        }

        for (int i = 0; i < count; i++, globalSample++) {
            double x = input[i] / 32768.0;
            int bestBand = 0;
            double bestRatio = -1.0;
            double bestEnv = 0.0;
            double bestNoise = 0.001;

            for (int b = 0; b < BAND_COUNT; b++) {
                double hp = hpAlpha[b] * (prevHp[b] + x - prevX[b]);
                prevX[b] = x;
                prevHp[b] = hp;
                double lp = prevLp[b] + lpAlpha[b] * (hp - prevLp[b]);
                prevLp[b] = lp;

                double abs = Math.abs(lp);
                bandEnvelope[b] = 0.80 * bandEnvelope[b] + 0.20 * abs;
                double env = bandEnvelope[b];
                double noise = bandNoise[b];

                // 이벤트가 아닌 조용한 구간을 중심으로 noise floor를 추종합니다.
                if (!inEvent || env < noise * 2.2) {
                    double k = env < noise ? 0.006 : 0.00045;
                    noise = (1.0 - k) * noise + k * env;
                    bandNoise[b] = clamp(noise, 0.00010, 0.08);
                }

                double ratio = env / Math.max(0.00010, bandNoise[b]);
                if (ratio > bestRatio) {
                    bestRatio = ratio;
                    bestBand = b;
                    bestEnv = env;
                    bestNoise = bandNoise[b];
                }
            }

            // autocorrelation용 energy history. 주파수 대역마다 절대 레벨이 달라도 ratio로 정규화됩니다.
            double normalizedEnergy = clamp(bestRatio - 1.0, 0.0, 12.0);
            energyAccumulator = Math.max(energyAccumulator, normalizedEnergy);
            energyStrideCounter++;
            if (energyStrideCounter >= energyStride) {
                pushEnergy((float) energyAccumulator);
                energyStrideCounter = 0;
                energyAccumulator = 0.0;
            }

            long sinceLast = globalSample - lastBeatSample;
            boolean predicted = isNearPredictedBeat(sinceLast);
            double multiplier = predicted ? 1.85 : 3.25;
            double threshold = Math.max(predicted ? 0.00045 : 0.00070, bestNoise * multiplier);

            if (!inEvent) {
                // 36,000 bph에서도 beat 간격은 100 ms이므로 43 ms refractory는 안전합니다.
                if (bestEnv > threshold && sinceLast > (long) (activeSampleRate * 0.043)) {
                    beginEvent(bestEnv, bestNoise, predicted);
                }
            } else {
                if (eventLength < eventEnvelope.length) eventEnvelope[eventLength] = bestEnv;
                eventLength++;
                if (bestEnv > eventPeakValue) {
                    eventPeakValue = bestEnv;
                    eventPeakSample = globalSample;
                }
                double quietLevel = Math.max(0.00035, eventNoiseAtStart * (eventPredicted ? 1.35 : 1.7));
                if (bestEnv < quietLevel) eventQuietSamples++; else eventQuietSamples = 0;
                long eventSamples = globalSample - eventStartSample;
                boolean quietEnded = eventSamples > activeSampleRate * 0.0022
                        && eventQuietSamples > activeSampleRate * 0.0015;
                boolean timeout = eventSamples > activeSampleRate * 0.034;
                if (quietEnded || timeout) finishEvent();
            }
        }
    }

    private boolean isNearPredictedBeat(long sinceLast) {
        int bph = lockedBph;
        if (bph <= 0 || sinceLast <= 0 || lastBeatSample < 0) return false;
        double interval = activeSampleRate * 3600.0 / bph;
        int n = Math.max(1, (int) Math.round(sinceLast / interval));
        if (n > 5) return false;
        double phase = Math.abs(sinceLast - n * interval);
        double window = Math.max(activeSampleRate * 0.016, interval * 0.14);
        return phase <= window;
    }

    private void beginEvent(double value, double noise, boolean predicted) {
        inEvent = true;
        eventStartSample = globalSample;
        eventPeakSample = globalSample;
        eventPeakValue = value;
        eventNoiseAtStart = Math.max(0.00010, noise);
        eventPredicted = predicted;
        eventQuietSamples = 0;
        eventLength = 0;
        if (eventEnvelope.length > 0) eventEnvelope[0] = value;
    }

    private void finishEvent() {
        inEvent = false;
        double acceptMultiplier = eventPredicted ? 1.55 : 2.55;
        if (eventPeakValue < Math.max(eventPredicted ? 0.00050 : 0.00080,
                eventNoiseAtStart * acceptMultiplier)) return;

        double time = eventPeakSample / (double) activeSampleRate;
        double lift = estimateLiftTime(Math.min(eventLength, eventEnvelope.length));
        lastBeatSample = eventPeakSample;
        synchronized (beats) {
            beats.add(new Beat(time, lift));
            if (beats.size() > MAX_BEATS) beats.remove(0);
        }
    }

    private double estimateLiftTime(int len) {
        if (len < 8) return Double.NaN;
        double max = 0;
        for (int i = 0; i < len; i++) max = Math.max(max, eventEnvelope[i]);
        double cut = Math.max(eventNoiseAtStart * 2.4, max * 0.24);
        int minSep = (int) (activeSampleRate * 0.00075);
        List<Integer> peaks = new ArrayList<>();
        int last = -minSep;
        for (int i = 2; i < len - 2; i++) {
            double v = eventEnvelope[i];
            if (v < cut) continue;
            if (v >= eventEnvelope[i - 1] && v >= eventEnvelope[i + 1] && i - last >= minSep) {
                peaks.add(i);
                last = i;
            }
        }
        if (peaks.size() < 2) return Double.NaN;
        int first = peaks.get(0);
        int chosen = -1;
        int maxSpan = (int) (activeSampleRate * 0.0155);
        for (int i = 1; i < peaks.size(); i++) {
            int p = peaks.get(i);
            if (p - first <= maxSpan) chosen = p; else break;
        }
        if (chosen < 0) return Double.NaN;
        double seconds = (chosen - first) / (double) activeSampleRate;
        if (seconds < 0.0018 || seconds > 0.0155) return Double.NaN;
        return seconds;
    }

    private void pushEnergy(float value) {
        energyHistory[energyWrite] = value;
        energyWrite = (energyWrite + 1) % ENERGY_CAPACITY;
        if (energyCount < ENERGY_CAPACITY) energyCount++;
    }

    private Snapshot analyze(boolean currentlyRunning) {
        List<Beat> copy;
        synchronized (beats) { copy = new ArrayList<>(beats); }
        double elapsed = startElapsedMs == 0 ? 0
                : (SystemClock.elapsedRealtime() - startElapsedMs) / 1000.0;

        BphDecision decision = decideBph(copy);
        updateBphLock(decision, elapsed);
        int displayBph = lockedBph > 0 ? lockedBph : decision.bph;
        boolean locked = lockedBph > 0;

        if (displayBph == 0) {
            double q = Math.min(22.0, copy.size() * 2.3 + Math.min(10.0, elapsed * 2.0));
            return new Snapshot(currentlyRunning, 0, false, Double.NaN, Double.NaN, Double.NaN,
                    q, copy.size(), elapsed, Collections.emptyList(),
                    elapsed < 1.7 ? "노이즈/주기 분석 중" : "틱/톡 주기를 찾는 중");
        }

        Fit fit = robustFit(copy, displayBph);
        if (fit == null || fit.accepted.size() < 6) {
            double q = clamp(18.0 + decision.score * 28.0, 15.0, 42.0);
            return new Snapshot(currentlyRunning, displayBph, locked,
                    Double.NaN, Double.NaN, Double.NaN, q, copy.size(), elapsed,
                    Collections.emptyList(), locked ? "BPH LOCK · Rate 안정화 중" : "BPH 잠정 분석 중");
        }

        double amplitude = estimateAmplitude(fit.accepted, fit.slope, liftAngleDeg);
        String status;
        if (!locked) status = "BPH 잠정값 · 주기 확인 중";
        else if (fit.quality >= 72) status = "LOCKED · 측정 안정적";
        else if (fit.quality >= 45) status = "LOCKED · 측정 중";
        else status = "LOCKED · 약한 신호 추적 중";

        return new Snapshot(currentlyRunning, displayBph, locked,
                fit.rate, fit.beatErrorMs, amplitude, fit.quality,
                fit.accepted.size(), elapsed, fit.trace, status);
    }

    private BphDecision decideBph(List<Beat> source) {
        BphDecision out = new BphDecision();
        double best = -1e9;
        double second = -1e9;
        int bestBph = 0;
        double bestAuto = 0;
        double bestInterval = 0;

        for (int candidate : COMMON_BPH) {
            double ac = autocorrelationScore(candidate);
            double iv = intervalScore(source, candidate);

            // 초반에는 autocorrelation이 빠른 BPH lock에 더 중요하고,
            // 이벤트가 충분해지면 interval consistency의 비중을 키웁니다.
            double eventWeight = Math.min(0.58, source.size() / 55.0);
            double autoWeight = 1.0 - eventWeight;
            double score = autoWeight * ac + eventWeight * iv;

            if (score > best) {
                second = best;
                best = score;
                bestBph = candidate;
                bestAuto = ac;
                bestInterval = iv;
            } else if (score > second) {
                second = score;
            }
        }

        out.bph = bestBph;
        out.score = Math.max(0.0, best);
        out.secondScore = Math.max(0.0, second);
        out.autocorr = bestAuto;
        out.intervalScore = bestInterval;

        // 아직 신호 근거가 거의 없으면 후보를 표시하지 않습니다.
        if (energyCount < ENERGY_RATE && source.size() < 4) out.bph = 0;
        if (best < 0.12 && source.size() < 7) out.bph = 0;
        return out;
    }

    private void updateBphLock(BphDecision d, double elapsed) {
        if (d.bph == 0) return;
        double margin = d.score - d.secondScore;

        if (lockedBph == 0) {
            if (tentativeBph == d.bph) tentativeHits++; else {
                tentativeBph = d.bph;
                tentativeHits = 1;
            }
            // 약 2초 이후, 두 번 연속 같은 후보이며 최소한의 score/margin이 있으면 lock.
            boolean enough = elapsed >= 1.8 && d.score >= 0.18 && margin >= 0.015;
            if (enough && tentativeHits >= 2) {
                lockedBph = d.bph;
                switchCandidate = 0;
                switchHits = 0;
            }
            return;
        }

        if (d.bph == lockedBph) {
            switchCandidate = 0;
            switchHits = 0;
            return;
        }

        // 잘못된 lock을 교정하되, 한두 번의 noise로 BPH가 출렁이지 않게 hysteresis를 둡니다.
        if (d.score >= 0.32 && margin >= 0.06) {
            if (switchCandidate == d.bph) switchHits++; else {
                switchCandidate = d.bph;
                switchHits = 1;
            }
            if (switchHits >= 5) {
                lockedBph = d.bph;
                switchCandidate = 0;
                switchHits = 0;
            }
        }
    }

    /** 500 Hz envelope에서 후보 beat interval의 normalized autocorrelation을 계산합니다. */
    private double autocorrelationScore(int bph) {
        int available = Math.min(energyCount, ENERGY_RATE * 5);
        if (available < ENERGY_RATE) return 0.0;
        int lag = Math.max(1, (int) Math.round(ENERGY_RATE * 3600.0 / bph));
        if (available <= lag + 20) return 0.0;

        float[] x = chronologicalEnergy(available);
        int start = Math.max(lag, available - ENERGY_RATE * 4);
        double dot = 0, aa = 0, bb = 0;
        double dot2 = 0, aa2 = 0, bb2 = 0;
        int lag2 = lag * 2;

        for (int i = start; i < available; i++) {
            double a = x[i];
            double b = x[i - lag];
            dot += a * b;
            aa += a * a;
            bb += b * b;
            if (i >= lag2) {
                double c = x[i - lag2];
                dot2 += a * c;
                aa2 += a * a;
                bb2 += c * c;
            }
        }
        double corr1 = dot / Math.sqrt(Math.max(1e-9, aa * bb));
        double corr2 = dot2 / Math.sqrt(Math.max(1e-9, aa2 * bb2));

        // beat 위치의 sharpness를 보강: 정확한 lag가 ±몇 sample보다 더 강해야 합니다.
        double side = 0.0;
        int sideCount = 0;
        for (int delta : new int[]{-5, -3, 3, 5}) {
            int l = lag + delta;
            if (l < 1 || available <= l + 10) continue;
            double d = 0, p = 0, q = 0;
            for (int i = Math.max(l, available - ENERGY_RATE * 3); i < available; i++) {
                double a = x[i], b = x[i - l];
                d += a * b; p += a * a; q += b * b;
            }
            side += d / Math.sqrt(Math.max(1e-9, p * q));
            sideCount++;
        }
        double sideAvg = sideCount == 0 ? 0 : side / sideCount;
        double sharp = Math.max(0, corr1 - sideAvg);
        return clamp(corr1 * 0.68 + corr2 * 0.20 + sharp * 0.40, 0, 1);
    }

    private float[] chronologicalEnergy(int n) {
        n = Math.min(n, energyCount);
        float[] out = new float[n];
        int start = (energyWrite - n + ENERGY_CAPACITY) % ENERGY_CAPACITY;
        for (int i = 0; i < n; i++) out[i] = energyHistory[(start + i) % ENERGY_CAPACITY];
        return out;
    }

    private double intervalScore(List<Beat> source, int candidate) {
        if (source.size() < 3) return 0.0;
        double t = 3600.0 / candidate;
        int from = Math.max(1, source.size() - 100);
        double sum = 0;
        double weight = 0;
        for (int i = from; i < source.size(); i++) {
            double d = source.get(i).timeSec - source.get(i - 1).timeSec;
            int step = (int) Math.round(d / t);
            if (step < 1 || step > 5) continue;
            double relative = Math.abs(d / step - t) / t;
            if (relative > 0.20) continue;
            double local = 1.0 - relative / 0.20;
            double w = step == 1 ? 1.0 : (step == 2 ? 0.82 : 0.62);
            sum += local * w;
            weight += w;
        }
        return weight <= 0 ? 0.0 : clamp(sum / weight, 0, 1);
    }

    private Fit robustFit(List<Beat> source, int bph) {
        if (source.size() < 5) return null;
        double ideal = 3600.0 / bph;
        double clockScale = activeSampleRate / effectiveSampleRate;
        int start = Math.max(0, source.size() - 520);
        List<Beat> accepted = new ArrayList<>();
        List<Double> indexes = new ArrayList<>();
        Beat prev = source.get(start);
        accepted.add(prev);
        indexes.add(0.0);
        long beatIndex = 0;

        for (int i = start + 1; i < source.size(); i++) {
            Beat cur = source.get(i);
            double d = cur.timeSec - prev.timeSec;
            int step = (int) Math.round(d / ideal);
            if (step < 1 || step > 6) continue;
            double err = Math.abs(d - step * ideal);
            // lock 후 predictive tracker가 약한 beat를 복구하므로 tolerance를 20%까지 허용하되 회귀가 평균화합니다.
            if (err > ideal * 0.20) continue;
            beatIndex += step;
            accepted.add(cur);
            indexes.add((double) beatIndex);
            prev = cur;
        }
        if (accepted.size() < 5) return null;

        double t0 = accepted.get(0).timeSec;
        int n = accepted.size();
        double[][] ata = new double[3][3];
        double[] aty = new double[3];
        for (int i = 0; i < n; i++) {
            double x = indexes.get(i);
            double z = (((long) Math.round(x)) & 1L) == 0 ? 0.0 : 1.0;
            double y = (accepted.get(i).timeSec - t0) * clockScale;
            double[] row = {1.0, x, z};
            for (int r = 0; r < 3; r++) {
                aty[r] += row[r] * y;
                for (int c = 0; c < 3; c++) ata[r][c] += row[r] * row[c];
            }
        }
        double[] beta = solve3(ata, aty);
        if (beta == null || beta[1] <= 0) return null;

        Fit f = new Fit();
        f.bph = bph;
        f.slope = beta[1];
        f.parityOffset = beta[2];
        f.rate = (ideal / f.slope - 1.0) * 86400.0;
        f.beatErrorMs = Math.abs(f.parityOffset) * 1000.0;
        f.accepted = accepted;

        // 명백히 비현실적인 초기 회귀는 아직 잠정값으로도 표시하지 않습니다.
        if (Math.abs(f.rate) > 900 || f.beatErrorMs > 20) return null;

        double sumSq = 0;
        List<Double> trace = new ArrayList<>();
        int traceStart = Math.max(0, n - 180);
        double nominalBase = accepted.get(traceStart).timeSec * clockScale - indexes.get(traceStart) * ideal;
        for (int i = 0; i < n; i++) {
            double x = indexes.get(i);
            double z = (((long) Math.round(x)) & 1L) == 0 ? 0.0 : 1.0;
            double y = (accepted.get(i).timeSec - t0) * clockScale;
            double pred = beta[0] + beta[1] * x + beta[2] * z;
            double residual = (y - pred) * 1000.0;
            sumSq += residual * residual;
            if (i >= traceStart) {
                double phase = (accepted.get(i).timeSec * clockScale - x * ideal - nominalBase) * 1000.0;
                trace.add(clamp(phase, -30.0, 30.0));
            }
        }
        f.trace = trace;
        f.rmsMs = Math.sqrt(sumSq / n);

        double span = Math.max(0.01,
                (accepted.get(n - 1).timeSec - accepted.get(0).timeSec) * clockScale);
        double expected = Math.max(1.0, span / ideal + 1.0);
        double captureRatio = Math.min(1.0, n / expected);
        double stability = 1.0 - Math.min(1.0, f.rmsMs / 2.2);
        double durationFactor = Math.min(1.0, span / 12.0);
        double sampleFactor = Math.min(1.0, n / 55.0);
        f.quality = clamp(captureRatio * 42.0 + stability * 32.0
                + durationFactor * 14.0 + sampleFactor * 12.0, 0, 100);
        return f;
    }

    private double estimateAmplitude(List<Beat> accepted, double beatInterval, double liftAngle) {
        List<Double> amps = new ArrayList<>();
        double omega = Math.PI / beatInterval;
        double liftRad = Math.toRadians(liftAngle);
        int from = Math.max(0, accepted.size() - 100);
        for (int i = from; i < accepted.size(); i++) {
            double lt = accepted.get(i).liftTimeSec;
            if (Double.isNaN(lt)) continue;
            lt *= activeSampleRate / effectiveSampleRate;
            double denom = 2.0 * Math.sin(omega * lt / 2.0);
            if (Math.abs(denom) < 1e-7) continue;
            double amp = Math.toDegrees(liftRad / denom);
            if (amp >= 120 && amp <= 360) amps.add(amp);
        }
        if (amps.size() < 6) return Double.NaN;
        Collections.sort(amps);
        int lo = (int) Math.floor(amps.size() * 0.20);
        int hi = (int) Math.ceil(amps.size() * 0.80);
        double sum = 0;
        int count = 0;
        for (int i = lo; i < hi; i++) { sum += amps.get(i); count++; }
        return count == 0 ? Double.NaN : sum / count;
    }

    private static double[] solve3(double[][] a, double[] b) {
        double[][] m = new double[3][4];
        for (int r = 0; r < 3; r++) {
            System.arraycopy(a[r], 0, m[r], 0, 3);
            m[r][3] = b[r];
        }
        for (int col = 0; col < 3; col++) {
            int pivot = col;
            for (int r = col + 1; r < 3; r++) {
                if (Math.abs(m[r][col]) > Math.abs(m[pivot][col])) pivot = r;
            }
            if (Math.abs(m[pivot][col]) < 1e-12) return null;
            double[] tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp;
            double div = m[col][col];
            for (int c = col; c < 4; c++) m[col][c] /= div;
            for (int r = 0; r < 3; r++) {
                if (r == col) continue;
                double factor = m[r][col];
                for (int c = col; c < 4; c++) m[r][c] -= factor * m[col][c];
            }
        }
        return new double[]{m[0][3], m[1][3], m[2][3]};
    }

    private void resetState() {
        synchronized (beats) { beats.clear(); }
        for (int i = 0; i < BAND_COUNT; i++) {
            prevX[i] = 0; prevHp[i] = 0; prevLp[i] = 0;
            bandEnvelope[i] = 0; bandNoise[i] = 0.0010;
        }
        globalSample = 0;
        lastBeatSample = Long.MIN_VALUE / 4;
        inEvent = false;
        eventLength = 0;
        eventQuietSamples = 0;
        eventPeakValue = 0;
        eventNoiseAtStart = 0.001;
        eventPredicted = false;
        energyWrite = 0;
        energyCount = 0;
        energyStrideCounter = 0;
        energyAccumulator = 0;
        for (int i = 0; i < energyHistory.length; i++) energyHistory[i] = 0;
        lockedBph = 0;
        tentativeBph = 0;
        tentativeHits = 0;
        switchCandidate = 0;
        switchHits = 0;
        startElapsedMs = 0;
        lastPublishMs = 0;
        audioClockBaseFrame = -1;
        audioClockBaseNs = -1;
        effectiveSampleRate = activeSampleRate;
        latest = idleSnapshot("측정 준비");
    }

    private Snapshot idleSnapshot(String status) {
        return new Snapshot(false, 0, false, Double.NaN, Double.NaN, Double.NaN,
                0, 0, 0, Collections.emptyList(), status);
    }

    private void releaseRecorder() {
        AudioRecord r = recorder;
        recorder = null;
        if (r != null) try { r.release(); } catch (Exception ignored) {}
    }

    private void notifyError(String message) {
        if (listener != null) listener.onError(message);
    }

    private static String safeMessage(Exception e) {
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static String commonBphText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < COMMON_BPH.length; i++) {
            if (i > 0) sb.append(" / ");
            sb.append(String.format(Locale.ROOT, "%,d", COMMON_BPH[i]));
        }
        return sb.toString();
    }
}

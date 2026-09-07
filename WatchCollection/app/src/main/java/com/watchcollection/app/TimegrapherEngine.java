package com.watchcollection.app;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.MediaRecorder;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 스마트폰 마이크를 이용한 로컬 timegrapher 엔진.
 *
 * 처리 흐름
 * 1) 48 kHz PCM 입력
 * 2) 1차 high-pass + envelope 추출
 * 3) adaptive threshold로 escapement impulse cluster 검출
 * 4) 일반 BPH 후보 중 robust matching
 * 5) beat index + parity를 포함한 선형 회귀로 rate / beat error 추정
 * 6) 한 beat 내부의 impulse span과 lift angle로 amplitude를 보조 추정
 *
 * 휴대폰 마이크 기반 측정이므로 전문 contact microphone timegrapher와 같은
 * 계측 보증을 제공하지 않습니다. 신호가 약하면 amplitude는 NaN으로 남깁니다.
 */
public final class TimegrapherEngine {
    public static final int[] COMMON_BPH = {18000, 19800, 21600, 25200, 28800, 36000};
    private static final int PREFERRED_SAMPLE_RATE = 48000;
    private static final int FALLBACK_SAMPLE_RATE = 44100;
    private static final double HP_CUTOFF_HZ = 900.0;
    private static final int MAX_BEATS = 900;

    public interface Listener {
        void onSnapshot(Snapshot snapshot);
        void onError(String message);
    }

    public static final class Snapshot {
        public final boolean running;
        public final int bph;
        public final double rateSecDay;
        public final double beatErrorMs;
        public final double amplitudeDeg;
        public final double signalQuality;
        public final int beatCount;
        public final double elapsedSeconds;
        public final List<Double> traceMs;
        public final String status;

        Snapshot(boolean running, int bph, double rateSecDay, double beatErrorMs,
                 double amplitudeDeg, double signalQuality, int beatCount,
                 double elapsedSeconds, List<Double> traceMs, String status) {
            this.running = running;
            this.bph = bph;
            this.rateSecDay = rateSecDay;
            this.beatErrorMs = beatErrorMs;
            this.amplitudeDeg = amplitudeDeg;
            this.signalQuality = signalQuality;
            this.beatCount = beatCount;
            this.elapsedSeconds = elapsedSeconds;
            this.traceMs = traceMs;
            this.status = status;
        }

        public boolean isUsable() {
            return bph > 0 && !Double.isNaN(rateSecDay) && beatCount >= 45
                    && elapsedSeconds >= 15.0 && signalQuality >= 30.0;
        }

        public String qualityLabel() {
            if (signalQuality >= 78) return "Excellent";
            if (signalQuality >= 58) return "Good";
            if (signalQuality >= 35) return "Fair";
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

    private final Listener listener;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;
    private AudioRecord recorder;
    private final List<Beat> beats = new ArrayList<>();
    private volatile double liftAngleDeg = 52.0;
    private volatile Snapshot latest = idleSnapshot("대기 중");
    private long startElapsedMs;

    // DSP state
    private double prevX = 0.0;
    private double prevHp = 0.0;
    private double envelope = 0.0;
    private double noiseFloor = 0.0012;
    private long globalSample = 0;
    private long lastBeatSample = Long.MIN_VALUE / 4;

    private boolean inEvent = false;
    private long eventStartSample;
    private long eventPeakSample;
    private double eventPeakValue;
    private int eventQuietSamples;
    private final double[] eventEnvelope = new double[(int) (PREFERRED_SAMPLE_RATE * 0.035) + 64];
    private int activeSampleRate = PREFERRED_SAMPLE_RATE;
    private int eventLength;
    private long lastPublishMs;
    private long audioClockBaseFrame = -1;
    private long audioClockBaseNs = -1;
    private volatile double effectiveSampleRate = PREFERRED_SAMPLE_RATE;

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
            worker = new Thread(this::recordLoop, "WatchTimegrapher");
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
        int[] sources = {MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.MIC};
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
            if (now - lastPublishMs >= 220) {
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
            if (df < activeSampleRate * 4L || dn <= 0) return;
            double measured = df / (dn / 1_000_000_000.0);
            // 비정상 timestamp는 무시합니다. 정상 48k 계열 오디오는 이 범위 안에 들어옵니다.
            if (measured > activeSampleRate * 0.96 && measured < activeSampleRate * 1.04) effectiveSampleRate = measured;
        } catch (Exception ignored) {}
    }

    private void process(short[] input, int count) {
        double dt = 1.0 / activeSampleRate;
        double rc = 1.0 / (2.0 * Math.PI * HP_CUTOFF_HZ);
        double alpha = rc / (rc + dt);

        for (int i = 0; i < count; i++, globalSample++) {
            double x = input[i] / 32768.0;
            double hp = alpha * (prevHp + x - prevX);
            prevX = x;
            prevHp = hp;

            double abs = Math.abs(hp);
            envelope = 0.82 * envelope + 0.18 * abs;

            // 조용한 구간에서 noise floor를 천천히 추종합니다.
            if (!inEvent || envelope < noiseFloor * 2.5) {
                double k = envelope < noiseFloor ? 0.004 : 0.00035;
                noiseFloor = (1.0 - k) * noiseFloor + k * envelope;
                noiseFloor = clamp(noiseFloor, 0.00018, 0.06);
            }
            double threshold = Math.max(0.0016, noiseFloor * 5.2);
            long sinceLast = globalSample - lastBeatSample;

            if (!inEvent) {
                if (envelope > threshold && sinceLast > (long) (activeSampleRate * 0.052)) {
                    beginEvent(envelope);
                }
            } else {
                int pos = eventLength;
                if (pos < eventEnvelope.length) eventEnvelope[pos] = envelope;
                eventLength++;
                if (envelope > eventPeakValue) {
                    eventPeakValue = envelope;
                    eventPeakSample = globalSample;
                }
                if (envelope < threshold * 0.60) eventQuietSamples++; else eventQuietSamples = 0;
                long eventSamples = globalSample - eventStartSample;
                boolean quietEnded = eventSamples > activeSampleRate * 0.0025 && eventQuietSamples > activeSampleRate * 0.0018;
                boolean timeout = eventSamples > activeSampleRate * 0.030;
                if (quietEnded || timeout) finishEvent();
            }
        }
    }

    private void beginEvent(double value) {
        inEvent = true;
        eventStartSample = globalSample;
        eventPeakSample = globalSample;
        eventPeakValue = value;
        eventQuietSamples = 0;
        eventLength = 0;
        if (eventEnvelope.length > 0) eventEnvelope[0] = value;
    }

    private void finishEvent() {
        inEvent = false;
        if (eventPeakValue < Math.max(0.0020, noiseFloor * 4.8)) return;
        double time = eventPeakSample / (double) activeSampleRate;
        double lift = estimateLiftTime(Math.min(eventLength, eventEnvelope.length), eventStartSample);
        lastBeatSample = eventPeakSample;
        synchronized (beats) {
            beats.add(new Beat(time, lift));
            if (beats.size() > MAX_BEATS) beats.remove(0);
        }
    }

    /** 한 impulse cluster 안의 첫/마지막 유효 sub-peak 간격을 lift time 후보로 사용합니다. */
    private double estimateLiftTime(int len, long startSample) {
        if (len < 8) return Double.NaN;
        double max = 0;
        for (int i = 0; i < len; i++) max = Math.max(max, eventEnvelope[i]);
        double cut = Math.max(noiseFloor * 4.0, max * 0.27);
        int minSep = (int) (activeSampleRate * 0.0009);
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
            if (p - first <= maxSpan) chosen = p;
            else break;
        }
        if (chosen < 0) return Double.NaN;
        double seconds = (chosen - first) / (double) activeSampleRate;
        if (seconds < 0.0020 || seconds > 0.0155) return Double.NaN;
        return seconds;
    }

    private Snapshot analyze(boolean currentlyRunning) {
        List<Beat> copy;
        synchronized (beats) { copy = new ArrayList<>(beats); }
        double elapsed = startElapsedMs == 0 ? 0 : (SystemClock.elapsedRealtime() - startElapsedMs) / 1000.0;
        if (copy.size() < 7) {
            return new Snapshot(currentlyRunning, 0, Double.NaN, Double.NaN, Double.NaN,
                    Math.min(25, copy.size() * 3.0), copy.size(), elapsed,
                    Collections.emptyList(), copy.isEmpty() ? "틱 소리를 기다리는 중" : "BPH 분석 중");
        }

        int candidate = detectBph(copy);
        if (candidate == 0) {
            return new Snapshot(currentlyRunning, 0, Double.NaN, Double.NaN, Double.NaN,
                    Math.min(32, copy.size() * 1.6), copy.size(), elapsed,
                    Collections.emptyList(), "일반 BPH 후보를 비교하는 중");
        }

        Fit fit = robustFit(copy, candidate);
        if (fit == null || fit.accepted.size() < 10) {
            return new Snapshot(currentlyRunning, candidate, Double.NaN, Double.NaN, Double.NaN,
                    25, copy.size(), elapsed, Collections.emptyList(), "신호 안정화 중");
        }

        double amplitude = estimateAmplitude(fit.accepted, fit.slope, liftAngleDeg);
        String status;
        if (fit.quality >= 75) status = "측정 안정적";
        else if (fit.quality >= 50) status = "측정 중 · 신호 양호";
        else status = "신호가 약합니다 · 크라운을 마이크 가까이에 두세요";

        return new Snapshot(currentlyRunning, candidate, fit.rate, fit.beatErrorMs, amplitude,
                fit.quality, fit.accepted.size(), elapsed, fit.trace, status);
    }

    private int detectBph(List<Beat> source) {
        int from = Math.max(1, source.size() - 90);
        int best = 0;
        double bestScore = -1e9;
        for (int candidate : COMMON_BPH) {
            double t = 3600.0 / candidate;
            int good = 0;
            int direct = 0;
            double penalty = 0;
            for (int i = from; i < source.size(); i++) {
                double d = source.get(i).timeSec - source.get(i - 1).timeSec;
                int step = (int) Math.round(d / t);
                if (step < 1 || step > 4) { penalty += 0.8; continue; }
                double err = Math.abs(d / step - t) / t;
                if (err <= 0.105) {
                    good++;
                    if (step == 1) direct++;
                    penalty += err;
                } else penalty += Math.min(0.8, err * 2.0);
            }
            double score = good * 4.0 + direct * 1.3 - penalty * 2.2;
            if (score > bestScore) { bestScore = score; best = candidate; }
        }
        int intervalCount = source.size() - from;
        if (intervalCount < 5 || bestScore < intervalCount * 1.25) return 0;
        return best;
    }

    private Fit robustFit(List<Beat> source, int bph) {
        double ideal = 3600.0 / bph;
        double clockScale = activeSampleRate / effectiveSampleRate;
        int start = Math.max(0, source.size() - 420);
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
            if (step < 1 || step > 5) continue;
            double err = Math.abs(d - step * ideal);
            if (err > ideal * 0.16) continue;
            beatIndex += step;
            accepted.add(cur);
            indexes.add((double) beatIndex);
            prev = cur;
        }
        if (accepted.size() < 9) return null;

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

        double expected = Math.max(1.0, ((accepted.get(n - 1).timeSec - accepted.get(0).timeSec) * clockScale) / ideal + 1.0);
        double captureRatio = Math.min(1.0, n / expected);
        double stability = 1.0 - Math.min(1.0, f.rmsMs / 1.6);
        double durationFactor = Math.min(1.0, n / 80.0);
        f.quality = clamp((captureRatio * 55.0 + stability * 30.0 + durationFactor * 15.0), 0, 100);
        return f;
    }

    private double estimateAmplitude(List<Beat> accepted, double beatInterval, double liftAngle) {
        List<Double> amps = new ArrayList<>();
        double omega = Math.PI / beatInterval; // full balance angular frequency
        double liftRad = Math.toRadians(liftAngle);
        int from = Math.max(0, accepted.size() - 80);
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
        // 중앙 60% 평균: 잘못 잡힌 sub-peak의 영향을 줄입니다.
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
            for (int r = col + 1; r < 3; r++) if (Math.abs(m[r][col]) > Math.abs(m[pivot][col])) pivot = r;
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
        prevX = 0; prevHp = 0; envelope = 0; noiseFloor = 0.0012;
        globalSample = 0; lastBeatSample = Long.MIN_VALUE / 4;
        inEvent = false; eventLength = 0; eventQuietSamples = 0;
        startElapsedMs = 0; lastPublishMs = 0;
        audioClockBaseFrame = -1; audioClockBaseNs = -1; effectiveSampleRate = activeSampleRate;
        latest = idleSnapshot("측정 준비");
    }

    private Snapshot idleSnapshot(String status) {
        return new Snapshot(false, 0, Double.NaN, Double.NaN, Double.NaN,
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

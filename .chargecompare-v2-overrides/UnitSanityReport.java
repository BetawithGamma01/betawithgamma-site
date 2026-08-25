package com.oai.chargecompare;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Evidence-based inference of the vendor current-property scale.
 *
 * The Android contract says CURRENT_NOW is microamperes. Some OEM kernels expose
 * different magnitudes. This analyzer does not overwrite the raw values; it tests
 * candidate scales against independent counters and sufficiently large SoC change.
 */
public final class UnitSanityReport {
    public final boolean resolved;
    public final String confidence;
    public final String scaleLabel;
    public final double ampPerRawUnit;
    public final int chargingSign;
    public final double integratedMah;
    public final double integratedWh;
    public final double chargeCounterDeltaMah;
    public final double energyCounterDeltaWh;
    public final int socDeltaPercent;
    public final double nominalCapacityMah;
    public final double candidateMargin;
    public final String evidence;

    private UnitSanityReport(boolean resolved, String confidence, String scaleLabel,
                             double ampPerRawUnit, int chargingSign,
                             double integratedMah, double integratedWh,
                             double chargeCounterDeltaMah, double energyCounterDeltaWh,
                             int socDeltaPercent, double nominalCapacityMah,
                             double candidateMargin, String evidence) {
        this.resolved = resolved;
        this.confidence = confidence;
        this.scaleLabel = scaleLabel;
        this.ampPerRawUnit = ampPerRawUnit;
        this.chargingSign = chargingSign;
        this.integratedMah = integratedMah;
        this.integratedWh = integratedWh;
        this.chargeCounterDeltaMah = chargeCounterDeltaMah;
        this.energyCounterDeltaWh = energyCounterDeltaWh;
        this.socDeltaPercent = socDeltaPercent;
        this.nominalCapacityMah = nominalCapacityMah;
        this.candidateMargin = candidateMargin;
        this.evidence = evidence;
    }

    private static final class Candidate implements Comparable<Candidate> {
        final String label;
        final double factor;
        final double mah;
        final double wh;
        final double avgA;
        final double score;
        Candidate(String label, double factor, double mah, double wh, double avgA, double score) {
            this.label=label; this.factor=factor; this.mah=mah; this.wh=wh; this.avgA=avgA; this.score=score;
        }
        @Override public int compareTo(Candidate o) { return Double.compare(score, o.score); }
    }

    public static UnitSanityReport analyze(TestSession s) {
        if (s == null || s.schemaVersion < 2 || s.samples.size() < 2) {
            return unresolved(s, "No machine-truth v2 session data.");
        }

        List<Integer> currents = new ArrayList<>();
        for (Sample x : s.samples) if (x.rawCurrentNow != Integer.MIN_VALUE && x.rawCurrentNow != 0) currents.add(x.rawCurrentNow);
        if (currents.size() < 2) return unresolved(s, "CURRENT_NOW is unavailable or zero for this session.");
        Collections.sort(currents);
        int medianRaw = currents.get(currents.size()/2);
        int chargingSign = medianRaw >= 0 ? 1 : -1;

        double counterDelta = positiveCounterDeltaMah(s);
        double energyDelta = positiveEnergyDeltaWh(s);
        int socDelta = s.endPercent() >= 0 && s.startPercent() >= 0 ? s.endPercent() - s.startPercent() : 0;
        double cap = s.nominalCapacityMah > 0 ? s.nominalCapacityMah : 7400.0;

        boolean counterRef = Double.isFinite(counterDelta) && counterDelta >= 3.0;
        boolean energyRef = Double.isFinite(energyDelta) && energyDelta >= 0.01;
        boolean socRef = socDelta >= 2;
        int independentRefs = (counterRef?1:0) + (energyRef?1:0) + (socRef?1:0);

        String[] labels = {"Android API µA/raw", "10 µA/raw", "100 µA/raw", "OEM mA/raw"};
        double[] factors = {1e-6, 1e-5, 1e-4, 1e-3};
        List<Candidate> candidates = new ArrayList<>();
        for (int i=0;i<factors.length;i++) {
            double[] q = integrate(s, factors[i], chargingSign);
            double mah=q[0], wh=q[1], avgA=q[2];
            double score=0;
            if (counterRef) score += 4.0 * logRatioError(mah, counterDelta);
            if (energyRef) score += 4.0 * logRatioError(wh, energyDelta);
            if (socRef) {
                double low = Math.max(0.25, socDelta - 1.0) * cap / 100.0;
                double high = (socDelta + 1.0) * cap / 100.0;
                score += 2.0 * bandLogError(mah, low, high);
            }
            // Weak physical sanity only; independent counters/SoC dominate the score.
            if (!(avgA > 0.005 && avgA < 25.0)) score += 0.35;
            double avgV = s.averageVoltageV();
            double avgW = Double.isFinite(avgV) ? avgA * avgV : Double.NaN;
            if (Double.isFinite(avgW) && !(avgW > 0.02 && avgW < 200.0)) score += 0.35;
            candidates.add(new Candidate(labels[i], factors[i], mah, wh, avgA, score));
        }
        Collections.sort(candidates);
        Candidate best = candidates.get(0);
        Candidate second = candidates.get(1);
        double margin = second.score - best.score;

        boolean resolved = independentRefs > 0 && margin >= 0.55;
        String confidence;
        if (!resolved) confidence = "Unresolved";
        else if ((counterRef || energyRef) && margin >= 1.20) confidence = "High";
        else if ((counterRef || energyRef) && margin >= 0.55) confidence = "Medium";
        else if (socDelta >= 4 && margin >= 1.0) confidence = "Medium";
        else confidence = "Low";

        if ("Low".equals(confidence) && !counterRef && !energyRef && socDelta < 3) resolved = false;
        if (!resolved) confidence = "Unresolved";

        String evidence = buildEvidence(best, chargingSign, counterDelta, energyDelta, socDelta, cap, margin,
                counterRef, energyRef, socRef, resolved);
        return new UnitSanityReport(resolved, confidence, best.label, best.factor, chargingSign,
                best.mah, best.wh, counterDelta, energyDelta, socDelta, cap, margin, evidence);
    }

    private static UnitSanityReport unresolved(TestSession s, String why) {
        double cap = s != null && s.nominalCapacityMah > 0 ? s.nominalCapacityMah : 7400.0;
        return new UnitSanityReport(false, "Unresolved", "No validated scale", Double.NaN, 1,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, cap, 0, why);
    }

    private static double[] integrate(TestSession s, double factor, int sign) {
        double mah=0, wh=0, timeH=0;
        for (int i=1;i<s.samples.size();i++) {
            Sample a=s.samples.get(i-1), b=s.samples.get(i);
            if (a.rawCurrentNow==Integer.MIN_VALUE || b.rawCurrentNow==Integer.MIN_VALUE) continue;
            double h=(b.elapsedMs-a.elapsedMs)/3_600_000.0;
            if (!(h>0)) continue;
            double ia=a.rawCurrentNow * factor * sign;
            double ib=b.rawCurrentNow * factor * sign;
            double avgCurrentA=(ia+ib)/2.0;
            mah += avgCurrentA*1000.0*h;
            double va=a.voltageV(), vb=b.voltageV();
            if (Double.isFinite(va) && Double.isFinite(vb)) wh += ((ia*va)+(ib*vb))/2.0*h;
            timeH += h;
        }
        double avgA = timeH>0 ? (mah/1000.0)/timeH : Double.NaN;
        return new double[]{mah,wh,avgA};
    }

    private static double positiveCounterDeltaMah(TestSession s) {
        Sample first=null,last=null;
        for (Sample x:s.samples) if (x.rawChargeCounter!=Integer.MIN_VALUE) { first=x; break; }
        for (int i=s.samples.size()-1;i>=0;i--) { Sample x=s.samples.get(i); if(x.rawChargeCounter!=Integer.MIN_VALUE){last=x;break;} }
        if (first==null || last==null || first==last) return Double.NaN;
        double d=(last.rawChargeCounter-first.rawChargeCounter)/1000.0;
        return d>0 ? d : Double.NaN;
    }

    private static double positiveEnergyDeltaWh(TestSession s) {
        Sample first=null,last=null;
        for (Sample x:s.samples) if (x.rawEnergyCounter!=Long.MIN_VALUE) { first=x; break; }
        for (int i=s.samples.size()-1;i>=0;i--) { Sample x=s.samples.get(i); if(x.rawEnergyCounter!=Long.MIN_VALUE){last=x;break;} }
        if (first==null || last==null || first==last) return Double.NaN;
        double d=(last.rawEnergyCounter-first.rawEnergyCounter)/1_000_000_000.0;
        return d>0 ? d : Double.NaN;
    }

    private static double logRatioError(double x, double ref) {
        if (!(x>0) || !(ref>0)) return 6.0;
        return Math.abs(Math.log10(x/ref));
    }

    private static double bandLogError(double x, double low, double high) {
        if (!(x>0)) return 6.0;
        if (x>=low && x<=high) return 0;
        return x<low ? Math.abs(Math.log10(x/low)) : Math.abs(Math.log10(x/high));
    }

    private static String buildEvidence(Candidate best, int sign, double counter, double energy,
                                        int socDelta, double cap, double margin,
                                        boolean counterRef, boolean energyRef, boolean socRef,
                                        boolean resolved) {
        StringBuilder b=new StringBuilder();
        if (resolved) b.append("Selected ").append(best.label).append("; ");
        else b.append("Provisional best candidate ").append(best.label).append("; scale NOT validated. ");
        b.append("charging-sign normalization ").append(sign>0?"+":"−").append("; ");
        if (counterRef) b.append("charge-counter Δ ").append(f(counter)).append(" mAh; ");
        else b.append("charge-counter no usable Δ; ");
        if (energyRef) b.append("energy-counter Δ ").append(f(energy)).append(" Wh; ");
        else b.append("energy-counter no usable Δ; ");
        b.append("SoC Δ ").append(socDelta).append("% at nominal ").append(Math.round(cap)).append(" mAh; ");
        b.append("candidate separation ").append(f(margin)).append(" score units. ");
        if (!resolved) b.append("For defensible scaling, run until SoC changes ≥2–3% or obtain a usable charge/energy-counter delta.");
        return b.toString();
    }

    public double interpretedCurrentA(Sample x) {
        if (!resolved || x.rawCurrentNow==Integer.MIN_VALUE) return Double.NaN;
        return x.rawCurrentNow * ampPerRawUnit * chargingSign;
    }

    public double interpretedPowerW(Sample x) {
        double a=interpretedCurrentA(x), v=x.voltageV();
        return Double.isFinite(a)&&Double.isFinite(v) ? a*v : Double.NaN;
    }

    private static String f(double v) { return Double.isFinite(v) ? String.format(Locale.US,"%.3f",v) : "n/a"; }
}

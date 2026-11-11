package org.roketsim.core.propulsion;
import java.util.List;
/** Claimed source reference is retained; it does not certify independent verification. */
public record MotorInertiaCurveModel(List<MotorInertiaSample> samples,String sourceReference) implements MotorInertiaModel {
    public MotorInertiaCurveModel {
        MotorInertiaEvaluator.source(sourceReference);
        MotorInertiaEvaluator.required(samples,"samples");
        if(samples.size()<2)throw new IllegalArgumentException("samples must contain at least two entries");
        for(int i=0;i<samples.size();i++) {
            MotorInertiaEvaluator.required(samples.get(i),"samples["+i+"]");
            if(i==0 && samples.get(i).motorTimeS()!=0)throw new IllegalArgumentException("samples must start at motor time zero");
            if(i>0 && samples.get(i).motorTimeS()<=samples.get(i-1).motorTimeS())
                throw new IllegalArgumentException("samples times must be strictly increasing");
        }
        samples=List.copyOf(samples);
    }
}

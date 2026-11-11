import org.roketsim.core.aerodynamics.AerodynamicEvaluationError;
import org.roketsim.core.aerodynamics.RecoveryDragEvaluator3DOF;
import org.roketsim.core.math.Vector;

/** Independent packaged-module consumer: no tests, Python, or direct Core source on the classpath. */
public final class RecoveryDragJarConsumer {
    public static void main(String[] args) {
        var evaluator=new RecoveryDragEvaluator3DOF();
        var flow=Vector.of(new double[]{3,4,0});
        var actual=evaluator.evaluate(2,flow,3);
        if(!actual.equals(Vector.of(new double[]{-45,-60,0})))
            throw new AssertionError("Incorrect packaged recovery force: "+actual);
        if(!evaluator.evaluate(0,flow,3).equals(Vector.of(new double[]{0,0,0})))
            throw new AssertionError("Zero density failed");
        try {
            evaluator.evaluate(2,flow,0);
            throw new AssertionError("Invalid CdA was accepted");
        } catch(AerodynamicEvaluationError error) {
            if(!error.errorCode().equals("INVALID_RECOVERY_DRAG_AREA")
                    || !error.fieldName().equals("effective_drag_area_m2") || !error.value().equals(0.))
                throw new AssertionError("Incorrect error metadata",error);
        }
        System.out.println("forceWorldN="+actual);
        System.out.println("RECOVERY_DRAG_JAR_CONSUMER: PASS");
    }
}

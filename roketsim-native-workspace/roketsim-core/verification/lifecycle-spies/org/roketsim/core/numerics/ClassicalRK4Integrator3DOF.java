package org.roketsim.core.numerics;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Test-only forwarding spy: runs ORIGINAL compiled RK4 bytecode, not copied equations. */
public final class ClassicalRK4Integrator3DOF {
    public static final List<FixedStepConfig> configurations=new ArrayList<>();
    public ClassicalRK4Integrator3DOF() { configurations.clear(); }
    public IntegrationPoint3DOF step(IntegrationPoint3DOF point,FixedStepConfig config,DerivativeFunction3DOF derivative) {
        configurations.add(config);
        String target=getClass().getName();
        try(var loader=new URLClassLoader(new java.net.URL[]{Path.of(System.getProperty("roketsim.verification.realClasses")).toUri().toURL()},getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                if(!name.equals(target))return super.loadClass(name,resolve);
                Class<?> type=findLoadedClass(name);if(type==null)type=findClass(name);
                if(resolve)resolveClass(type);return type;
            }
        }) {
            var type=loader.loadClass(target);
            try {
                return (IntegrationPoint3DOF)type.getMethod("step",IntegrationPoint3DOF.class,FixedStepConfig.class,DerivativeFunction3DOF.class)
                    .invoke(type.getConstructor().newInstance(),point,config,derivative);
            } catch(InvocationTargetException error) {
                if(error.getCause() instanceof RuntimeException cause)throw cause;
                if(error.getCause() instanceof Error cause)throw cause;
                throw new AssertionError(error.getCause());
            }
        } catch(ReflectiveOperationException|java.io.IOException error) { throw new AssertionError(error); }
    }
}

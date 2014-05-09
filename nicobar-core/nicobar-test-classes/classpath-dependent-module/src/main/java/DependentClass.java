import org.jboss.modules.Module;

public class DependentClass {
    /**
     *
     * Try to access a class from the application classpath
     * This will fail if app import filters block it, and pass
     * if it is allowed.
     *
     */
    public DependentClass() {
        // We know Module is in the classpath of the test
        Module.forClass(Object.class);
    }
}
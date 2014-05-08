import org.jboss.modules.Module;

public class DependentClass {
    public DependentClass() {
        Module.forClass(Object.class);
    }
}
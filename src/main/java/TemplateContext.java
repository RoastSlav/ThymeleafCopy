import java.util.HashMap;
import java.util.Map;

public class TemplateContext {
    private Map<String, Object> context = new HashMap<>();

    public void put(String name, Object value) {
        context.put(name, value);
    }

    public Object get(String name) {
        return context.get(name);
    }
}

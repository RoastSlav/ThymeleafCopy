import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Template {
    private static final Pattern VALUE_PATTERN = Pattern.compile("\\$\\{((\\w+\\.?\\(?\\)?)+)}");
    private Document template;

    public Template(String templatePath) throws IOException {
        template = Jsoup.parse(new File(templatePath));
    }

    public void render(TemplateContext ctx, PrintWriter out) {
        template.select("[t\\:each]").forEach(element -> {
            String each = element.attr("t:each");
            String[] parts = each.split(":");

            String collectionName = VALUE_PATTERN.matcher(parts[1]).group(1);
            String itemName = parts[0].trim();


            Collection<?> collection = null;
            try {
                collection = extractCollection(ctx, VALUE_PATTERN.matcher(collectionName));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            Iterable<?> iterable = collection;
            for (Object item : iterable) {
                out.print(item.toString());
            }
        });

        template.select("[t\\:if]").forEach(element -> {
            String ifAttr = element.attr("th:if");
            Object value = ctx.get(ifAttr);
            if (value instanceof Boolean) {
                boolean condition = (boolean) value;
                if (condition) {
                    out.print(element.outerHtml());
                }
            }
        });

        template.select("[t\\:text]").forEach(element -> {
            String textAttr = element.attr("th:text");
            Matcher matcher = VALUE_PATTERN.matcher(textAttr);
            if (!matcher.matches())
                throw new IllegalArgumentException("Invalid th:text attribute: " + textAttr);

            textAttr = matcher.group(1);
            Object value = ctx.get(textAttr);
            if (value == null)
                throw new IllegalStateException("No value for " + textAttr);
            element.text(value.toString());
        });
    }

    private Collection<?> extractCollection(TemplateContext ctx, Matcher matcher) throws NoSuchMethodException, ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        String group = matcher.group(1);
        String[] split = group.split("\\.");

        Object result = ctx.get(split[0]);
        if (result == null)
            throw new IllegalStateException("No value for " + split[0]);

        for (String part : split) {
            if (part.endsWith(")")) {
                String methodName = part;
                Method method = result.getClass().getMethod(methodName);
                try {
                    result = method.invoke(result);
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot invoke method " + methodName + " on " + result);
                }
                continue;
            }

            result = getFieldValue(result, part);
        }

        if (result instanceof Collection) {
            return (Collection<?>) result;
        } else
            throw new IllegalStateException("Cannot iterate over " + result);
    }
    private Object getFieldValue(Object instance, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Class<?> clazz = instance.getClass();
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(instance);
    }
}

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Template {
    private static final Pattern TEXT_SPLIT_PATTERN = Pattern.compile("\\$\\{(\\w+)(?:\\.(\\w+))?}");
    private static final Pattern FOREACH_SPLIT_PATTERN = Pattern.compile("\\s*(\\w+):\\s*\\$\\{(\\w+)(?:\\.(\\w+))?}");
    private static final String TEXT_ATTRIBUTE = "t:text";
    private static final String FOREACH_ATTRIBUTE = "t:each";
    private static final String IF_ATTRIBUTE = "t:if";
    Document template;

    public Template(String path) throws IOException {
        template = Jsoup.parse(Path.of(path).toFile());
    }

    public void render(TemplateContext context, PrintWriter printWriter) {
        Node root = template;
        processElement(root, context, printWriter);
        printWriter.flush();
    }

    private void processElement(Node node, TemplateContext context, PrintWriter printWriter) {
        String ifAttr = node.attr(IF_ATTRIBUTE);
        if (!ifAttr.isEmpty() && !isConditionTrue(ifAttr, context))
            return;

        String forEachAttr = node.attr(FOREACH_ATTRIBUTE);
        if (forEachAttr.isEmpty())
            renderNode(node, context, printWriter);
        else
            processForEachAttribute(node, context, printWriter, forEachAttr);
    }

    private void printOpeningTag(Node node, PrintWriter printWriter) {
        printWriter.print("<" + node.nodeName());
        for (Attribute attribute : node.attributes()) {
            String name = attribute.getKey();
            if (name.equals(IF_ATTRIBUTE) || name.equals(FOREACH_ATTRIBUTE) || name.equals(TEXT_ATTRIBUTE))
                continue;

            String value = attribute.getValue();
            printWriter.print(" " + name + "=" + value);
        }
        printWriter.println(">");
    }

    private void printClosingTag(Node node, PrintWriter out) {
        out.print("</"+node.nodeName()+">");
    }

    private void processForEachAttribute(Node node, TemplateContext context, PrintWriter printWriter, String forEachAttr) {
        Matcher matcher = FOREACH_SPLIT_PATTERN.matcher(forEachAttr);
        if (!matcher.matches())
            throw new IllegalArgumentException("Invalid attribute value: " + forEachAttr);

        Collection<Object> collection = getCollection(context, matcher);
        String variableName = matcher.group(1);
        Object oldValue = context.get(variableName);
        for (Object o : collection) {
            context.put(variableName, o);
            renderNode(node, context, printWriter);
        }

        if (oldValue == null)
            context.remove(variableName);
        else
            context.put(variableName, oldValue);
    }

    private void renderNode(Node node, TemplateContext context, PrintWriter printWriter) {
        printOpeningTag(node, printWriter);
        String textAttr = node.attr(TEXT_ATTRIBUTE);
        if (!textAttr.isEmpty()) {
            String textValue = getPropertyValue(context, textAttr).toString();
            printWriter.println(textValue);
            printClosingTag(node, printWriter);
            return;
        }

        for (Node childNode : node.childNodes()) {
            if (childNode instanceof Element) {
                processElement(childNode, context, printWriter);
                continue;
            }

            String str = childNode.toString();
            if (str.isBlank())
                continue;
            printWriter.println(str.trim());
        }
        printClosingTag(node, printWriter);
    }

    private boolean isConditionTrue(String attributeValue, TemplateContext context) {
        if (attributeValue.equalsIgnoreCase("false"))
            return false;
        if (attributeValue.equalsIgnoreCase("true"))
            return true;

        Object valueObject = getPropertyValue(context, attributeValue);
        if (valueObject == null)
            return false;

        if (valueObject instanceof String value)
            return value.equalsIgnoreCase("true");

        if (valueObject instanceof Boolean value)
            return value;

        if (valueObject instanceof Number value)
            return value.doubleValue() > 0;

        return true;
    }

    private Object getPropertyValue(TemplateContext context, String value) {
        Matcher matcher = TEXT_SPLIT_PATTERN.matcher(value);
        if (!matcher.matches())
            return value;

        String key = matcher.group(1);
        Object valueObject = context.get(key);
        if (valueObject == null)
            throw new IllegalStateException("No property with name in context: " + key);

        for (int i = 2; i <= matcher.groupCount(); i++) {
            if (matcher.group(i) == null)
                break;

            try {
                valueObject = getFieldValue(valueObject, matcher.group(i));
            } catch (Exception e) {
                throw new IllegalStateException("No field with name: " + matcher.group(i) + " in object: " + valueObject);
            }
        }

        return valueObject;
    }

    private Object getFieldValue(Object instance, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Class<?> clazz = instance.getClass();
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(instance);
    }

    private Collection<Object> getCollection(TemplateContext context, Matcher matcher) {
        String key = matcher.group(2);
        Object valueObject = context.get(key);
        if (valueObject == null)
            throw new IllegalStateException("No property with name: " + key);

        int grpCount = matcher.groupCount();
        for (int i = 2; i < grpCount; i++) {
            String grp = matcher.group(i + 1);
            if (grp == null)
                continue;

            try {
                valueObject = getFieldValue(valueObject, grp);
            } catch (Exception e) {
                throw new IllegalStateException("No field with name: " + grp + " in object: " + valueObject);
            }
        }

        if (valueObject.getClass().isArray())
            return Arrays.asList((Object[]) valueObject);

        if (Collection.class.isAssignableFrom(valueObject.getClass()))
            return (Collection<Object>) valueObject;

        throw new IllegalArgumentException("Object is not a collection or an array: " + valueObject);
    }
}

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;

public class TestThymeleaf {
    @Test
    void test() throws IOException, NoSuchFieldException, IllegalAccessException {
        TemplateContext ctx = new TemplateContext();
        WelcomeMessage welcome = new WelcomeMessage("hello world");
        ctx.put("welcome", welcome);

        Student[] students = {
                new Student(1, "Ivan"),
                new Student(2, "Maria"),
                new Student(3, "Nikola")
        };
        ctx.put("students", students);

        Template t = new Template("src/test/resources/template.tm");
        PrintWriter out = new PrintWriter(System.out);
        t.render(ctx, out);
    }
}

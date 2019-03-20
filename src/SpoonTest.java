import AST.Node;
import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtVariableReference;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Scanner;

public class SpoonTest {
    public static void main(String[] args) throws FileNotFoundException {
        generateAllNodePrograms("1.java", "main");

    }

    public static void generateAllNodePrograms(String fileName, String methodName) throws FileNotFoundException {
        Scanner scan = new Scanner(new File(fileName));
        scan.useDelimiter("\\Z");
        String content = scan.next();
        scan.close();

        CtClass<?> clazz = Launcher.parseClass(content);
        CtBlock<?> block = clazz.getMethodsByName(methodName).get(0).getBody();
    }

    class MyException extends Exception{

    }
}

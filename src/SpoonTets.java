import AST.Node;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;

import java.util.HashMap;

public class SpoonTets {
    public static void main(String[] args) {
//        CtClass<?> l = Launcher.parseClass("class A { int foo;void m(int k) { int[] x;t*=6;String uu=\"\";x=Salam.boogh(c,x[0],k,uu.length,this.foo,x.lengths,jj.getIt());} }");
//        CtClass<?> l = Launcher.parseClass("class Clazz { int salam;void m(int k) { if(tt)salam=2+3.5;} }");
//        CtClass<?> l = Launcher.parseClass("class Clazz { int salam;void m(int k) { for(i=0,j=0;i<10;i=i+1)salam=2+3.5;} }");
//        CtClass<?> l = Launcher.parseClass("class Clazz { void m(int k) { A a=null;int y=2;int s;s=5;} }");
        CtClass<?> l = Launcher.parseClass("class Clazz { static{int x=2;} void m(int k) { x=func(this.a,3);} }");

        var map = new HashMap<CtElement, Node>();
    }
}

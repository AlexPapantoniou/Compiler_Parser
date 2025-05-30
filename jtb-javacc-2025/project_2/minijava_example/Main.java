import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import syntaxtree.*;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <inputFile>");
            System.exit(1);
        }

        for (int i = 0; i < args.length; i++) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(args[i]);
                MiniJavaParser parser = new MiniJavaParser(fis);

                Goal root = parser.Goal();

                System.err.println("Program parsed successfully.");

                MyVisitor my_vis = new MyVisitor();

                SymbolTable st = new SymbolTable();
                root.accept(my_vis, st);
                TypeChecker typechecker = new TypeChecker();
                root.accept(typechecker, st);
                st.print_offsets();

            } catch (ParseException ex) {
                System.out.println(ex.getMessage());
            } catch (FileNotFoundException ex) {
                System.err.println(ex.getMessage());
            } finally {
                try {
                    if (fis != null)
                        fis.close();
                } catch (IOException ex) {
                    System.err.println(ex.getMessage());
                }
            }
        }
    }
}
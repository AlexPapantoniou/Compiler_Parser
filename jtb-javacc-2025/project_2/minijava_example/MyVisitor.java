import java.util.List;

import syntaxtree.*;
import visitor.*;

class MyVisitor extends GJDepthFirst<String, SymbolTable> {

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> "public"
     * f4 -> "static"
     * f5 -> "void"
     * f6 -> "main"
     * f7 -> "("
     * f8 -> "String"
     * f9 -> "["
     * f10 -> "]"
     * f11 -> Identifier()
     * f12 -> ")"
     * f13 -> "{"
     * f14 -> ( VarDeclaration() )*
     * f15 -> ( Statement() )*
     * f16 -> "}"
     * f17 -> "}"
     */
    @Override
    public String visit(MainClass n, SymbolTable st) throws Exception {
        String class_name = n.f1.accept(this, st);
        st.declare_class(class_name, null);

        String param_name = n.f11.accept(this, st);
        st.declare_method(class_name, "main", "void",
                List.of(new SymbolTable.Param(param_name, "String", SymbolTable.Kind.ARRAY, 0)));
        st.enter_method_scope(class_name, "main");
        super.visit(n, st);
        st.exit_method_scope();

        System.out.println();

        return null;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> ( VarDeclaration() )*
     * f4 -> ( MethodDeclaration() )*
     * f5 -> "}"
     */
    @Override
    public String visit(ClassDeclaration n, SymbolTable st) throws Exception {
        n.f0.accept(this, st);

        String class_name = n.f1.accept(this, st);
        System.out.println("Class: " + class_name);

        n.f2.accept(this, st);
        // System.out.println("Fields: ");
        n.f3.accept(this, st);
        System.out.println("Methods: ");
        n.f4.accept(this, st);
        n.f5.accept(this, st);

        System.out.println();

        return null;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "extends"
     * f3 -> Identifier()
     * f4 -> "{"
     * f5 -> ( VarDeclaration() )*
     * f6 -> ( MethodDeclaration() )*
     * f7 -> "}"
     */
    @Override
    public String visit(ClassExtendsDeclaration n, SymbolTable st) throws Exception {
        n.f0.accept(this, st);

        String class_name = n.f1.accept(this, null);
        n.f2.accept(this, st);
        String super_class = n.f3.accept(this, null);
        System.out.println("Class: " + class_name + " extends: " + super_class);

        n.f4.accept(this, st);
        System.out.println("Fields: ");
        n.f5.accept(this, st);
        System.out.println("Methods: ");
        n.f6.accept(this, st);
        n.f7.accept(this, st);

        System.out.println();

        return null;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    public String visit(VarDeclaration n, SymbolTable st) throws Exception {
        String _ret = null;
        String type = n.f0.accept(this, st);
        String var = n.f1.accept(this, st);
        st.declare_var(var, type);
        super.visit(n, st);

        return _ret;
    }

    /**
     * 
     * f0 -> "{"
     * f1 -> ( Statement() )*
     * f2 -> "}"
     */
    @Override
    public String visit(Block n, SymbolTable st) {
        st.enter_scope();
        try {
            n.f1.accept(this, st);
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
        }
        st.exit_scope();
        return null;
    }

    /**
     * f0 -> "if"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     * f5 -> "else"
     * f6 -> Statement()
     */
    @Override
    public String visit(IfStatement n, SymbolTable st) throws Exception {
        n.f2.accept(this, st); // evaluate condition
        st.enter_scope();
        n.f4.accept(this, st); // if block
        st.exit_scope();
        st.enter_scope();
        n.f6.accept(this, st); // else block
        st.exit_scope();
        return null;
    }

    /**
     * f0 -> "while"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     */
    @Override
    public String visit(WhileStatement n, SymbolTable st) throws Exception {
        n.f2.accept(this, st); // evaluate condition
        st.enter_scope();
        n.f4.accept(this, st); // while block
        st.exit_scope();
        return null;
    }

    /**
     * f0 -> Identifier()
     * f1 -> "="
     * f2 -> Expression()
     * f3 -> ";"
     */
    @Override
    public String visit(AssignmentStatement n, SymbolTable st) throws Exception {
        String var = n.f0.accept(this, st);
        // Check if variable exists in symbol table
        SymbolTable.Symbol sym = st.lookup(var);
        if (sym == null) {
            throw new Exception("Variable " + var + " not declared");
        }
        return null;
    }

    /**
     * f0 -> Identifier()
     * f1 -> "["
     * f2 -> Expression()
     * f3 -> "]"
     * f4 -> "="
     * f5 -> Expression()
     * f6 -> ";"
     */
    @Override
    public String visit(ArrayAssignmentStatement n, SymbolTable st) throws Exception {
        String array = n.f0.accept(this, st);
        // Check if array exists in symbol table
        SymbolTable.Symbol sym = st.lookup(array);
        if (sym == null) {
            throw new Exception("Array " + array + " not declared");
        }
        if (!sym.type.equals("int[]") && !sym.type.equals("boolean[]")) {
            throw new Exception(array + " is not an array");
        }
        return null;
    }

    /**
     * f0 -> "System.out.println"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> ";"
     */
    @Override
    public String visit(PrintStatement n, SymbolTable st) throws Exception {
        return n.f2.accept(this, st);
    }

    /**
     * f0 -> "public"
     * f1 -> Type()
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( FormalParameterList() )?
     * f5 -> ")"
     * f6 -> "{"
     * f7 -> ( VarDeclaration() )*
     * f8 -> ( Statement() )*
     * f9 -> "return"
     * f10 -> Expression()
     * f11 -> ";"
     * f12 -> "}"
     */
    @Override
    public String visit(MethodDeclaration n, SymbolTable st) throws Exception {
        String argumentList = n.f4.present() ? n.f4.accept(this, null) : "";

        String myType = n.f1.accept(this, null);
        String myName = n.f2.accept(this, null);

        System.out.println("Method: " + myType + " " + myName + " (" + argumentList + ")");
        System.out.println("Local vars:");

        super.visit(n, st);
        return null;
    }

    /**
     * f0 -> FormalParameter()
     * f1 -> FormalParameterTail()
     */
    @Override
    public String visit(FormalParameterList n, SymbolTable st) throws Exception {
        String ret = n.f0.accept(this, null);

        if (n.f1 != null) {
            ret += n.f1.accept(this, null);
        }

        return ret;
    }

    /**
     * f0 -> FormalParameter()
     * f1 -> FormalParameterTail()
     */
    public String visit(FormalParameterTerm n, SymbolTable st) throws Exception {
        return n.f1.accept(this, st);
    }

    /**
     * f0 -> ","
     * f1 -> FormalParameter()
     */
    @Override
    public String visit(FormalParameterTail n, SymbolTable st) throws Exception {
        String ret = "";
        for (Node node : n.f0.nodes) {
            ret += ", " + node.accept(this, null);
        }

        return ret;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     */
    @Override
    public String visit(FormalParameter n, SymbolTable st) throws Exception {
        String type = n.f0.accept(this, null);
        String name = n.f1.accept(this, null);
        return type + " " + name;
    }

    @Override
    public String visit(ArrayType n, SymbolTable st) {
        return "int[]";
    }

    public String visit(BooleanType n, SymbolTable st) {
        return "boolean";
    }

    public String visit(IntegerType n, SymbolTable st) {
        return "int";
    }

    @Override
    public String visit(Identifier n, SymbolTable st) {
        return n.f0.toString();
    }
}
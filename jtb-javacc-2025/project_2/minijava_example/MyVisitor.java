import java.util.ArrayList;
import java.util.List;

import syntaxtree.*;
import visitor.*;

class MyVisitor extends GJDepthFirst<String, SymbolTable> {

    private int field_offset;
    private int method_offset;

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
        st.declare_class(class_name, null, 0, 0);
        st.enter_class_scope(class_name);

        field_offset = 0;
        method_offset = 0;
        String param_name = n.f11.accept(this, st);
        st.declare_method("main", "void",
                List.of(new SymbolTable.Param(param_name, "String[]", SymbolTable.Kind.ARRAY)), method_offset);
        method_offset += 8;
        SymbolTable.ClassSymbol main_class = st.get_class(class_name);
        main_class.max_method_offset = method_offset;

        st.enter_method_scope("main");
        super.visit(n, st);
        st.exit_method_scope();

        st.exit_class_scope();

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
        st.declare_class(class_name, null, 0, 0);

        n.f2.accept(this, st);
        st.enter_class_scope(class_name);

        field_offset = 0;
        method_offset = 0;
        n.f3.accept(this, st);
        n.f4.accept(this, st);

        st.exit_class_scope();

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
        String class_name = n.f1.accept(this, null);
        String super_class_name = n.f3.accept(this, null);
        SymbolTable.ClassSymbol super_class = st.get_class(super_class_name);
        field_offset = super_class.max_field_offset;
        method_offset = super_class.max_method_offset;
        st.declare_class(class_name, super_class_name, field_offset, method_offset);
        st.enter_class_scope(class_name);

        n.f5.accept(this, st);
        n.f6.accept(this, st);

        st.exit_class_scope();

        return null;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    public String visit(VarDeclaration n, SymbolTable st) throws Exception {
        String type = n.f0.accept(this, st);
        String var = n.f1.accept(this, st);
        if (st.current_method == null) {
            if (!st.declare_field(var, type, field_offset)) {
                throw new Exception("Identifier '" + var + "' already defined.");
            }
            switch (type) {
                case "int":
                    field_offset += 4;
                    break;

                case "boolean":
                    field_offset += 1;
                    break;

                case "int[]":
                    field_offset += 8;
                    break;

                case "boolean[]":
                    field_offset += 8;
                    break;

                default:
                    field_offset += 8;
                    break;
            }
            SymbolTable.ClassSymbol curent_class = st.get_class(st.current_class);
            curent_class.max_field_offset = field_offset;
        } else {
            if (!st.declare_var(var, type)) {
                throw new Exception("Identifier '" + var + "' already defined.");
            }
        }

        return null;
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
        String my_type = n.f1.accept(this, null);
        String my_name = n.f2.accept(this, null);

        String argument_list = n.f4.present() ? n.f4.accept(this, null) : "";
        if (argument_list == "") {
            st.declare_method(my_name, my_type, List.of(), method_offset);
            return null;
        }
        String[] argument_list_split = argument_list.split(",");
        List<SymbolTable.Param> params = new ArrayList<>();
        for (String argument : argument_list_split) {
            String[] parts = argument.trim().split("\\s+");
            if (parts.length == 2) {
                params.add(new SymbolTable.Param(parts[1], parts[0],
                        parts[0].endsWith("[]") ? SymbolTable.Kind.ARRAY : SymbolTable.Kind.VARIABLE));
            }
        }

        if (!st.declare_method(my_name, my_type, params, method_offset)) {
            throw new Exception("Method " + my_name + " already declared in this scope");
        }
        method_offset += 8;
        SymbolTable.ClassSymbol current_class = st.get_class(st.current_class);
        current_class.max_method_offset = method_offset;
        st.enter_method_scope(my_name);
        n.f7.accept(this, st);
        st.exit_method_scope();

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
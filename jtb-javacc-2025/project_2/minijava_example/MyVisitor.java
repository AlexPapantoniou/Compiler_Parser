import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import syntaxtree.*;
import visitor.*;

class MyVisitor extends GJDepthFirst<String, Void> {

    public class SymbolTable {

        public enum Kind {
            VARIABLE,
            METHOD
        }

        public static class Param {
            public final String name;
            public final String type;

            public Param(String name, String type) {
                this.name = name;
                this.type = type;
            }

            @Override
            public String toString() {
                return type + " " + name;
            }
        }

        public static class Symbol {
            public final String name;
            public final Kind kind;
            public final String type; // for variables: type; for functions: return type
            public final List<Param> params; // only for functions
            public Object value; // for debugging
            public final int scope_level;

            public Symbol(String name, Kind kind, String type, List<Param> params, Object value, int scope_level) {
                this.name = name;
                this.kind = kind;
                this.type = type;
                this.params = params;
                this.value = value;
                this.scope_level = scope_level;
            }

            @Override
            public String toString() {
                if (kind == Kind.METHOD) {
                    return String.format("Function{name='%s', return_type='%s', params=%s, scope=%d}",
                            name, type, params, scope_level);
                } else {
                    return String.format("Variable{name='%s', type='%s', value=%s, scope=%d}",
                            name, type, value, scope_level);
                }
            }
        }

        // Global scope
        private final Map<String, Symbol> global_scope = new LinkedHashMap<>();

        // Function-scoped symbol tables
        private Map<String, List<Map<String, Symbol>>> function_scopes = new LinkedHashMap<>();

        // State tracking
        private String current_function = null;
        private int current_scope = -1;

        public void add_method(String name, String return_type, List<Param> params) {
            if (global_scope.containsKey(name)) {
                throw new IllegalArgumentException("Function already declared: " + name);
            }

            Symbol method = new Symbol(name, Kind.METHOD, return_type, params, null, 0);
            global_scope.put(name, method);
            function_scopes.put(name, new ArrayList<>());
        }

        public void set_current_method(String name) {
            if (!function_scopes.containsKey(name)) {
                throw new IllegalArgumentException("Function not found: " + name);
            }
            current_function = name;
            current_scope = 0;
        }

        public void exit_method() {
            current_function = null;
            current_scope = -1;
        }

        public void enter_scope() {
            if (current_function == null) {
                throw new IllegalStateException("Must set function context before entering scope.");
            }
            function_scopes.get(current_function).add(new LinkedHashMap<>());
            current_scope = function_scopes.get(current_function).size() - 1;
        }

        public void exit_scope() {
            if (current_function == null || current_scope < 0) {
                throw new IllegalStateException("No scope to exit.");
            }
            current_scope--;
        }

        public boolean add_variable(String name, String type) {
            if (current_function == null) {
                // Global variable
                if (global_scope.containsKey(name)) {
                    return false;
                }
                global_scope.put(name, new Symbol(name, Kind.VARIABLE, type, null, null, 0));
                return true;
            } else {
                List<Map<String, Symbol>> scopes = function_scopes.get(current_function);
                if (scopes.isEmpty()) {
                    enter_scope(); // auto-enter if user forgot to
                }
                Map<String, Symbol> scope = scopes.get(current_scope);
                if (scope.containsKey(name)) {
                    return false;
                }
                scope.put(name, new Symbol(name, Kind.VARIABLE, type, null, null, current_scope));
                return true;
            }
        }

        public boolean assign(String name, Object value) {
            if (current_function != null) {
                List<Map<String, Symbol>> scopes = function_scopes.get(current_function);
                for (int i = current_scope; i >= 0; i--) {
                    Symbol s = scopes.get(i).get(name);
                    if (s != null && s.kind == Kind.VARIABLE) {
                        s.value = value;
                        return true;
                    }
                }
            }
            Symbol global = global_scope.get(name);
            if (global != null && global.kind == Kind.VARIABLE) {
                global.value = value;
                return true;
            }
            return false;
        }

        public Symbol lookup(String name) {
            return lookup(name, current_function);
        }

        public Symbol lookup(String name, String function_context) {
            if (function_context != null && function_scopes.containsKey(function_context)) {
                List<Map<String, Symbol>> scopes = function_scopes.get(function_context);
                for (int i = scopes.size() - 1; i >= 0; i--) {
                    Symbol sym = scopes.get(i).get(name);
                    if (sym != null) {
                        return sym;
                    }
                }
            }
            return global_scope.get(name);
        }

        public void printAll() {
            System.out.println("Function/Scope: global");
            System.out.println("  Scope 0:");
            for (Symbol sym : global_scope.values()) {
                System.out.println("    " + sym);
            }

            for (var entry : function_scopes.entrySet()) {
                String func = entry.getKey();
                System.out.println("Function/Scope: " + func);
                List<Map<String, Symbol>> scopes = entry.getValue();
                for (int i = 0; i < scopes.size(); i++) {
                    System.out.println("  Scope " + i + ":");
                    for (Symbol sym : scopes.get(i).values()) {
                        System.out.println("    " + sym);
                    }
                }
            }
        }
    }

    Map<String, SymbolTable> classes = new LinkedHashMap<>();

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
    public String visit(MainClass n, Void argu) throws Exception {
        String classname = n.f1.accept(this, null);
        System.out.println("Class: " + classname);

        super.visit(n, argu);

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
    public String visit(ClassDeclaration n, Void argu) throws Exception {
        n.f0.accept(this, argu);

        String classname = n.f1.accept(this, argu);
        System.out.println("Class: " + classname);

        n.f2.accept(this, argu);
        System.out.println("Fields: ");
        n.f3.accept(this, argu);
        System.out.println("Methods: ");
        n.f4.accept(this, argu);
        n.f5.accept(this, argu);

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
    public String visit(ClassExtendsDeclaration n, Void argu) throws Exception {
        n.f0.accept(this, argu);

        String classname = n.f1.accept(this, null);
        System.out.println("Class: " + classname);

        n.f2.accept(this, argu);
        n.f3.accept(this, argu);
        n.f4.accept(this, argu);
        System.out.println("Fields: ");
        n.f5.accept(this, argu);
        System.out.println("Methods: ");
        n.f6.accept(this, argu);
        n.f7.accept(this, argu);

        System.out.println();

        return null;
    }

    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    public String visit(VarDeclaration n, Void argu) throws Exception {
        String _ret = null;
        String type = n.f0.accept(this, argu);
        String var = n.f1.accept(this, argu);
        System.out.println(var + " " + type);
        super.visit(n, argu);

        return _ret;
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
    public String visit(MethodDeclaration n, Void argu) throws Exception {
        String argumentList = n.f4.present() ? n.f4.accept(this, null) : "";

        String myType = n.f1.accept(this, null);
        String myName = n.f2.accept(this, null);

        System.out.println("Method: " + myType + " " + myName + " (" + argumentList + ")");
        System.out.println("Local vars:");

        super.visit(n, argu);
        return null;
    }

    /**
     * f0 -> FormalParameter()
     * f1 -> FormalParameterTail()
     */
    @Override
    public String visit(FormalParameterList n, Void argu) throws Exception {
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
    public String visit(FormalParameterTerm n, Void argu) throws Exception {
        return n.f1.accept(this, argu);
    }

    /**
     * f0 -> ","
     * f1 -> FormalParameter()
     */
    @Override
    public String visit(FormalParameterTail n, Void argu) throws Exception {
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
    public String visit(FormalParameter n, Void argu) throws Exception {
        String type = n.f0.accept(this, null);
        String name = n.f1.accept(this, null);
        return type + " " + name;
    }

    @Override
    public String visit(ArrayType n, Void argu) {
        return "int[]";
    }

    public String visit(BooleanType n, Void argu) {
        return "boolean";
    }

    public String visit(IntegerType n, Void argu) {
        return "int";
    }

    @Override
    public String visit(Identifier n, Void argu) {
        return n.f0.toString();
    }
}
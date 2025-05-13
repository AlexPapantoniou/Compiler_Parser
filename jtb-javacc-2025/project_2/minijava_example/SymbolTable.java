import java.util.*;

public class SymbolTable {

    public enum Kind {
        Variable,
        Method
    }

    public static class Param {
        public final String name;
        public final String type;

        public Param(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    public static class Symbol {
        public String name;
        public String type;
        public Kind kind;
        public int scope_level;
        public List<Param> method_params;

        public Symbol(String name, String type, Kind kind, int scope_level, List<Param> method_params) {
            this.name = name;
            this.type = type;
            this.kind = kind;
            this.scope_level = scope_level;
            this.method_params = method_params;
        }
    }

    private LinkedHashMap<String, Symbol> methods;
    private ArrayList<LinkedHashMap<String, Symbol>> variables_in_scope;
    private int current_scope;

    public SymbolTable() {
        methods = new LinkedHashMap<>();
        variables_in_scope = new ArrayList<>();
        enter_scope();
    }

    public void enter_scope() {
        variables_in_scope.add(new LinkedHashMap<>());
        current_scope = variables_in_scope.size() - 1;
    }

    public boolean add_variable(String name, String type) {
        int size = variables_in_scope.size() - 1;
        Symbol variable = variables_in_scope.get(size).get(name);
        if (variable == null) {
            variables_in_scope.get(size).put(name, new Symbol(name, type, Kind.Variable, current_scope, null));
            return true;
        } else if (variable.scope_level != current_scope) {
            variables_in_scope.get(size).put(name, new Symbol(name, type, Kind.Variable, current_scope, null));
            return true;
        } else {
            return false;
        }
    }

    public boolean add_method(String name, String type, List<Param> method_params) {
        Symbol method = methods.get(name);
        if (method == null) {
            methods.put(name, new Symbol(name, type, Kind.Method, current_scope, method_params));
            return true;
        } else if (method.scope_level != current_scope &&
                (method.type == type && method.method_params.equals(method_params))) {

            methods.put(name, new Symbol(name, type, Kind.Method, current_scope, method_params));
            return true;
        } else {
            return false;
        }
    }

    public Symbol lookup_variable(String name, int scope) {
        return variables_in_scope.get(scope).get(name);
    }

    public Symbol lookup_method(String name, int scope) {
        return methods.get(name);
    }

    public void exit_scope() {
        if (this.current_scope > 0) {
            current_scope--;
        } else {
            throw new IllegalStateException("Can't exit global scope.");
        }
    }

}

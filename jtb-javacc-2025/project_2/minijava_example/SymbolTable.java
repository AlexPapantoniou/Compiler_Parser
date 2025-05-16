import java.util.*;

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
    private final Map<String, List<Map<String, Symbol>>> function_scopes = new LinkedHashMap<>();

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

    public static void main(String[] args) {
        SymbolTable st = new SymbolTable();

        // Declare global variable
        st.add_variable("g", "int");
        st.assign("g", 100);

        // Declare a function
        List<Param> sum_params = List.of(new Param("a", "int"), new Param("b", "int"));
        st.add_method("sum", "int", sum_params);

        // Enter function scope
        st.set_current_method("sum");
        st.enter_scope(); // function-level scope

        // Declare parameter variables
        for (Param param : sum_params) {
            st.add_variable(param.name, param.type);
        }

        // Declare local variable in function scope
        st.add_variable("result", "int");
        st.assign("result", 42);

        // Simulate nested block
        st.enter_scope();
        st.add_variable("temp", "int");
        st.assign("temp", 99);
        st.exit_scope();

        st.exit_scope(); // end function-level scope
        st.exit_method(); // exit function

        // Declare another function
        List<Param> print_params = List.of(new Param("msg", "string"));
        st.add_method("print", "void", print_params);

        st.set_current_method("print");
        st.enter_scope();
        for (Param param : print_params) {
            st.add_variable(param.name, param.type);
        }
        st.add_variable("a", "string");
        st.assign("a", "Hello, world!");
        st.exit_scope();
        st.exit_method();

        // Print all symbols
        System.out.println("\n== Symbol Table ==");
        st.printAll();

        // Lookup test
        System.out.println("\n== Lookups ==");
        System.out.println("Lookup 'g': " + st.lookup("g"));
        System.out.println("Lookup 'a' in 'sum': " + st.lookup("a", "sum"));
        System.out.println("Lookup 'a' in 'print': " + st.lookup("a", "print"));
        System.out.println("Lookup 'temp' in 'sum': " + st.lookup("temp", "sum"));
        System.out.println("Lookup 'notDeclared': " + st.lookup("notDeclared"));
    }
}

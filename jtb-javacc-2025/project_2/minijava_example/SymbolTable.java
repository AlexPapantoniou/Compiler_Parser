import java.util.*;

public class SymbolTable {

    // Enumeration of kinds of symbols we track
    public enum Kind {
        VARIABLE, // local variables in method/block scopes
        ARRAY, // array type variables
        FIELD_VARIABLE, // variable-field of class
        FIELD_ARRAY, // array field of class
        METHOD, // methods inside classes
        CLASS // classes themselves
    }

    // Parameter info for methods: name and type
    public static class Param {
        public final String name; // name of the parameter
        public final String type; // "int" or "boolean"
        public final Kind kind; // VARIABLE or ARRAY

        public Param(String name, String type, Kind kind) {
            this.name = name;
            this.type = type;
            this.kind = kind;
        }

        @Override
        public String toString() {
            return type + " " + name;
        }
    }

    // Symbol representing variables, methods, fields, etc.
    public static class Symbol {
        public final String name; // symbol name
        public final Kind kind; // kind (variable, method, etc)
        public final String type; // type info (e.g. "int", "boolean", "table")
        public final List<Param> params; // method parameters (if kind==METHOD)
        public Object value; // assigned value (for debugging)
        public final int scopeLevel; // scope depth level (0=outermost)
        public Map<String, Symbol> method_locals; // local variables in method scope (only for METHOD)

        public Symbol(String name, Kind kind, String type, List<Param> params, Object value, int scopeLevel) {
            this.name = name;
            this.kind = kind;
            this.type = type;
            this.params = params;
            this.value = value;
            this.scopeLevel = scopeLevel;
            // Initialize method locals map only for methods
            if (kind == Kind.METHOD) {
                this.method_locals = new LinkedHashMap<>();
            }
        }

        @Override
        public String toString() {
            if (kind == Kind.METHOD) {
                return String.format("%s{name='%s', return_type='%s', params=%s, scope=%d, locals=%s}",
                        kind, name, type, params, scopeLevel, method_locals == null ? "null" : method_locals.keySet());
            } else {
                return String.format("%s{name='%s', type='%s', value=%s, scope=%d}",
                        kind, name, type, value, scopeLevel);
            }
        }
    }

    // Represents a class with its fields and methods, plus optional super_class
    public static class ClassSymbol {
        public final String name;
        public final String super_class; // name of super_class or null
        public final Map<String, Symbol> fields = new LinkedHashMap<>();
        public final Map<String, Symbol> methods = new LinkedHashMap<>();

        public ClassSymbol(String name, String super_class) {
            this.name = name;
            this.super_class = super_class;
        }

        @Override
        public String toString() {
            return String.format("Class{name='%s', super='%s', fields=%s, methods=%s}",
                    name, super_class, fields.keySet(), methods.keySet());
        }
    }

    // List of nested scopes representing variables defined in blocks/methods
    private final List<Map<String, Symbol>> scopes = new ArrayList<>();

    // Map of class name to ClassSymbol representing declared classes
    private final Map<String, ClassSymbol> classes = new LinkedHashMap<>();

    // Current index into scopes list
    private int current_scope = -1;

    // Track method scope start index and the current method symbol for locals
    // tracking
    private int method_start_scope = -1;
    public Symbol current_method = null;

    // Track the current class
    private String current_class = null;

    /**
     * Enter a new nested scope (block or method)
     * Adds an empty scope map and updates current_scope
     */
    public void enter_scope() {
        scopes.add(new HashMap<>());
        current_scope = scopes.size() - 1;
    }

    /**
     * Exit the current nested scope (only for inner blocks inside methods)
     * Collects all variables declared in this scope into current method locals.
     * Throws if trying to exit a method scope via this function.
     */
    public void exit_scope() {
        if (method_start_scope != -1 && current_scope <= method_start_scope) {
            throw new IllegalStateException(
                    "Cannot exit method scope using exit_scope(), use exit_method_scope() instead.");
        }
        // Remove current scope from stack and collect its locals into current method
        // locals
        Map<String, Symbol> exiting_scope = scopes.remove(current_scope);
        current_scope--;
        if (current_method != null && exiting_scope != null) {
            current_method.method_locals.putAll(exiting_scope);
        }
    }

    public void enter_class_scope(String class_name) {
        if (!classes.containsKey(class_name)) {
            throw new IllegalArgumentException(
                    "Class " + class_name + " has not been declared");
        }
        current_class = class_name;
    }

    public void exit_class_scope() {
        current_class = null;
    }

    /**
     * Enter a new method scope inside a class.
     * Initializes locals tracking for this method.
     *
     * class_name: the name of the class containing the method
     * method_name: the method name
     */
    public void enter_method_scope(String method_name) {
        enter_scope(); // method body scope
        method_start_scope = current_scope; // mark where method scope started

        // Lookup class and method symbols
        ClassSymbol cls = classes.get(current_class);
        current_method = cls.methods.get(method_name);
        if (current_method == null) {
            throw new IllegalArgumentException(
                    "Method '" + method_name + "' not found in class '" + current_class + "'.");
        }

        // Initialize locals map for this method and set the parameters as local
        // variables
        current_method.method_locals = new LinkedHashMap<>();

        if (!current_method.params.isEmpty()) {
            Map<String, Symbol> scope = scopes.get(current_scope);
            for (Param param : current_method.params) {
                scope.put(param.name,
                        new Symbol(param.name, param.kind, param.type, null, null, current_scope));
            }
        }
    }

    /**
     * Exit the current method scope.
     * Collects all nested block locals and the method's own scope locals into
     * method_locals.
     * Cleans up and resets the method tracking state.
     */
    public void exit_method_scope() {
        if (method_start_scope == -1 || current_method == null) {
            throw new IllegalStateException("Not currently in a method scope.");
        }

        // Collect locals from all nested scopes inside method scope
        while (current_scope > method_start_scope) {
            Map<String, Symbol> nested_scope = scopes.remove(current_scope);
            if (nested_scope != null) {
                current_method.method_locals.putAll(nested_scope);
            }
            current_scope--;
        }

        // Collect locals from the method scope itself
        Map<String, Symbol> method_scope = scopes.remove(current_scope);
        if (method_scope != null) {
            current_method.method_locals.putAll(method_scope);
        }
        current_scope--;

        // Reset method scope tracking
        method_start_scope = -1;
        current_method = null;
    }

    /**
     * Declare a new variable in the current scope.
     * 
     * name: variable name
     * type: variable type
     * returns false if already declared in this scope, true if declared
     * successfully
     */
    public boolean declare_var(String name, String type) {
        if (scopes.isEmpty()) {
            // Enter scope if user forgot to
            enter_scope();
        }
        Map<String, Symbol> scope = scopes.get(current_scope);
        if (scope.containsKey(name)) {
            return false; // redeclaration in same scope not allowed
        }
        Kind kind = (type.endsWith("[]")) ? Kind.ARRAY : Kind.VARIABLE;
        scope.put(name, new Symbol(name, kind, type, null, null, current_scope));
        return true;
    }

    /**
     * Declare a class with optional super_class
     * 
     * name: class name
     * super_class: name of super_class (or null)
     * returns false if class already declared, true if success
     */
    public boolean declare_class(String name, String super_class) {
        if (classes.containsKey(name)) {
            return false;
        }
        classes.put(name, new ClassSymbol(name, super_class));
        return true;
    }

    /**
     * Declare a field in a class
     * 
     * class_name: the class name
     * field_name: field name
     * type: field type
     * returns false if class doesn't exist or field already exists, true if success
     */
    public boolean declare_field(String field_name, String type) {
        ClassSymbol cls = classes.get(current_class);
        if (cls == null || cls.fields.containsKey(field_name)) {
            return false;
        }
        Kind kind = (type.endsWith("[]")) ? Kind.FIELD_ARRAY : Kind.FIELD_VARIABLE;
        cls.fields.put(field_name, new Symbol(field_name, kind, type, null, null, 0));
        return true;
    }

    /**
     * Declare a method in a class
     * 
     * class_name: the class name
     * method_name: method name
     * return_type: return type
     * params: list of parameters
     * returns false if class doesn't exist or method already exists, true if
     * success
     */
    public boolean declare_method(String method_name, String return_type, List<Param> params) {
        ClassSymbol cls = classes.get(current_class);
        if (cls == null || cls.methods.containsKey(method_name)) {
            // Check if class already contains a method with this name
            return false;
        }
        ClassSymbol super_cls = classes.get(cls.super_class);
        if (super_cls != null) {
            Symbol method = super_cls.methods.get(method_name);
            // Check if super class has a method with the same name
            if (method != null && (!method.type.equals(return_type) || !method.params.equals(params))) {
                // No overloading
                return false;
            }
        }
        cls.methods.put(method_name, new Symbol(method_name, Kind.METHOD, return_type, params, null, 0));
        return true;
    }

    /**
     * Assign a value to a variable by name (searching from innermost scope outward)
     * 
     * name: variable name
     * value: value to assign
     * returns true if found and assigned, false if variable not found
     */
    public boolean assign(String name, Object value) {
        for (int i = current_scope; i >= 0; i--) {
            Symbol sym = scopes.get(i).get(name);
            if (sym != null && sym.kind == Kind.VARIABLE) {
                sym.value = value;
                return true;
            }
        }
        return false;
    }

    /**
     * Lookup a variable by name searching from innermost scope outward.
     * Does NOT look inside classes or methods.
     * 
     * name: variable name
     * returns Symbol or null if not found
     */
    public Symbol lookup(String name) {
        for (int i = current_scope; i >= 0; i--) {
            Symbol sym = scopes.get(i).get(name);
            if (sym != null) {
                return sym;
            }
        }
        return null;
    }

    /**
     * Lookup a field in a class or its super_classes.
     * 
     * class_name: starting class name
     * field_name: field to lookup
     * returns Symbol or null if not found
     */
    public Symbol lookup_field(String class_name, String field_name) {
        ClassSymbol cls = classes.get(class_name);
        while (cls != null) {
            Symbol field = cls.fields.get(field_name);
            if (field != null) {
                return field;
            }
            cls = classes.get(cls.super_class);
        }
        return null;
    }

    /**
     * Lookup a method in a class or its super_classes.
     * 
     * class_name: starting class name
     * method_name: method to lookup
     * returns Symbol or null if not found
     */
    public Symbol lookup_method(String class_name, String method_name) {
        ClassSymbol cls = classes.get(class_name);
        while (cls != null) {
            Symbol method = cls.methods.get(method_name);
            if (method != null) {
                return method;
            }
            cls = classes.get(cls.super_class);
        }
        return null;
    }

    /**
     * Print all scopes and classes for debugging
     */
    public void print_all() {
        System.out.println("\n== Classes ==");
        for (ClassSymbol cls : classes.values()) {
            System.out.println(cls);
            for (Symbol method : cls.methods.values()) {
                System.out.println("    " + method);
                if (method.method_locals != null) {
                    System.out.println("      Locals: " + method.method_locals.keySet());
                }
            }
        }
    }

    public static void main(String[] args) {
        SymbolTable st = new SymbolTable();

        // Declare class Main and its main method
        st.declare_class("Main", null);
        st.declare_method("main", "void", List.of());

        // Enter the method scope for main (begin tracking method locals)
        st.enter_method_scope("main");

        // Declare variables in main method scope and nested blocks
        st.declare_var("temp", "int");
        st.assign("temp", 100);

        // if block scope inside main method
        st.enter_scope();
        st.declare_var("cond", "boolean");
        st.assign("cond", true);
        st.exit_scope(); // exit if block, locals merged into main method locals

        // while block scope inside main method
        st.enter_scope();
        st.declare_var("counter", "int");
        st.assign("counter", 0);
        st.exit_scope(); // exit while block, locals merged into main method locals

        // Exit method scope: gather all locals declared in main method
        st.exit_method_scope();

        // Declare classes Animal and Dog with inheritance
        st.declare_class("Animal", null);
        st.declare_field("age", "int[]");
        st.declare_method("speak", "void", List.of(new Param("temp", "boolean", Kind.VARIABLE)));
        st.enter_method_scope("speak");
        // st.declare_var("temp", "int");
        st.exit_method_scope();

        st.declare_class("Dog", "Animal");
        st.declare_field("breed", "string");
        st.declare_method("bark", "void", List.of());
        st.enter_method_scope("bark");
        st.declare_var("temp", "int");
        st.exit_method_scope();

        // Print all scopes and classes with methods and locals
        st.print_all();

        // Lookup inherited method speak on Dog class
        Symbol speak = st.lookup_method("Dog", "speak");
        System.out.println("\nLookup Dog.speak (inherited method): " + speak);
        System.out.println("Lookup local variables in Dog.speak (should be null): " +
                (speak == null ? null : speak.method_locals));

        // Lookup main method in Main class and its locals
        Symbol mainMethod = st.lookup_method("Main", "main");
        System.out.println("\nLookup Main.main method: " + mainMethod);
        System.out.println("Lookup local variable 'temp' in Main.main: " +
                (mainMethod == null ? null : mainMethod.method_locals.get("temp")));
    }
}

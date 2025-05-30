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

    }

    // Symbol representing variables, methods, fields, etc.
    public static class Symbol {
        public final String name; // symbol name
        public final Kind kind; // kind (variable, method, etc)
        public final String type; // type info (e.g. "int", "boolean", "table")
        public final List<Param> params; // method parameters (if kind==METHOD)
        public Map<String, Symbol> method_locals; // local variables in method scope (only for METHOD)
        public int offset;

        public Symbol(String name, Kind kind, String type, List<Param> params, int offset) {
            this.name = name;
            this.kind = kind;
            this.type = type;
            this.params = params;
            this.method_locals = null;
            this.offset = offset;
        }

    }

    // Represents a class with its fields and methods, plus optional super_class
    public static class ClassSymbol {
        public final String name;
        public final String super_class; // name of super_class or null
        public final Map<String, Symbol> fields = new LinkedHashMap<>();
        public final Map<String, Symbol> methods = new LinkedHashMap<>();
        public int max_field_offset;
        public int max_method_offset;

        public ClassSymbol(String name, String super_class, int max_field_offset, int max_method_offset) {
            this.name = name;
            this.super_class = super_class;
            this.max_field_offset = max_field_offset;
            this.max_method_offset = max_method_offset;
        }

    }

    // Map of class name to ClassSymbol representing declared classes
    private final Map<String, ClassSymbol> classes = new LinkedHashMap<>();

    // Track method scope start index and the current method symbol for locals
    // tracking
    public Symbol current_method = null;

    // Track the current class
    public String current_class = null;
    public String main_class = null;

    public void enter_class_scope(String class_name) {
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
            for (Param param : current_method.params) {
                current_method.method_locals.put(param.name,
                        new Symbol(param.name, param.kind, param.type, null, 0));
            }
        }
    }

    /**
     * Exit the current method scope.
     *
     * Cleans up and resets the method tracking state.
     */
    public void exit_method_scope() {
        if (current_method == null) {
            throw new IllegalStateException("Not currently in a method scope.");
        }

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
    public boolean declare_var(String var_name, String type) throws Exception {
        if (current_method == null) {
            throw new Exception("Can't declare variables outside method scopes. Use declare_field instead.");
        }
        if (current_method.method_locals.containsKey(var_name)) {
            return false; // redeclaration in same scope not allowed
        }
        Kind kind = (type.endsWith("[]")) ? Kind.ARRAY : Kind.VARIABLE;
        current_method.method_locals.put(var_name, new Symbol(var_name, kind, type, null, 0));

        return true;
    }

    /**
     * Declare a class with optional super_class
     * 
     * name: class name
     * super_class: name of super_class (or null)
     * returns false if class already declared, true if success
     */
    public boolean declare_class(String class_name, String super_class, int max_field_offset, int max_method_offset) {
        if (classes.containsKey(class_name)) {
            return false;
        }
        classes.put(class_name, new ClassSymbol(class_name, super_class, max_field_offset, max_method_offset));

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
    public boolean declare_field(String field_name, String type, int offset) {
        ClassSymbol cls = classes.get(current_class);
        if (cls == null || cls.fields.containsKey(field_name)) {
            return false;
        }
        Kind kind = (type.endsWith("[]")) ? Kind.FIELD_ARRAY : Kind.FIELD_VARIABLE;
        cls.fields.put(field_name, new Symbol(field_name, kind, type, null, offset));

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
    public boolean declare_method(String method_name, String return_type, List<Param> params, int offset) {
        ClassSymbol cls = classes.get(current_class);
        if (cls == null || cls.methods.containsKey(method_name)) {
            // Check if class already contains a method with this name
            return false;
        }
        ClassSymbol super_cls = classes.get(cls.super_class);
        if (super_cls != null) {
            Symbol super_method = super_cls.methods.get(method_name);
            if (super_method != null) {
                // Method exists in superclass - check signature
                boolean same_return_type = super_method.type.equals(return_type);
                boolean same_params = false;

                // Handle null params case
                if (super_method.params.isEmpty() && params.isEmpty()) {
                    same_params = true;
                } else if (super_method.params != null && params != null) {
                    // Compare parameter lists
                    if (super_method.params.size() == params.size()) {
                        same_params = true;
                        for (int i = 0; i < params.size(); i++) {
                            Param p1 = super_method.params.get(i);
                            Param p2 = params.get(i);
                            if (!p1.type.equals(p2.type) || !p1.name.equals(p2.name)) {
                                same_params = false;
                                break;
                            }
                        }
                    }
                }

                if (!same_return_type || !same_params) {
                    System.err.println("Error: Method " + method_name + " in class " + current_class +
                            " has different signature than superclass method");
                    return false;
                }
            }
        }
        cls.methods.put(method_name, new Symbol(method_name, Kind.METHOD, return_type, params, offset));

        return true;
    }

    public void enter_class_scope_typecheck(String class_name) {
        current_class = class_name;
    }

    public void enter_method_scope_typecheck(String method_name) {
        current_method = lookup_method(current_class, method_name);
    }

    public void exit_class_scope_typecheck() {
        current_class = null;
    }

    public void exit_method_scope_typecheck() {
        current_method = null;
    }

    /**
     * Lookup a variable by name in method scope and class fields.
     * 
     * name: variable name
     * returns Symbol or null if not found
     */
    public Symbol lookup(String name) {
        Symbol sym = current_method.method_locals.get(name);
        if (sym == null) {
            sym = lookup_field(current_class, name);
        }
        return sym;
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
     * Check if a class with the given name exists
     * 
     * name: class name
     * returns true if class exists, false otherwise
     */
    public boolean is_class(String name) {
        return classes.containsKey(name);
    }

    /**
     * Get the ClassSymbol for a class by name
     * 
     * class_name: name of the class
     * returns ClassSymbol or null if not found
     */
    public ClassSymbol get_class(String class_name) {
        return classes.get(class_name);
    }

    /**
     * Print all scopes and classes for debugging
     */
    public void print_offsets() {
        for (ClassSymbol cls : classes.values()) {
            if (cls.name.equals(main_class)) {
                continue;
            }
            System.err.println("--------Class " + cls.name + "--------");
            System.err.println("--------Fields--------");
            for (Symbol field : cls.fields.values()) {
                System.out.println(cls.name + "." + field.name + ": " + field.offset);
            }
            System.err.println("--------Methods--------");
            ClassSymbol super_class = null;
            if (cls.super_class != null) {
                super_class = get_class(cls.super_class);
            }
            for (Symbol method : cls.methods.values()) {
                if (super_class == null || !super_class.methods.containsKey(method.name)) {
                    System.out.println(cls.name + "." + method.name + ": " + method.offset);
                }
            }
        }
    }

}

import syntaxtree.*;
import visitor.*;

public class TypeChecker extends GJDepthFirst<String, SymbolTable> {

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
        if (!st.is_class(class_name)) {
            throw new Exception("Main class '" + class_name + "' is not declared.");
        }
        st.enter_class_scope_typecheck(class_name);
        n.f15.accept(this, st);
        st.exit_class_scope_typecheck();

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
        String class_name = n.f1.accept(this, st);
        if (!st.is_class(class_name)) {
            throw new Exception("Class '" + class_name + "' is not declared.");
        }
        st.enter_class_scope_typecheck(class_name);
        n.f4.accept(this, st);
        st.exit_class_scope_typecheck();

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
        String class_name = n.f1.accept(this, st);
        String super_class = n.f3.accept(this, st);
        if (!st.is_class(class_name)) {
            throw new Exception("Class '" + class_name + "' is not declared.");
        }
        if (!st.is_class(super_class)) {
            throw new Exception("Superclass '" + super_class + "' is not declared.");
        }

        st.enter_class_scope_typecheck(class_name);
        n.f6.accept(this, st);
        st.exit_class_scope_typecheck();

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
        String method_name = n.f2.accept(this, st);
        SymbolTable.Symbol method = st.lookup_method(st.current_class, method_name);
        if (method == null) {
            throw new Exception(
                    "Method '" + method_name + "' has not been declared in class '" + st.current_class + "'.");
        }
        st.enter_method_scope_typecheck(method_name);
        n.f8.accept(this, st);

        String return_type = n.f10.accept(this, st);
        if (!return_type.equals("int") && !return_type.equals("boolean") && !return_type.endsWith("[]")) {
            SymbolTable.Symbol sym = st.lookup(return_type);
            if (sym == null) {
                throw new Exception("Undefined identifier: " + return_type);
            }
            return_type = sym.type;
        }
        if (!method.type.equals(return_type)) {
            throw new Exception("Return type mismatch in method: '" + st.current_class + "." + method_name
                    + "'. Expected " + method.type + ", found " + return_type);
        }

        st.exit_method_scope_typecheck();

        return null;
    }

    /**
     * f0 -> Block()
     * | AssignmentStatement()
     * | ArrayAssignmentStatement()
     * | IfStatement()
     * | WhileStatement()
     * | PrintStatement()
     */
    @Override
    public String visit(Statement n, SymbolTable st) throws Exception {
        n.f0.accept(this, st);

        return null;
    }

    /**
     * f0 -> "{"
     * f1 -> ( Statement() )*
     * f2 -> "}"
     */
    @Override
    public String visit(Block n, SymbolTable st) throws Exception {
        n.f1.accept(this, st);

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
        String left = n.f0.accept(this, st);
        SymbolTable.Symbol left_sym = st.lookup(left);
        if (left_sym == null) {
            throw new Exception("Undefined identifier: " + left);
        }
        String right_type = n.f2.accept(this, st);
        if (!left_sym.type.equals(right_type)) {
            throw new Exception("Type mismatch: " + left_sym.type + " != " + right_type);
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
        String left = n.f0.accept(this, st);
        SymbolTable.Symbol left_sym = st.lookup(left);
        if (left_sym == null) {
            throw new Exception("Undefined identifier: " + left);
        }
        if (!left_sym.type.endsWith("[]")) {
            throw new Exception("Identifier " + left + "'s type is " + left_sym.type);
        }
        String index = n.f2.accept(this, st);
        if (!index.equals("int")) {
            throw new Exception("Expected 'int' expression.");
        }
        String right_type = n.f5.accept(this, st);
        String left_type_stripped = left_sym.type.substring(0, left_sym.type.length() - 2);
        if (!left_type_stripped.equals(right_type)) {
            throw new Exception("Type mismatch: " + left_type_stripped + " != " + right_type);
        }

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
        String condition = n.f2.accept(this, st);
        if (!condition.equals("boolean")) {
            throw new Exception("Expected 'boolean' expression.");
        }
        n.f4.accept(this, st);
        n.f6.accept(this, st);

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
        String condition = n.f2.accept(this, st);
        if (!condition.equals("boolean")) {
            throw new Exception("Expected 'boolean' expression.");
        }
        n.f4.accept(this, st);

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
        String printing = n.f2.accept(this, st);
        if (!printing.equals("int") && !printing.equals("boolean")) {
            throw new Exception("Can't print type: " + printing);
        }
        return null;
    }

    /**
     * f0 -> AndExpression()
     * | CompareExpression()
     * | PlusExpression()
     * | MinusExpression()
     * | TimesExpression()
     * | ArrayLookup()
     * | ArrayLength()
     * | MessageSend()
     * | Clause()
     */
    @Override
    public String visit(Expression n, SymbolTable st) throws Exception {
        return n.f0.accept(this, st);
    }

    /**
     * f0 -> Clause()
     * f1 -> "&&"
     * f2 -> Clause()
     */
    @Override
    public String visit(AndExpression n, SymbolTable st) throws Exception {
        String left_clause = n.f0.accept(this, st);
        String right_clause = n.f2.accept(this, st);
        if (!left_clause.equals("boolean")) {
            SymbolTable.Symbol left_sym = st.lookup(left_clause);
            if (left_sym == null) {
                throw new Exception("Undefined identifier: " + left_clause);
            } else if (!left_sym.type.equals("boolean")) {
                throw new Exception("Expected 'boolean' expression.");
            }
        }
        if (!right_clause.equals("boolean")) {
            SymbolTable.Symbol right_sym = st.lookup(right_clause);
            if (right_sym == null) {
                throw new Exception("Undefined identifier: " + right_clause);
            } else if (!right_sym.type.equals("boolean")) {
                throw new Exception("Expected 'boolean' expression.");
            }
        }
        return "boolean";
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "<"
     * f2 -> PrimaryExpression()
     */
    @Override
    public String visit(CompareExpression n, SymbolTable st) throws Exception {
        String left_prim = n.f0.accept(this, st);
        String right_prim = n.f2.accept(this, st);
        if (!left_prim.equals("int")) {
            SymbolTable.Symbol left_sym = st.lookup(left_prim);
            if (left_sym == null) {
                throw new Exception("Undefined identifier: " + left_prim);
            } else if (!left_sym.type.equals("int")) {
                throw new Exception("Expected 'int' expression.");
            }
        }
        if (!right_prim.equals("int")) {
            SymbolTable.Symbol right_sym = st.lookup(right_prim);
            if (right_sym == null) {
                throw new Exception("Undefined identifier: " + right_prim);
            } else if (!right_sym.type.equals("int")) {
                throw new Exception("Expected 'int' expression.");
            }
        }
        return "boolean";
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "+"
     * f2 -> PrimaryExpression()
     */
    @Override
    public String visit(PlusExpression n, SymbolTable st) throws Exception {
        String left_prim = n.f0.accept(this, st);
        String right_prim = n.f2.accept(this, st);
        if (!left_prim.equals("int")) {
            SymbolTable.Symbol left_sym = st.lookup(left_prim);
            if (left_sym == null) {
                throw new Exception("Undefined identifier: " + left_prim);
            } else if (!left_sym.type.equals("int")) {
                throw new Exception("Expected 'int' expression.");
            }
        }
        if (!right_prim.equals("int")) {
            SymbolTable.Symbol right_sym = st.lookup(right_prim);
            if (right_sym == null) {
                throw new Exception("Undefined identifier: " + right_prim);
            } else if (!right_sym.type.equals("int")) {
                throw new Exception("Expected 'int' expression.");
            }
        }
        return "int";
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "-"
     * f2 -> PrimaryExpression()
     */
    @Override
    public String visit(MinusExpression n, SymbolTable st) throws Exception {
        String left_prim = n.f0.accept(this, st);
        String right_prim = n.f2.accept(this, st);
        if (!left_prim.equals("int")) {
            SymbolTable.Symbol left_sym = st.lookup(left_prim);
            if (left_sym == null) {
                throw new Exception("Undefined identifier: " + left_prim);
            } else if (!left_sym.type.equals("int")) {
                throw new Exception("Expected 'int' expression.");
            }
        }
        if (!right_prim.equals("int")) {
            SymbolTable.Symbol right_sym = st.lookup(right_prim);
            if (right_sym == null) {
                throw new Exception("Undefined identifier: " + right_prim);
            } else if (!right_sym.type.equals("int")) {
                throw new Exception("Expected 'int' expression.");
            }
        }
        return "int";
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "*"
     * f2 -> PrimaryExpression()
     */
    @Override
    public String visit(TimesExpression n, SymbolTable st) throws Exception {
        String left_prim = n.f0.accept(this, st);
        String right_prim = n.f2.accept(this, st);
        if (!left_prim.equals("int")) {
            SymbolTable.Symbol left_sym = st.lookup(left_prim);
            if (left_sym == null) {
                throw new Exception("Undefined identifier: " + left_prim);
            } else if (!left_sym.type.equals("int")) {
                throw new Exception("Expected 'int' expression.");
            }
        }
        if (!right_prim.equals("int")) {
            SymbolTable.Symbol right_sym = st.lookup(right_prim);
            if (right_sym == null) {
                throw new Exception("Undefined identifier: " + right_prim);
            } else if (!right_sym.type.equals("int")) {
                throw new Exception("Expected 'int' expression.");
            }
        }
        return "int";
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "["
     * f2 -> PrimaryExpression()
     * f3 -> "]"
     */
    @Override
    public String visit(ArrayLookup n, SymbolTable st) throws Exception {
        String prim_expr = n.f0.accept(this, st);
        SymbolTable.Symbol sym = st.lookup(prim_expr);
        if (sym == null) {
            throw new Exception("Undefined identifier: " + prim_expr);
        }
        if (!sym.type.endsWith("[]")) {
            throw new Exception(prim_expr + " variable is not an array.");
        }
        String index = n.f2.accept(this, st);
        if (!index.equals("int")) {
            throw new Exception("Expected 'int' expression.");
        }
        return sym.type.substring(0, sym.type.length() - 2);
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> "length"
     */
    @Override
    public String visit(ArrayLength n, SymbolTable st) throws Exception {
        String prim_expr = n.f0.accept(this, st);
        SymbolTable.Symbol sym = st.lookup(prim_expr);
        if (sym == null) {
            throw new Exception("Undefined identifier: " + prim_expr);
        }
        if (!sym.type.endsWith("[]")) {
            throw new Exception(prim_expr + " variable is not an array.");
        }

        return "int";
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( ExpressionList() )?
     * f5 -> ")"
     */
    @Override
    public String visit(MessageSend n, SymbolTable st) throws Exception {
        String prim_expr = n.f0.accept(this, st);
        SymbolTable.ClassSymbol class_sym = prim_expr.equals("this") ? st.get_class(st.current_class)
                : st.get_class(prim_expr);
        if (class_sym == null) {
            throw new Exception("Unknown type: " + prim_expr);
        }
        String method_name = n.f2.accept(this, st);
        SymbolTable.Symbol method = st.lookup_method(class_sym.name, method_name);
        if (method == null) {
            throw new Exception("Class '" + class_sym.name + "' does not contain any field '" + method_name + "'.");
        }
        String argument_list = n.f4.present() ? n.f4.accept(this, null) : "";
        if (argument_list == "") {
            if (!method.params.isEmpty()) {
                throw new Exception(
                        "Too few arguments in funtion call. Expected " + method.params.size() + ", found 0.");
            }

            return method.type;
        }
        String[] argument_list_split = argument_list.split(",");
        int i;
        for (i = 0; i < method.params.size(); i++) {
            SymbolTable.Param param = method.params.get(i);
            String argument = argument_list_split[i];
            if (!param.type.equals(argument)) {
                throw new Exception(
                        "Type mismatch in argument list. Expected '" + param.type + "', found '" + argument + "'.");
            }
        }
        if (i < argument_list_split.length) {
            throw new Exception("Too many arguments in function call. Expected " + method.params.size() + ", found "
                    + argument_list_split.length);
        }

        return method.type;
    }

    /**
     * f0 -> Expression()
     * f1 -> ExpressionTail()
     */
    @Override
    public String visit(ExpressionList n, SymbolTable st) throws Exception {
        String ret = n.f0.accept(this, st);
        if (n.f1 != null) {
            ret += n.f1.accept(this, st);
        }

        return ret;
    }

    /**
     * f0 -> ( ExpressionTerm() )*
     */
    @Override
    public String visit(ExpressionTail n, SymbolTable st) throws Exception {
        String ret = "";
        for (Node node : n.f0.nodes) {
            ret += ", " + node.accept(this, st);
        }
        return ret;
    }

    /**
     * f0 -> ","
     * f1 -> Expression()
     */
    @Override
    public String visit(ExpressionTerm n, SymbolTable st) throws Exception {
        String expr = n.f0.accept(this, st);
        if (!expr.equals("int") && !expr.equals("boolean") && !expr.endsWith("[]")) {
            SymbolTable.Symbol sym = st.lookup(expr);
            if (sym == null) {
                throw new Exception("Undefined identifier: " + expr);
            }
            return sym.type;
        }
        return expr;
    }

    /**
     * f0 -> NotExpression()
     * | PrimaryExpression()
     */
    @Override
    public String visit(Clause n, SymbolTable st) throws Exception {
        return n.f0.accept(this, st);
    }

    /**
     * f0 -> IntegerLiteral()
     * | TrueLiteral()
     * | FalseLiteral()
     * | Identifier()
     * | ThisExpression()
     * | ArrayAllocationExpression()
     * | AllocationExpression()
     * | BracketExpression()
     */
    @Override
    public String visit(PrimaryExpression n, SymbolTable st) throws Exception {
        return n.f0.accept(this, st);
    }

    /**
     * f0 -> <INTEGER_LITERAL>
     */
    @Override
    public String visit(IntegerLiteral n, SymbolTable st) throws Exception {
        return "int";
    }

    /**
     * f0 -> "true"
     */
    @Override
    public String visit(TrueLiteral n, SymbolTable st) throws Exception {
        return "boolean";
    }

    /**
     * f0 -> "false"
     */
    @Override
    public String visit(FalseLiteral n, SymbolTable st) throws Exception {
        return "boolean";
    }

    /**
     * f0 -> "int[]"
     */
    @Override
    public String visit(IntegerArrayType n, SymbolTable st) {
        return "int[]";
    }

    /**
     * f0 -> "boolean[]"
     */
    @Override
    public String visit(BooleanArrayType n, SymbolTable st) {
        return "boolean[]";
    }

    /**
     * f0 -> "boolean"
     */
    public String visit(BooleanType n, SymbolTable st) {
        return "boolean";
    }

    /**
     * f0 -> "int"
     */
    public String visit(IntegerType n, SymbolTable st) {
        return "int";
    }

    @Override
    public String visit(Identifier n, SymbolTable st) {
        return n.f0.toString();
    }

    /**
     * f0 -> "this"
     */
    @Override
    public String visit(ThisExpression n, SymbolTable st) throws Exception {
        return "this";
    }

    /**
     * f0 -> BooleanArrayAllocationExpression()
     * | IntegerArrayAllocationExpression()
     */
    @Override
    public String visit(ArrayAllocationExpression n, SymbolTable st) throws Exception {
        return n.f0.accept(this, st);
    }

    /**
     * f0 -> "new"
     * f1 -> "boolean"
     * f2 -> "["
     * f3 -> Expression()
     * f4 -> "]"
     */
    public String visit(BooleanArrayAllocationExpression n, SymbolTable st) throws Exception {
        String size = n.f3.accept(this, st);
        if (!size.equals("int")) {
            throw new Exception("Expected 'int' expression.");
        }
        return "boolean[]";
    }

    /**
     * f0 -> "new"
     * f1 -> "int"
     * f2 -> "["
     * f3 -> Expression()
     * f4 -> "]"
     */
    public String visit(IntegerArrayAllocationExpression n, SymbolTable st) throws Exception {
        String size = n.f3.accept(this, st);
        if (!size.equals("int")) {
            throw new Exception("Expected 'int' expression.");
        }
        return "int[]";
    }

    /**
     * f0 -> "new"
     * f1 -> Identifier()
     * f2 -> "("
     * f3 -> ")"
     */
    @Override
    public String visit(AllocationExpression n, SymbolTable st) throws Exception {
        String type = n.f0.accept(this, st);
        if (!st.is_class(type)) {
            throw new Exception("Unknown type '" + type + "'.");
        }
        return type;
    }

    /**
     * f0 -> "!"
     * f1 -> Clause()
     */
    @Override
    public String visit(NotExpression n, SymbolTable st) throws Exception {
        return n.f1.accept(this, st);
    }

    /**
     * f0 -> "("
     * f1 -> Expression()
     * f2 -> ")"
     */
    @Override
    public String visit(BracketExpression n, SymbolTable st) throws Exception {
        return n.f1.accept(this, st);
    }

}

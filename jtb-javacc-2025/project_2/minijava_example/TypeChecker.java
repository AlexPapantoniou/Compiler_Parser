import syntaxtree.*;
import visitor.*;

public class TypeChecker extends GJDepthFirst<String, SymbolTable> {

    // @Override
    // public String visit(Goal n, SymbolTable st) {
    // return null;
    // }

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
        st.enter_class_scope(class_name);
        n.f15.accept(this, st);
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
        String class_name = n.f1.accept(this, st);
        if (!st.is_class(class_name)) {
            throw new Exception("Class '" + class_name + "' is not declared.");
        }
        st.enter_class_scope(class_name);
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
        String class_name = n.f1.accept(this, st);
        String super_class = n.f3.accept(this, st);
        if (!st.is_class(class_name)) {
            throw new Exception("Class '" + class_name + "' is not declared.");
        }
        if (!st.is_class(super_class)) {
            throw new Exception("Superclass '" + super_class + "' is not declared.");
        }

        st.enter_class_scope(class_name);
        n.f6.accept(this, st);
        st.exit_class_scope();

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
        st.enter_method_scope(method_name);
        n.f8.accept(this, st);

        String return_type = n.f10.accept(this, st);
        if (!method.type.equals(return_type)) {
            throw new Exception("Return type mismatch in method: '" + st.current_class + "." + method_name
                    + "'. Expected " + method.type + ", found " + return_type);
        }

        st.exit_method_scope();

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
            left_sym = st.lookup_field(st.current_class, left);
            if (left_sym == null) {
                throw new Exception("Undefined identifier: " + left);
            }
        }
        String right_type = n.f2.accept(this, st);
        if (left_sym.type.equals(right_type)) {
            throw new Exception("Type mismatch: " + left_sym.type + " + " + right_type);
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
            left_sym = st.lookup_field(st.current_class, left);
            if (left_sym == null) {
                throw new Exception("Undefined identifier: " + left);
            }
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
        if (left_type_stripped.equals(right_type)) {
            throw new Exception("Type mismatch: " + left_type_stripped + " + " + right_type);
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
        st.enter_scope();
        n.f4.accept(this, st);
        st.exit_scope();
        st.enter_scope();
        n.f6.accept(this, st);
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
        String condition = n.f2.accept(this, st);
        if (!condition.equals("boolean")) {
            throw new Exception("Expected 'boolean' expression.");
        }
        st.enter_scope();
        n.f4.accept(this, st);
        st.exit_scope();

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
        return null;
    }

    /**
     * f0 -> Clause()
     * f1 -> "&&"
     * f2 -> Clause()
     */
    @Override
    public String visit(AndExpression n, SymbolTable st) throws Exception {
        return null;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "<"
     * f2 -> PrimaryExpression()
     */
    @Override
    public String visit(CompareExpression n, SymbolTable st) throws Exception {
        return null;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "+"
     * f2 -> PrimaryExpression()
     */
    @Override
    public String visit(PlusExpression n, SymbolTable st) throws Exception {
        return null;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "-"
     * f2 -> PrimaryExpression()
     */
    @Override
    public String visit(MinusExpression n, SymbolTable st) throws Exception {
        return null;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "*"
     * f2 -> PrimaryExpression()
     */
    @Override
    public String visit(TimesExpression n, SymbolTable st) throws Exception {
        return null;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "["
     * f2 -> PrimaryExpression()
     * f3 -> "]"
     */
    @Override
    public String visit(ArrayLookup n, SymbolTable st) throws Exception {
        return null;
    }

    /**
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> "length"
     */
    @Override
    public String visit(ArrayLength n, SymbolTable st) throws Exception {
        return null;
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
        return null;
    }

    /**
     * f0 -> NotExpression()
     * | PrimaryExpression()
     */
    @Override
    public String visit(Clause n, SymbolTable st) throws Exception {
        return null;
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
        return null;
    }

    @Override
    public String visit(IntegerArrayType n, SymbolTable st) {
        return "int[]";
    }

    @Override
    public String visit(BooleanArrayType n, SymbolTable st) {
        return "boolean[]";
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

    /**
     * f0 -> "this"
     */
    @Override
    public String visit(ThisExpression n, SymbolTable st) throws Exception {
        return null;
    }

    /**
     * f0 -> BooleanArrayAllocationExpression()
     * | IntegerArrayAllocationExpression()
     */
    @Override
    public String visit(ArrayAllocationExpression n, SymbolTable st) throws Exception {
        return null;
    }

    /**
     * f0 -> "new"
     * f1 -> Identifier()
     * f2 -> "("
     * f3 -> ")"
     */
    @Override
    public String visit(AllocationExpression n, SymbolTable st) throws Exception {
        return null;
    }

    /**
     * f0 -> "!"
     * f1 -> Clause()
     */
    @Override
    public String visit(NotExpression n, SymbolTable st) throws Exception {
        return null;
    }

    /**
     * f0 -> "("
     * f1 -> Expression()
     * f2 -> ")"
     */
    @Override
    public String visit(BracketExpression n, SymbolTable st) throws Exception {
        return null;
    }

}

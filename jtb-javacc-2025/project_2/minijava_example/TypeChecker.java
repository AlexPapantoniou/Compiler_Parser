import syntaxtree.AssignmentStatement;
import syntaxtree.MethodDeclaration;
import visitor.GJDepthFirst;

public class TypeChecker extends GJDepthFirst<String, SymbolTable> {

    @Override
    public String visit(AssignmentStatement n, SymbolTable st) throws Exception {
        String var_name = n.f0.accept(this, st);
        String var_type = st.get_var_type(var_name);
        String expr_type = n.f1.accept(this, st);
        if (var_type == null) {
            throw new Exception("Variable '" + var_name + "' is not declared.");
        }
        if (!var_type.equals(expr_type)) {
            throw new Exception("Type mismatch: cannot assign '" + expr_type + "' to '" + var_type + "'.");
        }
        return null;
    }

    @Override
    public String visit(MethodDeclaration n, SymbolTable st) throws Exception {

        return null;
    }
}

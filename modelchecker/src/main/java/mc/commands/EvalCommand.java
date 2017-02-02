package mc.commands;

import mc.solver.ExpressionSolver;
import mc.util.expr.BooleanOperand;
import mc.util.expr.Expression;
import mc.util.expr.IntegerOperand;
import org.fusesource.jansi.Ansi;

import java.util.Collections;
import java.util.Map;

public class EvalCommand implements Command{
    @Override
    public void run(String[] args) {
        Expression expression = Expression.constructExpression(args[0]);
        Map<String,Integer> vars = Collections.emptyMap();
        if (args.length > 1) {
            //TODO: parse a json map of vars
        }
        try {
            System.out.println("Expression evaluated to: "+ eval(ExpressionSolver.simplify(expression, vars)));
        } catch (Exception ex) {
            System.out.println(Ansi.ansi().render("@|red There was an error parsing that expression. |@"));
            System.out.println("Error Message: "+ex.getLocalizedMessage());
        }
    }
    private int eval(Expression ex) {
        if (ex instanceof BooleanOperand) return ((BooleanOperand) ex).getValue()?1:0;
        if (ex instanceof IntegerOperand) return ((IntegerOperand) ex).getValue();
        throw new IllegalStateException("There was an undefined variable in that statement.");
    }
}

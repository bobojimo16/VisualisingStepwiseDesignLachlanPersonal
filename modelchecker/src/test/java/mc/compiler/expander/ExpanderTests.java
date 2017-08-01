package mc.compiler.expander;

import mc.compiler.Expander;
import mc.compiler.Lexer;
import mc.compiler.Parser;
import mc.compiler.TestBase;
import mc.compiler.ast.AbstractSyntaxTree;
import mc.compiler.ast.ProcessNode;
import mc.compiler.token.Token;
import mc.exceptions.CompilationException;
import mc.util.PrintQueue;

import java.util.List;

public class ExpanderTests extends TestBase {

	private final Lexer lexer = new Lexer();
	private final Parser parser = new Parser();
	private final Expander expander = new Expander();

	ProcessNode constructProcessNode(String code) throws InterruptedException {
        try{
            List<Token> tokens = lexer.tokenise(code);
            AbstractSyntaxTree ast = parser.parse(tokens);
            ast = expander.expand(ast,new PrintQueue());
            return ast.getProcesses().get(0);
        }catch(CompilationException e){
            e.printStackTrace();
        }

        return null;
	}

    List<ProcessNode> constructProcessList(String code) throws InterruptedException {
        try{
            List<Token> tokens = lexer.tokenise(code);
            AbstractSyntaxTree ast = parser.parse(tokens);
            return expander.expand(ast,new PrintQueue()).getProcesses();
        }catch(CompilationException e){
            e.printStackTrace();
        }

        return null;
    }
}

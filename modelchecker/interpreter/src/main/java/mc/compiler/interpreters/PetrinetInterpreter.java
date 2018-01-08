package mc.compiler.interpreters;

import com.microsoft.z3.Context;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import mc.compiler.LocalCompiler;
import mc.compiler.ast.ASTNode;
import mc.compiler.ast.IdentifierNode;
import mc.compiler.ast.ProcessNode;
import mc.compiler.ast.ProcessRootNode;
import mc.compiler.ast.VariableSetNode;
import mc.exceptions.CompilationException;
import mc.processmodels.ProcessModel;
import mc.processmodels.automata.Automaton;
import mc.processmodels.petrinet.Petrinet;
import mc.processmodels.petrinet.components.PetriNetPlace;

public class PetrinetInterpreter implements ProcessModelInterpreter {

  Context context;

  Map<String, PetriNetPlace> referenceMap = new HashMap<>();
  Map<String, ProcessModel> processMap = new HashMap<>();
  Stack<Petrinet> processStack = new Stack<>();
  LocalCompiler compiler;
  Set<String> variableList;
  int subProcessCount = 0;
  VariableSetNode variables;

  @Override
  public ProcessModel interpret(ProcessNode processNode, Map<String, ProcessModel> processMap, LocalCompiler localCompiler, Context context) throws CompilationException, InterruptedException {
    reset();
    this.compiler = compiler;
    this.context = context;
    variableList = new HashSet<>();
    this.processMap = processMap;
    String identifier = processNode.getIdentifier();
    this.variables = processNode.getVariables();

    return null;
  }

  @Override
  public ProcessModel interpret(ASTNode astNode, String identifier, Map<String, ProcessModel> processMap, Context context) throws CompilationException, InterruptedException {
    return null;
  }


  private void interpretProcess(ASTNode astNode, String identifier) throws CompilationException, InterruptedException {
    if (astNode instanceof IdentifierNode) {
      String reference = ((IdentifierNode) astNode).getIdentifier();
      if (variables != null) {
        ProcessNode node = (ProcessNode) compiler.getProcessNodeMap().get(reference).copy();
        node.setVariables(variables);
        node = compiler.compile(node, context);
        ProcessModel model = new PetrinetInterpreter().interpret(node, processMap, compiler, context);
        processStack.push((Petrinet) model);
      } else {
        processStack.push((Petrinet) processMap.get(reference));
      }
      return;
    }
    if (astNode instanceof ProcessRootNode) {
      ProcessRootNode root = (ProcessRootNode) astNode;

      interpretProcess(root.getProcess(), identifier);
      Petrinet petrinet = processStack.pop();
      //TODO: Relabel
//      petrinet =
//      if(root.hasHiding())

      processStack.push(petrinet);
      return;
    }
    Petrinet petrinet = new Petrinet(identifier);

    if (variables != null) {
      petrinet.setHiddenVariables(variables.getVariables());
      petrinet.setHiddenVariablesLocation(variables.getLocation());
    }

    petrinet.getVariables().addAll(variableList);
    petrinet.setVariablesLocation(astNode.getLocation());

    //Interpret Node
    //TODO: Interpreting
    processStack.push(petrinet);
  }


  private void interpretASTNode(ASTNode currentNode, Automaton automaton) throws CompilationException, InterruptedException {
    if(Thread.currentThread().isInterrupted())
      throw new InterruptedException();

    if (currentNode.hasReferences()) {
      currentNode.getReferences().forEach(s->referenceMap.put(s,currentNode));
    }
  }

  public void reset() {
    referenceMap.clear();
    processStack.clear();
  }
}
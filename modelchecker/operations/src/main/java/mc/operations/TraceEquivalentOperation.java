package mc.operations;

import java.util.*;

import mc.exceptions.CompilationException;
import mc.operations.functions.NFtoDFconvFunction;
import mc.plugins.IOperationInfixFunction;
import mc.processmodels.ProcessModel;
import mc.processmodels.automata.Automaton;

public class TraceEquivalentOperation implements IOperationInfixFunction {
  /**
   * A method of tracking the function.
   *
   * @return The Human-Readable form of the function name
   */
  @Override
  public String getFunctionName() {
    return "TraceEquivalent";
  }

  /**
   * The form which the function will appear when composed in the text.
   *
   * @return the textual notation of the infix function
   */
  @Override
  public String getNotation() {
    return "=t";
  }
  @Override
  public String getOperationType(){return "automata";}
  /**
   * Evaluate the function.
   *
   * @param processModels automaton in the function (e.g. {@code A} in {@code A ~ B})
   * @return the resulting automaton of the operation
   */
  @Override
  public boolean evaluate(Collection<ProcessModel> processModels) throws CompilationException {
    if (processModels.iterator().next() instanceof Automaton) {
      NFtoDFconvFunction func = new NFtoDFconvFunction();

      ArrayList<ProcessModel> nfas = new ArrayList<>();
      for (ProcessModel pm : processModels) {
        Automaton a = (Automaton) pm;
        try {
          nfas.add(
                  func.compose(a.getId(), new HashSet<>(), null, a)
          );
        } catch (CompilationException e) {
          System.out.println("PINGO" + e.toString());
        }
      }
      BisimulationAutomata bo = new BisimulationAutomata();
      boolean r = bo.evaluate(  nfas);

   /*
    return new BisimulationOperation().evaluate(automata.stream().map(a -> {
      try {
        return func.compose(a.getId(), new HashSet<>(), null, a);
      } catch (CompilationException e) {
        return null;
      }
    }).filter(Objects::nonNull).collect(Collectors.toList()));

*/

      return r;
    }
    System.out.printf("\nTrace semantics not defined for type " + processModels.iterator().next().getClass()+"\n");
    return false;
  }
}

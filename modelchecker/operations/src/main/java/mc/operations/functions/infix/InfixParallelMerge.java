package mc.operations.functions.infix;

import mc.exceptions.CompilationException;
import mc.operations.impl.AutomataParallelMerge;
import mc.plugins.IProcessInfixFunction;
import mc.processmodels.automata.Automaton;
import mc.processmodels.automata.operations.AutomataReachability;
import mc.processmodels.automata.operations.PetrinetParallelMergeFunction;
import mc.processmodels.petrinet.Petrinet;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class InfixParallelMerge implements IProcessInfixFunction {

  public InfixParallelMerge() {
  }

  /**
   * A method of tracking the function.
   *
   * @return The Human-Readable form of the function name
   */
  @Override
  public String getFunctionName() {
    return "ParallelMerge";
  }
  public Collection<String> getValidFlags(){return new HashSet<>();}
  /**
   * The form which the function will appear when composed in the text.
   *
   * @return
   */
  @Override
  public String getNotation() {
    return "|*|";
  }

  /**
   * Execute the function.
   *
   * @param id         the id of the resulting automaton
   * @param automaton1 the first  automaton in the function (e.g. {@code A} in {@code A||B})
   * @param automaton2 the second automaton in the function (e.g. {@code B} in {@code A||B})
   * @return the resulting automaton of the operation
   */
  @Override
  public Automaton compose(String id, Automaton automaton1, Automaton automaton2)
      throws CompilationException {

    //use this secondary function in new object, as without mutable state the function
    // becomes **very** hard to properly compose without a wild mess of lambdas.

    return AutomataReachability.removeUnreachableNodes(new AutomataParallelMerge()
            .execute(id, automaton1, automaton2));
  }

  /**
   * TODO:
   * Execute the function.
   *
   * @param id        the id of the resulting petrinet
   * @param petrinet1 the first  petrinet in the function (e.g. {@code A} in {@code A||B})
   * @param petrinet2 the second petrinet in the function (e.g. {@code B} in {@code A||B})
   * @param flags
   * @return the resulting petrinet of the operation
   */
  @Override
  public Petrinet compose(String id, Petrinet petrinet1, Petrinet petrinet2, Set<String> flags) throws CompilationException {
    return PetrinetParallelMergeFunction.compose(petrinet1,petrinet2);
  }

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof InfixParallelMerge)) return false;
    final InfixParallelMerge other = (InfixParallelMerge) o;
    return true;
  }

  public int hashCode() {
    int result = 1;
    return result;
  }

  public String toString() {
    return "InfixParallelMerge()";
  }
}

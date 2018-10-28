package mc.operations;

import java.util.*;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;
import com.microsoft.z3.Context;
import mc.Constant;
import mc.TraceType;
import mc.exceptions.CompilationException;
import mc.plugins.IOperationInfixFunction;
import mc.processmodels.ProcessModel;
import mc.processmodels.automata.AutomatonNode;

public class TraceRefinement implements IOperationInfixFunction {
  /**
   * A method of tracking the function.
   *
   * @return The Human-Readable form of the function name
   */
  @Override
  public String getFunctionName() {
    return "TraceRefinement";
  }
  @Override
  public Collection<String> getValidFlags(){
    return ImmutableSet.of(Constant.UNFAIR, Constant.FAIR, Constant.CONGURENT);
  }

  /**
   * The form which the function will appear when composed in the text.
   *
   * @return the textual notation of the infix function
   */
  @Override
  public String getNotation() {
    return "<t";
  }
  @Override
  public String getOperationType(){return "automata";}
  /**
   * Evaluate the function.
   *
   * @param alpha
   * @param processModels automaton in the function (e.g. {@code A} in {@code A ~ B})
   * @return the resulting automaton of the operation
   */
  @Override
  public boolean evaluate(Set<String> alpha, Set<String> flags, Context context, Collection<ProcessModel> processModels) throws CompilationException {
    ProcessModel[] pms = processModels.toArray(new ProcessModel[processModels.size()]);
    //System.out.println("TraceRefinement "+ alpha +" "+flags+ " "+ pms[0].getId()+ " "+pms[1].getId());
    TraceWork tw = new TraceWork();
    //Void parameters used elsewhere to build Failure,Singelton Fail, ....
    //SubSetDataConstructor doNothing = (x,y) -> new ArrayList<>();
    //SubSetEval yes = (x,y,z) -> true;
    return tw.evaluate(flags,context, processModels,
      TraceType.CompleteTrace,
      this::readyWrapped,
      this::isReadySubset);
  }

  /*
     function returns the union of the ready sets to be added to the dfa
   */
  public List<Set<String>> readyWrapped(Set<AutomatonNode> nds, boolean cong){

    List<Set<String>> readyWrap = new ArrayList<>();
    Set<String> ready = new TreeSet<>();
    nds.stream().map(x->x.quiescentReadySet(cong)).forEach(s->ready.addAll(s));
    //System.out.println("readyWrapped "+ready);
    readyWrap.add(ready.stream().distinct().collect(Collectors.toSet()));
    //System.out.println("readyWrapped "+readyWrap);

    return readyWrap;
  }

  /*
    function to be applied to the data output from readyWrapped
    returns subset
   */

  public boolean isReadySubset(List<Set<String>> s1,List<Set<String>> s2, boolean cong) {
    boolean out = true;
    if (cong) out =  s2.get(0).containsAll(s1.get(0));
    else {
      for (String lab :s1.get(0)) {
        if (Constant.external(lab)) continue;
        if (!s2.get(0).contains(lab)) {
          out = false;
          break;
        }
      }
    }
    return out;
  }
}


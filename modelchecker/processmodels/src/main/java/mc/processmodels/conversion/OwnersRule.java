package mc.processmodels.conversion;

import java.util.*;
import java.util.stream.Collectors;


import lombok.SneakyThrows;
import mc.exceptions.CompilationException;

import mc.processmodels.automata.Automaton;
import mc.processmodels.automata.AutomatonEdge;
import mc.processmodels.automata.AutomatonNode;
import mc.processmodels.automata.operations.PetrinetParallelFunction;
import mc.processmodels.automata.operations.SequentialInfixFun;
import mc.processmodels.petrinet.Petrinet;
import mc.processmodels.petrinet.components.PetriNetPlace;
import mc.processmodels.petrinet.components.PetriNetTransition;
import mc.processmodels.petrinet.operations.PetrinetReachability;
import mc.util.MyAssert;

//import mc.operations.impl.PetrinetParallelFunction;
public class OwnersRule {

  private static Map<AutomatonNode, Map<String, PetriNetPlace>> aN2Marking = new HashMap<>();
  private static todoStack todo = new todoStack();
  private static Map<String, AutomatonEdge> processed = new HashMap<>(); // No new Peri transition needed

  private static Set<AutomatonNode> ndProcessed;

  private static void clean() {
    aN2Marking = new HashMap<>();
    todo = new todoStack();
    ndProcessed = new HashSet<>();
    processed = new HashMap<>();
  }

  /**
   * This converts an automata to a petrinet.
   * For each owner O1 project the automata to a SLICE automata(Net)
   * i.e. a net built from  edges with owners containing O1
   * <p>
   * Build the final net  as the parallel composition of all the slices!
   * <p>
   * Internal choice introduces multiple start states NOT EASY to code directly
   * So
   * 1. to automata A add initial event S*=>A now only one start state.
   * 2. proceed as before
   * 3. remove S* to reveal the multiple start states.
   * <p>
   * For synchronised broadcast events petri transitions have "optional" listening
   * coresponding automata events  are marked as optional
   * and are ignored in this construction
   *
   * @param ain The automaton to be converted
   * @return a petrinet representation fo the automaton
   */
  @SneakyThrows({CompilationException.class})
  public static Petrinet ownersRule(Automaton ain) {
    System.out.println("OwnersRule initial automata " + ain.getId() + "*START ");
    //Throwable t = new Throwable(); t.printStackTrace();
    clean();
    MyAssert.myAssert(ain.validateAutomaton("Owners Rule input "+ain.getId()+" vlaid = "), "Owners Rule Failure");

    //assert ain.validateAutomaton("Owners Automaton"): "Owners Rule precondition";
    // 1. to automata A add initial event S*=>A now only one start state.
    Automaton star = Automaton.singleEventAutomata("single", "S*");
    SequentialInfixFun sif = new SequentialInfixFun();
    Automaton ai = sif.compose(ain.getId(), star, ain);
    //System.out.println("owners S* "+ai.myString());
    // 2. proceed as before
    Automaton a = ai.copy(); // smaller Ids make debugging easier
    a.tagEvents();
    //System.out.println("\nOWNERS Stared Rule " + a.myString());

    AutomatonNode root = null;

//       There is only one Root node (because of S*)
    root = a.getRoot().iterator().next();

    Stack<Petrinet> subNets = new Stack<>();
/*
   Build,one for each owner,  projection mappings from nodes to a  SLICE
    */
    //System.out.println("Owners START " + a.myString() + "\n");
    for (String own : a.getOwners()) {
      //System.out.println(" >>>>>>>Owner "+ own);
      if (own.equals("_default"))
        throw new CompilationException(ain.getClass(),"Owners Failure in Owners Rule "+ain.myString());
      Petrinet petri = new Petrinet(a.getId(), false);
      petri.setOwners(Collections.singleton(own));
      Stack<AutomatonNode> toDo = new Stack<>();
      Stack<AutomatonNode> processed = new Stack<>();
      toDo.add(root);
      int endcnt = 1;  //for setting endNos on NetPlace  - Net will be set from Place
      //BUILD the nd2Pl Mapp
      Map<AutomatonNode, PetriNetPlace> nd2Pl = new HashMap<>();
      boolean first = true;
      while (!toDo.isEmpty()) {
        AutomatonNode nd = toDo.pop();
    //System.out.println("   >>>>>>>>>OWNStart nd " + nd.myString());
        if (processed.contains(nd)) continue;
        processed.add(nd);
        if (!nd2Pl.containsKey(nd)) {
          PetriNetPlace added = petri.addPlace();
          Set<String> owns = new TreeSet<>();
          owns.add(own);
          added.setOwners(owns);
          if (nd.isStopNode()) {
            added.addEndNo(endcnt++);
          }

          if (first) {
            petri.addRoot(Collections.singleton(added.getId()));
            added.setStart(true);
            added.getStartNos().add(1);
          }
          first = false;
          //System.out.println("PreClump \n"+ain.myString());

          Set<AutomatonNode> clump = reach(ain, nd, own);
          //System.out.println("PostClump \n"+ain.myString());
          //System.out.println("Clump " + clump.stream().map(x -> x.getId() + ", ").collect(Collectors.joining()));
          for (AutomatonNode n : clump) {
            if (!nd2Pl.containsKey(n)) nd2Pl.put(n, added);
            if (n.isTerminal() && n.isSTOP()) {
              added.setTerminal("STOP");
              if (added.getEndNos().size() == 0) added.addEndNo(endcnt++);
            }
            //System.out.println("added "+added.myString());
          }
        }

        Set<AutomatonNode> next = nd.getIncomingEdges().stream().map(ed -> ed.getFrom()).collect(Collectors.toSet());
        next.addAll(nd.getOutgoingEdges().stream().map(ed -> ed.getTo()).collect(Collectors.toSet()));
        toDo.addAll(next);
        //System.out.println("Next " + next.stream().map(x -> x.getId() + ", ").collect(Collectors.joining()));
      }
      //Use the nd2Pl Mapp to build the projected automaton
      toDo.clear();
      processed.clear();
      toDo.push(root);
      //System.out.println("Half way "+petri.myString());
      while (!toDo.isEmpty()) {
        AutomatonNode nd = toDo.pop();
        //System.out.println("Start 2 nd " + nd.getId());
        if (processed.contains(nd)) {
          //System.out.println("Skipped");
          continue;
        }
        processed.add(nd);

        for (AutomatonEdge ed : nd.getOutgoingEdges()) {
          //System.out.println("    Start 2 " + ed.myString() + " own " + own);
          // optional send events May not be needed
          if (ed.getOptionalEdge()) continue;
          toDo.push(ed.getTo());
          //System.out.println("    Start 2 " + ed.myString() + " own " + own);

          if (ed.getOwnerLocation().contains(own)) {
      //System.out.println("Own adding " + ed.getId()+" "+ed.getLabel());
            PetriNetTransition tran = petri.addTransition(ed.getLabel());
            petri.addEdge(tran, nd2Pl.get(ed.getFrom()), ed.getOptionalEdge());
            petri.addEdge(nd2Pl.get(ed.getTo()), tran, ed.getOptionalEdge());
            tran.setOwners(Collections.singleton(own));
            //System.out.println("Adding " + tran.myString());
          } else {
            //System.out.println("par edge " + ed.myString());
          }
        }
      }

      //System.out.println("\nSlice Net = " + petri.myString());
      //petri = PetrinetReachability.removeUnreachableStates(petri).copy();
      //System.out.println("\npushing "+petri.myString());
      petri.setEndFromPlace();
      subNets.push(petri);  // Clones
      //System.out.println(" SLICE Net \n"+petri.myString()+ "\n SLICE Net ");

    }
    Petrinet build;
    //System.out.println("\n   OWNERS Rule Stacked "+subNets.size()+"    *********");
    if (subNets.size()>0) {
      build = subNets.pop();
      while (!subNets.isEmpty()) {
        //System.out.println(subNets.size()+" Adding");
        //build = PetrinetParallelMergeFunction.compose(build, subNets.pop());  //Debuging
        build = PetrinetParallelFunction.compose(build, subNets.pop(), new HashSet<String>());
        //  build = subNets.pop();  //for debugging
        //System.out.println("Build " + build.myString());
      }

      build.deTagTransitions();  //use ":" tonot mess up with Galois  (undo Automaton  tagEvents() )


      //System.out.println("  before reach  "+build.myString());
      build = PetrinetReachability.removeUnreachableStates(build);//, false);
      //System.out.println("reach *END "+build.myString());

      //3. remove S* to reveal the multiple start states.
      stripStar(build);
      //System.out.println("  OWNERS Rule *END " + build.myString()+"\n*END");
      //assert(build.validatePNet(): "OwnersRule End");
    } else {
      build = Petrinet.startNet();
    }
    //build.validatePNet();
    MyAssert.myAssert(ain.validateAutomaton("Owners Rule output "+build.getId()+" vlaid = "), "Owners Rule Failure");
    //assert build.validatePNet("Owners Net"): "Owners Rule Failure";
    //System.out.println("Owners end with "+build.getId());
    return build;
  }

  private static Petrinet stripStar(Petrinet pout) throws CompilationException {
    //System.out.println("\n Strip Star pout "+pout.myString());
    Set<String> roots = pout.getRoots().stream().
      flatMap(Set::stream).collect(Collectors.toSet());
    //System.out.println("stripStar roots "+roots);
    pout.getRootPlacess().clear();
    String rs = "";
    Set<PetriNetTransition> toStrip = new HashSet<>();
    if (roots.size() != 1) {
      //System.out.println("\nWARNING  root size = " + roots.size() + " other than for testing should be 1");
      Throwable t = new Throwable();
      t.printStackTrace();
    /*System.out.println("Owner build failed. Strip start failure "+pout.myString());
     throw new CompilationException(pout.getClass(),
       "Owner build failed. Strip start failure "+pout.myString()); */
      Set<String> allRoots = pout.getAllRoots().stream().map(x -> x.getId()).collect(Collectors.toSet());
      toStrip = allRoots.stream().map(x -> pout.getPlaces().get(x)).map(x -> x.post()).
        flatMap(Set::stream).collect(Collectors.toSet());
    } else {
      rs = roots.iterator().next();
      toStrip = pout.getPlaces().get(rs).post();
      //System.out.println("toStrip "+toStrip.size()+ " " +toStrip.stream().map(x->x.getId()+" ").collect(Collectors.joining()));
    }
    //System.out.println("rs = "+rs);
    List<Set<String>> newRoots = new ArrayList<>();
    Integer rindx = 1;
    for (PetriNetTransition tr : toStrip) {
      //System.out.println("trans "+tr.myString());
      Set<String> rtpost = tr.post().stream().map(x -> x.getId()).collect(Collectors.toSet());
      newRoots.add(rtpost);
      pout.removeTransition(tr);
      for (String plname : rtpost) {
        PetriNetPlace pl = pout.getPlaces().get(plname);
        pl.addStartNo(rindx);
        pl.setStart(true);
      }
      rindx++;
    }
    //System.out.println("rs = "+rs);
    if (pout.getPlaces().keySet().contains(rs)) pout.removePlace(pout.getPlaces().get(rs));
    pout.setRoots(newRoots);
    //pout.setStartFromRoot();
    //System.out.println("StripStar OUT"+pout.myString()+"\n");
    return pout;
  }

  private static String printVisited(Map<AutomatonNode, PetriNetPlace> v) {
    String out = "Visited {";
    for (AutomatonNode nd : v.keySet()) {
      out = out + " " + nd.getId() + "->" + v.get(nd).getId() + ",";
    }
    return out + "}";
  }

  private static String printPhase2(Map<AutomatonNode, AutomatonNode> v) {
    String out = "PHASE2 {";
    for (AutomatonNode nd : v.keySet()) {
      out = out + " " + nd.getId() + "->" + v.get(nd).getId() + " T " + v.get(nd).isTerminal() + ",";
    }
    return out + "}";
  }


  private static String mark2String(Map<String, PetriNetPlace> mark) {
    String x = "{";
    for (String k : mark.keySet()) {
      x = x + " " + k + "->" + mark.get(k).getId();
    }
    return x + " }";
  }


  private static void printaN2Marking() {
    System.out.println("aN2Marking");
    for (AutomatonNode nd : aN2Marking.keySet()) {
      System.out.println("  " + nd.getId() + " =>> ");
      for (String k : aN2Marking.get(nd).keySet()) {
        System.out.println("    " + k + " own2place " + aN2Marking.get(nd).get(k).myString());
        ;
      }
    }
  }


  /**
   * Build the set of nodes reachable by undirected edges
   * edges filtered by not containing owner own
   *
   * @param a
   * @param ndi
   * @param own
   * @return
   */

  private static Set<AutomatonNode> reach(Automaton a, AutomatonNode ndi, String own) {
    Set<AutomatonNode> processed = new HashSet<>();

    Stack<AutomatonNode> sofar = new Stack<>();
    sofar.push(ndi);

    while (!sofar.isEmpty()) {
      AutomatonNode nd = sofar.pop();
//System.out.println("reachfrom "+nd.getId());
      if (processed.contains(nd)) continue;
      processed.add(nd);

      Set<AutomatonNode> oneStep = new HashSet<>(nd.getOutgoingEdges().stream().
        filter(ed -> !ed.getOwnerLocation().contains(own)).
        map(e -> e.getTo()).collect(Collectors.toSet()));

      oneStep.addAll(nd.getIncomingEdges().stream().
        filter(ed -> !ed.getOwnerLocation().contains(own)).
        map(e -> e.getFrom()).collect(Collectors.toSet()));

      //System.out.println("All "+ union.stream().map(x->x.getId()+", ").collect(Collectors.joining()));
      sofar.addAll(oneStep);
    }

    return processed;
  }


  private static class todoStack {
    private Stack<AutomatonNode> stackit = new Stack<>();

    public void todoPush(AutomatonNode nd) {
      if (stackit.contains(nd)) {
        //System.out.println("stackit Duplicate " + nd.myString());
        return;
      } else {
        stackit.push(nd);
      }
    }

    public int todoSize() {
      return stackit.size();
    }

    public AutomatonNode todoPop() {
      return stackit.pop();
    }

    public boolean todoIsEmpty() {
      return stackit.isEmpty();
    }
  }

}

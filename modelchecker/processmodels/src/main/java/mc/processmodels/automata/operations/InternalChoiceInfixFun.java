package mc.processmodels.automata.operations;

import mc.exceptions.CompilationException;
import mc.plugins.IProcessInfixFunction;
import mc.processmodels.automata.Automaton;
import mc.processmodels.automata.AutomatonEdge;
import mc.processmodels.automata.AutomatonNode;
import mc.processmodels.petrinet.Petrinet;
import mc.processmodels.petrinet.components.PetriNetPlace;

import java.util.*;

/**
 * This covers the "internal choice" function.
 * This is a way that introduces non-determinism into the processes.
 * <p>
 * If this is introduced without a prefix, this will lead to multiple start nodes.
 * <p>
 * e.g. {@code A = B+C.}
 * <p>
 * <pre>
 * B   C
 * </pre>
 * However, if there are transitions that lead into the internal choice, it duplicates these
 * connections, creating a nondeterministic choice between the two processes provided
 * <p>
 * e.g. {@code A = b->C+D.}
 * <p>
 * <pre>
 *           ROOT
 *          /    \
 *         b      b
 *        /       \
 *       C         D
 * </pre>
 *
 * @author Jacob Beal
 * @see Automaton
 *
 */
public class InternalChoiceInfixFun implements IProcessInfixFunction {

  /**
   * A method of tracking the function.
   *
   * @return The Human-Readable form of the function name
   */
  @Override
  public String getFunctionName() {
    return "internalChoice";
  }

  /**
   * The form which the function will appear when composed in the text.
   *
   * @return the textual notation of the infix function
   */
  @Override
  public String getNotation() {
    return "+";
  }
  public Collection<String> getValidFlags(){return new HashSet<>();}
  /**
   * Execute the function
   *
   * @param id         the id of the resulting automaton
   * @param automaton1 the first  automaton in the function (e.g. {@code A} in {@code A||B})
   * @param automaton2 the second automaton in the function (e.g. {@code B} in {@code A||B})
   * @return the resulting automaton of the operation
   */
  @Override
  public Automaton compose(String id, Automaton automaton1, Automaton automaton2)
      throws CompilationException {
   //System.out.println("COMPOSE +");//Never get clled with STOP+P
    Automaton choice = new Automaton(id, !Automaton.CONSTRUCT_ROOT);

    choice.addAutomaton(automaton1);

    Map<AutomatonNode, AutomatonNode> automaton2NodeMap = new HashMap<>();

    automaton2.getNodes().forEach(n -> {
    //System.out.println("Adding "+ n.toString());
      AutomatonNode newN = choice.addNode();
      automaton2NodeMap.put(n, newN);
      newN.copyProperties(n);
      if (n.isStartNode()) {
        newN.setStartNode(true);
     //System.out.println("new is start" + newN.toString());
      }
    });
// Building ownership very complex   SO start with Petri Net
    for (AutomatonEdge ed : automaton2.getEdges()) {
     AutomatonEdge ednew = choice.addEdge(ed.getLabel(),
          automaton2NodeMap.get(ed.getFrom()),
          automaton2NodeMap.get(ed.getTo()),
          ed.getGuard() == null ? null : ed.getGuard().copy(), false,ed.getNotMaximalOwnedEdge());
    }


    return choice;
  }

  /**
   *
   * The Roots and Ends have  got to be the union of  the roots/ends  in both Nets
   * A Plae can be both Root and End
   *
   * @param id        the id of the resulting petrinet
   * @param net1 the first  petrinet in the function (e.g. {@code A} in {@code A||B})
   * @param net2 the second petrinet in the function (e.g. {@code B} in {@code A||B})
   * @param flags
   * @return the resulting petrinet of the operation
   */
  @Override
  public Petrinet compose(String id, Petrinet net1, Petrinet net2, Set<String> flags) throws CompilationException {


    net1.validatePNet();

    net2.validatePNet();
    //System.out.println("ok");
    Petrinet petrinet1 = net1.copy().reId("1"); // calls reOwn
    Petrinet petrinet2 = net2.copy().reId("2");
    //System.out.println("\n"+id+" + PETRI1 "+net1.myString());
    //System.out.println(id+" + PETRI2 "+net2.myString());
    TreeSet<String> o1 = new TreeSet<>();
    for(String el: petrinet1.getOwners()){
      o1.add(el);
    }
    TreeSet<String> o2 = new TreeSet<>();
    for(String el: petrinet2.getOwners()){
      o2.add(el);
    }
    List<Set<PetriNetPlace>> root1 = petrinet1.getRootPlacess();
    List<Set<PetriNetPlace>> root2 = petrinet2.getRootPlacess();
    List<Set<String>> ends1 = petrinet1.getEnds();
    List<Set<String>> ends2 = petrinet2.getEnds();
    //System.out.println("+PETRI1 "+petrinet1.myString());
    //System.out.println("+PETRI2 "+petrinet2.myString());
    if (petrinet1 == petrinet2) {
      System.out.println("\n SAME NETS PROBLEM");
    }
    for(PetriNetPlace pl1: petrinet1.getPlaces().values()){
      for(PetriNetPlace pl2: petrinet2.getPlaces().values()){
        if (pl1.getId().equals(pl2.getId())) System.out.println("\n SAME PLACES PROBLEM");
      }
    }

    //Petrinet choice = new Petrinet(id, false);
    //adding the Places and transitions
    petrinet2.addPetrinetNoOwner(petrinet1,"");
    //System.out.println("Internal "+petrinet2.myString());


    //The root is now that of external choice
    Petrinet choice = petrinet2;
    choice.clearRoots();
    //System.out.println("Internal Root cleared "+petrinet2.myString());
    choice.addRootsPl(root1);
    choice.addRootsPl(root2);
    choice.setEnds(ends2);
    choice.getEnds().addAll(ends1);
    //System.out.println("Internal check Rootand End on Net "+petrinet2.myString());
    choice.glueOwners(o1,o2); //NEEDED IN INTERNAL CHOICE

    choice.setRootFromNet();
    choice.setEndFromNet();
    choice.validatePNet();
    //System.out.println("**choice + RETURNS "+choice.myString());
    return choice;
  }
}

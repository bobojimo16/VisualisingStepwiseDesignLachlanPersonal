package mc.processmodels.automata.operations;

import com.google.common.collect.Multimap;
import com.rits.cloning.Cloner;
import mc.compiler.Guard;
import mc.exceptions.CompilationException;
import mc.processmodels.automata.Automaton;
import mc.processmodels.automata.AutomatonEdge;
import mc.processmodels.automata.AutomatonNode;
import mc.processmodels.petrinet.Petrinet;
import mc.processmodels.petrinet.components.PetriNetEdge;
import mc.processmodels.petrinet.components.PetriNetPlace;
import mc.processmodels.petrinet.components.PetriNetTransition;
import mc.processmodels.petrinet.operations.PetrinetReachability;
import mc.util.expr.MyAssert;

import java.util.*;
import java.util.stream.Collectors;

public class SequentialInfixFun {


  /**
   * Execute the function.
   *
   * @param id the id of the resulting automaton
   * @param a1 the first  automaton in the function (e.g. {@code A} in {@code A||B})
   * @param a2 the second automaton in the function (e.g. {@code B} in {@code A||B})
   * @return the resulting automaton of the operation
   */
  public Automaton compose(String id, Automaton a1, Automaton a2) throws CompilationException {
    //System.out.println("\n *********seqInfixFun***********");
    //System.out.println("a1 "+a1.myString());
    //System.out.println("a2 "+a2.myString());
    Automaton sequence = new Automaton(id, !Automaton.CONSTRUCT_ROOT);
    Cloner cloner = new Cloner();
    Automaton automaton1 = cloner.deepClone(a1);
    Automaton automaton2 = cloner.deepClone(a2);
      //System.out.println("automaton2 #0  "+ automaton2.myString());
    Multimap<String, String> setOfOwners = Automaton.ownerProduct(automaton1, automaton2);
      //System.out.println("automaton2 #1  "+ automaton2.myString());
    //System.out.println("setOfOwners "+setOfOwners.toString());
    //store a map to the nodes so id can be ignored
    Map<String, AutomatonNode> automata1nodes = new HashMap<>();
    Map<String, AutomatonNode> automata2nodes = new HashMap<>();

    // //System.out.println("Sequence aut1 "+ automaton1.toString());
    // //System.out.println("Sequence aut2 "+ automaton2.toString());
    //copy node1 nodes across
    AutomataReachability.removeUnreachableNodes(automaton1).getNodes().forEach(node -> {

      try {
        //System.out.println("1 adding "+node.myString());
        AutomatonNode newNode = sequence.addNode();
        newNode.copyProperties(node);
        automata1nodes.put(node.getId(), newNode);
        if (newNode.isStartNode()) {
          sequence.addRoot(newNode);
        }
      } catch (CompilationException e) {
        e.printStackTrace();
      }
    });

  //System.out.println("automaton2 #2  "+ automaton2.myString());

    copyAutomataEdges(sequence, automaton1, automata1nodes, setOfOwners);


    //get the stop nodes such that they can be replaced
    Collection<AutomatonNode> stopNodes = sequence.getNodes().stream()
      .filter(n -> n.isSTOP())
      .collect(Collectors.toList());
   /*System.out.print("stopNodes "+stopNodes.stream().
      map(x->x.getId()).reduce("{",(x,y)->x=x+" "+y)+"}"); */
    //if there are no stop nodes, we cannot glue them together
    if (stopNodes.isEmpty()) {
      //System.out.println("EMPTY STOP!");
      return sequence;
    }

      //System.out.println("automaton2 #3  "+ automaton2.myString());
//below copies the automaton hence renames the nodes
    AutomataReachability.removeUnreachableNodes(automaton2).getNodes().forEach(node -> {
      //System.out.println("2 adding "+node.myString());
      AutomatonNode newNode = sequence.addNode();
      newNode.copyProperties(node);
      automata2nodes.put(node.getId(), newNode);
      if (newNode.isStartNode()) {
        newNode.setStartNode(false);
        // for every stop node of automata1, get the edges that go into it
        // replace it with the start node of automata2
        for (AutomatonNode stopNode : stopNodes) {
          if (stopNode.getIncomingEdges().size() == 0) {// If automaton 1 is only a stop node
            newNode.setStartNode(true);
          }


          for (AutomatonEdge edge : stopNode.getIncomingEdges()) {
            AutomatonNode origin = edge.getFrom();
            //System.out.println("last "+edge.myString());
            try {
                AutomatonEdge ed = sequence.addEdge(edge.getLabel(), origin, newNode,
                                  edge.getGuard() == null ? null : edge.getGuard().copy(),
                                  edge.getOptionalOwners(), edge.getNotMaximalOwnedEdge());
              sequence.addOwnersToEdge(ed, edge.getEdgeOwners());
              ed.setMarkedOwners(edge.getMarkedOwners());
            } catch (CompilationException e) {
              e.printStackTrace();
            }
          }
        }
      }
    });

    stopNodes.stream().map(AutomatonNode::getIncomingEdges)
      .flatMap(List::stream)
      .forEach(sequence::removeEdge);
    stopNodes.forEach(sequence::removeNode);
    //System.out.println("automaton2 #4  "+ automaton2.myString());

    copyAutomataEdges(sequence, automaton2, automata2nodes, setOfOwners);

    //System.out.println("End Seq   "+sequence.myString());
    return sequence;
  }

  /**
   * Assumes Single End marking
   * A=>B   where B has roots Br1,Br2,.. Must build copies  A1,A2,..
   * The net A=>B is the union of B,A1,A2,...  and has roots A1r,A2r, ..
   * (the roots can not be glued together because of the case when Ar overlaps with Ae)
   * <p>
   * Symbolic Petri Nets -  put processing in the glueing function
   * Sequential
   * Boolean Guards (only on edges leaving n2Root)
   * Take the conjunction of the Guards on each edge leaving the n2 root and
   * add this to each edge leaving the new Glued Places
   * Assignmnets, ax (only on edges entering E1x an n1 End Place)
   * T1-ax->E1x   add to all edges T1-ax->(E1x,R2_)
   * <p>
   * Choice
   * Only Boolean Guards  treated the same as Sequential
   *
   * @param id the id of the resulting petrinet
   * @param n1 the first  petrinet in the function (e.g. {@code A} in {@code A||B})
   * @param n2 the second petrinet in the function (e.g. {@code B} in {@code A||B})
   * @return the resulting petrinet of the operation
   */
  public Petrinet compose(String id, Petrinet n1, Petrinet n2)
    throws CompilationException {
    //if (n1==null)System.out.println("compose n1 null");
    //if (n2==null)System.out.println("compose n2 null");
    return compose(id, n1, n2, null);
  }

  public Petrinet compose(String id, Petrinet n1, Petrinet n2, Guard guard)
    throws CompilationException {
    //System.out.println("=>PETRI1 "+ id+" "+n1.getId()+" => "+n2.getId());
    MyAssert.validate(n1,"nn1 ==> precondition ");
    MyAssert.validate(n1," ==> n2 precondition ");

 /*     myAssert(n1.validatePNet("Sequential => input "+n1.myString()+ " valid ="), "=> precondition");
    MyAssert.myAssert(n2.validatePNet("Sequential => input "+n2.myString()+ " valid ="), "=> precondition");
*/

    Petrinet net1 = n1.reId("1");
    Petrinet net2 = n2.reId("2"); // the tag "2" ensures unique ids in nets
    //System.out.println("\n   ***\n   ***\n1 =>  PETRI1 " + id + " " + net1.myString());
    //System.out.println("=> n2  PETRI2 " + id + " " + net2.myString());

    TreeSet<String> own1 = new TreeSet<>();
    TreeSet<String> own2 = new TreeSet<>();

    own1.addAll(net1.getOwners());
    own2.addAll(net2.getOwners());
    //System.out.println("In1=> " + net1.myString());
    //System.out.println("=>In2 " + net2.myString());
    if (net1.getId().equals(net2.getId())) {
      System.out.println("\n SAME NETS PROBLEM\n");
    }
    Petrinet composition = new Petrinet(id, false);
    if (n2.getRootEvaluation() != null && n2.getRootEvaluation().size() > 0) {
      composition.setRootEvaluation(n2.getRootEvaluation());
    }
    //List<Set<String>> p2roots = net2.getRoots();
    //int i = net2.getRoots().size();
    //System.out.println("ROOTS " + i);
    for (PetriNetPlace pl : net2.getPlaces().values()) {
      pl.setStart(false);
      pl.setStartNos(new HashSet<>());
    }
    for (PetriNetPlace pl : net1.getPlaces().values()) {
      pl.setEndNos(new HashSet<>());
      pl.setTerminal("");
    }
    List<Set<String>> oneEnd = net1.copyEnds();
    int i = 1;
    //Adding neet 1 and 2 ONCE  - later change root end bridge
    composition.addPetrinetNoOwner(net2, ""); // OLD worked but now need the owners
    composition.addPetrinetNoOwner(net1, "");
    composition.getOwners().addAll(own1);
    composition.getOwners().addAll(own2);
    Petrinet sequential;
    //System.out.println("\n ***Seq "+ net1.getEnds() +"  "+net2.getRoots());
    //System.out.println("\n  => comp1 \n"+composition.myString());  // has root and end of net1!
    //System.out.println("Ends = "+oneEnd);
    for (Set<String> ends : oneEnd) {

      for (Set<String> rt : net2.getRoots()) {
        // FOR EACH PAIR BUILD A NEW END-ROOT NODE THEN FICK IN PLACE
        //System.out.println("\n *******SEQ   START " + i++ + " end "+ends+ " root = " + rt);
        composition.setOwners(new TreeSet<>());

        net1.getPlaces().values().stream().forEach(x -> x.setTerminal(""));
        composition.setRootFromStart();
        Set<PetriNetPlace> newroot = new HashSet<>();//new root for each End
        //System.out.println("******     SEQUENTIAL beforCopy \n"+ composition.myString() );
        for (String r : rt) {
          newroot.add(composition.copyRootOrEnd(composition.getPlaces().get(r), "", true));
        }
        Set<PetriNetPlace> newend = new HashSet<>();
        for (String e : ends) {
          newend.add(composition.copyRootOrEnd(composition.getPlaces().get(e), "",false));
        }
        //System.out.println("******     SEQUENTIAL afterCopy \n"+ composition.myString() );
        composition.setUpv2o(n1, n2);
        composition.glueOwners(own1, own2); //must only be done once
        Multimap<String, String> s2s = composition.gluePlaces(newroot, newend);
        //Repair Root and End markers  useing s2s
        //composition.repairRootEnd(s2s);

        //System.out.println(" ***SEQ Glue places OVER \n" + composition.myString() + "\n");
      }
    }
    composition.removeRoots(oneEnd);
    composition.removeRoots(net2.getRoots()); // had to keep roots
    //composition.glueOwners(own1, own2);
    //System.out.println("\n  => comp2 \n"+composition.myString());

    //remove old ends  -- overlapping end sets!
    for (Set<String> eds : oneEnd) {
      //System.out.println("ends for removal " + eds);
      eds.retainAll(composition.getPlaces().keySet());  //Overlapping
      //System.out.println("ends for removal " + eds);
      for (String ed : eds) {
        PetriNetPlace pl = composition.getPlaces().get(ed);
        //System.out.println("  Place "+pl.myString());
        Set<PetriNetTransition> trs = pl.pre();
        //System.out.println("  trs " + trs.size());
        trs.retainAll(composition.getTransitions().values()); //Overlapping
        for (PetriNetTransition tr : trs) {
          //System.out.println("tr for Removal " + tr.getId());
          if (composition.getTransitions().containsKey(tr.getId())) {
            composition.removeTransition(tr);
          }
        }
        if (composition.getPlaces().containsKey(pl.getId())) {
          composition.removePlace(pl);
        }
      }
    }
    //System.out.println("\n  =>  comp3 \n"+composition.myString());

    composition.setRootFromStart();
    composition.setEndFromPlace();
    //System.out.println("comp3 "+composition.myString());
    sequential = PetrinetReachability.removeUnreachableStates(composition);
    //System.out.println("\n=> near end "+sequential.myString());


//*** must come after setEndFromPlace()
    Map<String, String> eval = n2.getRootEvaluation();
    //System.out.println("eval " + eval.toString());
    if (eval.size() > 0) {
      for (PetriNetEdge edge : sequential.getLastEdges()) {
        //System.out.println("Last edge " + edge.myString());
        if (edge.getGuard() == null) edge.setGuard(new Guard());
        edge.getGuard().setNextMap(eval);
        //System.out.println("Last edge " + edge.myString());
      }
    }

    //****
    //Petrinet seq = new Petrinet(id, false);
    //seq.addPetrinet(sequential);  // renumbers the ids
    //System.out.println("FINAL => " +sequential.myString());

    //MyAssert.myAssert(sequential.validatePNet("Sequential => input "+sequential.getId()+ " valid ="), "=> precondition");
    MyAssert.validate(sequential, "Sequential => output ");
    //System.out.println("=> END " + sequential.myString());
    return sequential;


  }

  /**
   * Copies the edges from one automata to another.
   *
   * @param writeAutomaton the automata that will have the edges copied to it
   * @param readAutomaton  the automata that will have the edges copied from it
   * @param nodeMap        the mapping of the ids to AutomatonNodes
   */
  private void copyAutomataEdges(Automaton writeAutomaton, Automaton readAutomaton,
                                 Map<String, AutomatonNode> nodeMap,
                                 Multimap<String, String> edgeOwnersMap) throws CompilationException {


    for (AutomatonEdge readEdge : readAutomaton.getEdges()) {
      AutomatonNode fromNode = nodeMap.get(readEdge.getFrom().getId());
      AutomatonNode toNode = nodeMap.get(readEdge.getTo().getId());
      AutomatonEdge ed  = writeAutomaton.addEdge(readEdge.getLabel(), fromNode, toNode, readEdge.getGuard(),
          getEdgeOwnersFromProduct(readEdge.getOptionalOwners(), edgeOwnersMap),
          readEdge.getNotMaximalOwnedEdge());

      writeAutomaton.addOwnersToEdge(ed , getEdgeOwnersFromProduct(readEdge.getEdgeOwners(), edgeOwnersMap));
      ed.setMarkedOwners(getEdgeOwnersFromProduct(readEdge.getMarkedOwners(), edgeOwnersMap));
//System.out.println("copyAutomataEdges "+readEdge.getMarkedOwners()+" -"+ed.getLabel()+"> "+   ed.getMarkedOwners());
 //System.out.println(ed.myString());
    }
      //System.out.println("WRITE "+writeAutomaton.myString());
  }

  private Set<String> getEdgeOwnersFromProduct(Set<String> edgeOwners,
                                               Multimap<String, String> productSpace) {
    return edgeOwners.stream().map(productSpace::get)
      .flatMap(Collection::stream)
      .collect(Collectors.toSet());
  }

}

/*
  if (petrinet1.getOwners().contains(Petrinet.DEFAULT_OWNER)) {
      petrinet1.getOwners().clear();
      for (String eId : petrinet1.getEdges().keySet()) {
        Set<String> owner = new HashSet<>();
        owner.add(petrinet1.getId());
        petrinet1.getEdges().get(eId).setOwners(owner);
      }
    }

    if (petrinet2.getOwners().contains(Petrinet.DEFAULT_OWNER)) {
      petrinet2.getOwners().clear();
      for (String eId : petrinet2.getEdges().keySet()) {
        Set<String> owner = new HashSet<>();
        owner.add(petrinet2.getId());
        petrinet2.getEdges().get(eId).setOwners(owner);
      }
    }
 */

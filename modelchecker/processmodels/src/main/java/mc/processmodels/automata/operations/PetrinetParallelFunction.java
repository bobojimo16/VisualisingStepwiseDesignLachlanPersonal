package mc.processmodels.automata.operations;

import com.google.common.collect.Iterables;

import java.util.*;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import mc.Constant;
import mc.exceptions.CompilationException;
import mc.processmodels.petrinet.Petrinet;
import mc.processmodels.petrinet.components.PetriNetEdge;
import mc.processmodels.petrinet.components.PetriNetPlace;
import mc.processmodels.petrinet.components.PetriNetTransition;
import mc.processmodels.petrinet.operations.PetrinetReachability;
import mc.util.MyAssert;

public class PetrinetParallelFunction  {

  //private static Set<String> unsynchedActions;
  private static Set<String> synchronisedActions;
  private static Map<Petrinet, Map<PetriNetPlace, PetriNetPlace>> petriPlaceMap;
  private static Map<PetriNetTransition, PetriNetTransition> petriTransMap;
  //private static final String tag1 = ""; //"*P1";
  //private static final String tag2 = ""; //"*P2";

  public static Petrinet compose(Petrinet pi1, Petrinet pi2,Set<String> flags) throws CompilationException {
    clear();
    Petrinet p1 = pi1.reId("1");
    Petrinet p2 = pi2.reId("2");
    List<Set<String>> newEnds = buildEnds(p1.getEnds(),p2.getEnds());
    p1.rebuildAlphabet(); p2.rebuildAlphabet();
    MyAssert.myAssert(p1.validatePNet("Parallel || input "+p1.getId()+ " valid ="), "|| precondition Failure");
    MyAssert.myAssert(p2.validatePNet("Parallel || input "+p2.getId()+ " valid ="), "|| precondition Failure");

    System.out.println("     PETRINET PARALLELFUNCTION"+" "+p1.getId()+" ||"+flags+" "+p2.getId());

   //builds synchronisedActions set
    setupActions(p1, p2,flags);
    //System.out.println("  synchronisedActions "+synchronisedActions);
    Petrinet composition = new Petrinet(p1.getId()  + p2.getId(), false);
    composition.getOwners().clear();
    composition.getOwners().addAll(p1.getOwners());
    composition.getOwners().addAll(p2.getOwners());

    List<Set<String>> roots = buildRoots(p1,p2);

    petriTransMap.putAll(composition.addPetrinetNoOwner(p1,""));  //Tag not needed as reId dose this
    //System.out.println("par "+ composition.myString());
    petriTransMap.putAll(composition.addPetrinetNoOwner(p2,"")); //adds unsynchronised transitions
    //System.out.println("newEnds "+newEnds);
    composition.setRoots(roots);
    composition.setEnds(newEnds);
    composition.setStartFromRoot();
    composition.setEndFromNet();
    //System.out.println("BeforeSYNC END " +composition.myString());
    //do not merge places?
     setupSynchronisedActions(p1, p2, composition);
    //System.out.println("AfterSYNC END " +composition.myString());


    composition = PetrinetReachability.removeUnreachableStates(composition, false);
    //System.out.println("  synced  \n "+ composition.myString("edges"));

     composition.reId("");
    MyAssert.myAssert(composition.validatePNet("Parallel || output "+composition.getId()+ " valid ="), "||  Failure");

    //System.out.println("\n   PAR end "+composition.myString());
    return composition;
  }

  /**
   *
   * @param net1
   * @param net2
   * @return the multiRoot for parallel composition of the nets
   */
  private static List<Set<String>> buildRoots(Petrinet net1,Petrinet net2) {
    //System.out.println("Building Roots");
    List<Set<String>> out = new ArrayList<>();
    for(Set<String> m1: net1.getRoots()) {
      for(Set<String> m2: net2.getRoots()) {
        out.add(buildMark(m1,m2));
      }
    }
    //System.out.println("New buildRoots "+out);
    return out;
  }
  private static List<Set<String>> buildEnds(List<Set<String>> p1, List<Set<String>> p2){
    //System.out.println("Build Ends input "+p1+" "+p2);
    List<Set<String>> out = new ArrayList<>();
    for(Set<String> e1: p1){
      for(Set<String> e2: p2){
        Set<String> o = new HashSet<>() ;
        o.addAll(e1);
        o.addAll(e2);
        o = o.stream().distinct().sorted().collect(Collectors.toSet());
        out.add( o );
      }
    }
    //System.out.println("Build Ends returns  "+out);
    return out;
  }

  /*
  Adds actions to synchronisedActions.
   */
  private static void setupActions(Petrinet p1, Petrinet p2,Set<String> flags) {
    //System.out.println("setupActions flags "+ flags);
    if (flags.size()==0) {
      Set<String> actions1 = p1.getAlphabet().keySet();
      Set<String> actions2 = p2.getAlphabet().keySet();
      actions1.forEach(a -> setupAction(a, actions2));
      actions2.forEach(a -> setupAction(a, actions1));
    } else {
      flags.forEach(a -> setupAction(a, flags));
    }
    //System.out.println("setupAction "+synchronisedActions);
  }

  private static void setupAction(String action, Set<String> otherPetrinetActions) {
     if (action.endsWith(Constant.BROADCASTSoutput)) {
      if (containsReceiverOf(action, otherPetrinetActions)) {
        synchronisedActions.add(action);
      } else if (containsBroadcasterOf(action, otherPetrinetActions)) {
        synchronisedActions.add(action);
      }
    } else if (action.endsWith(Constant.BROADCASTSinput)) {
      if (containsReceiverOf(action, otherPetrinetActions)) {
        synchronisedActions.add(action);
      } else if (containsBroadcasterOf(action, otherPetrinetActions)) {
        synchronisedActions.add(action);
      }
    } else if (action.endsWith(Constant.ACTIVE)) {
       String passiveAction = action.substring(0, action.length() - 1);

       if (otherPetrinetActions.contains(passiveAction)) {
         synchronisedActions.add(action);
         //synchronisedActions.add(passiveAction);
       }
     }else if (otherPetrinetActions.contains(action)) {
      synchronisedActions.add(action);
    }
    //System.out.println("Sync "+ action+ " with "+ synchronisedActions);
  }

  @SneakyThrows(value = {CompilationException.class})
  private static void setupSynchronisedActions(Petrinet p1, Petrinet p2, Petrinet comp) {
    //System.out.println("Start setupSynchronisedActions ");
    for (String action : synchronisedActions) {
      Set<PetriNetTransition> p1P = new TreeSet<>();
      Set<PetriNetTransition> p2P = new TreeSet<>();
      List<PetriNetTransition> toGo = new ArrayList<>();
      //System.out.println("  action = "+action+ "");
      if (action.endsWith(Constant.BROADCASTSoutput)) {
        String sync = action.substring(0, action.length() - 1)+Constant.BROADCASTSinput;
        //System.out.println("Bcast sync = "+sync);
        p1P = p1.getAlphabet().get(action).stream()
                .map(t -> petriTransMap.get(t)).collect(Collectors.toSet());
        p2P = p2.getAlphabet().get(sync).stream()
                .map(t -> petriTransMap.get(t)).collect(Collectors.toSet());
        toGo.addAll(p2P.stream().collect(Collectors.toSet()));
        toGo.addAll(p1P.stream().collect(Collectors.toSet()));
        replaceActions(p1P, p2P, comp, action, true);  //p1P = out
         p1P = p1.getAlphabet().get(sync).stream()
                .map(t -> petriTransMap.get(t)).collect(Collectors.toSet());
         p2P = p2.getAlphabet().get(action).stream()
                .map(t -> petriTransMap.get(t)).collect(Collectors.toSet());
        toGo.addAll(p2P.stream().collect(Collectors.toSet()));
        toGo.addAll(p1P.stream().collect(Collectors.toSet()));
        replaceActions(p2P, p1P, comp, action, true); //p2P = out
        //System.out.println("Sync CHECK END " +comp.getEnds());
      }
      else if (action.endsWith(Constant.ACTIVE)) {
        String sync = action.substring(0, action.length() - 1);

        p1P = p1.getAlphabet().get(action).stream()
          .map(t -> petriTransMap.get(t)).collect(Collectors.toSet());
        p2P = p2.getAlphabet().get(sync).stream()
          .map(t -> petriTransMap.get(t)).collect(Collectors.toSet());
        toGo.addAll(p2P.stream().collect(Collectors.toSet()));
        toGo.addAll(p1P.stream().collect(Collectors.toSet()));
        replaceActions(p1P, p2P, comp, action, true);  //active in p1P
        p1P = p1.getAlphabet().get(sync).stream()
          .map(t -> petriTransMap.get(t)).collect(Collectors.toSet());
        p2P = p2.getAlphabet().get(action).stream()
          .map(t -> petriTransMap.get(t)).collect(Collectors.toSet());
        toGo.addAll(p2P.stream().collect(Collectors.toSet()));
        toGo.addAll(p1P.stream().collect(Collectors.toSet()));
        replaceActions(p2P, p1P, comp, action, true);  //active in p2P
      } else {
        //do nothing
      }

      //System.out.println("REMOVE "+toGo.stream().map(x->x.getId()).reduce((x,y)-> x+" "+y) );
      removeoldTrans(comp,toGo.stream().distinct().collect(Collectors.toSet()));
      //System.out.println("Sync2 CHECK END " +comp.getEnds());
    }

    //System.out.println("Sync END BC"+comp.myString());
    setupSynchronisedHS(p1,p2,comp);
    //System.out.println("Sync END HS"+comp.myString());

  }
  @SneakyThrows(value = {CompilationException.class})
  private static void setupSynchronisedHS(Petrinet p1, Petrinet p2, Petrinet comp) {
    //System.out.println("Start setupSynchronisedHS ");
    for (String action : synchronisedActions) {
      //System.out.println("setupSyncHS action " + action);
      Set<PetriNetTransition> p1Pair = p1.getAlphabet().get(action).stream()
        .map(t -> petriTransMap.get(t)).collect(Collectors.toSet());
      Set<PetriNetTransition> p2Pair = p2.getAlphabet().get(action).stream()
        .map(t -> petriTransMap.get(t)).collect(Collectors.toSet());
      if (p1Pair.size() > 0 && p2Pair.size() > 0) {
        replaceActions(p1Pair, p2Pair, comp, action, false); //handshake on label equality
      }
      //System.out.println("p1Pair "+p1Pair.size());
      //p1Pair.stream().forEach(x->{System.out.println(x.myString());});
      //System.out.println("p2Pair "+p2Pair.size());
      //p2Pair.stream().forEach(x->{System.out.println(x.myString());});
      Set<PetriNetTransition> toGo = p1Pair.stream().collect(Collectors.toSet());
      toGo.addAll(p2Pair.stream().collect(Collectors.toSet()));
      removeoldTrans(comp,toGo);

    }

  }
/*
   Replace each pair of synchronising transitions with their combined transition
   outputs in p1_ and inputs in p2_
 */
  private static void replaceActions(Set<PetriNetTransition> p1_ , Set<PetriNetTransition> p2_ ,
                                     Petrinet comp, String action,boolean optional)
  throws  CompilationException {
    //optional is only true if one tran is b! and the other b?
    //only synced transitions passed into the method
    //if (p1_.size()==0 || p2_.size() ==0) return; // Must continue as delete transitions at end


    //System.out.println("Replace actions "+ p1_.size()+" "+p2_.size());
    //System.out.println("p1 "+p1_.stream().map(x->x.getLabel()).reduce("",(x,y)->x+y+" "));
    //System.out.println("p2 "+p2_.stream().map(x->x.getLabel()).reduce("",(x,y)->x+y+" "));
    for (PetriNetTransition t1 : p1_) {
        for (PetriNetTransition t2 : p2_) {
          //System.out.println("Replaceing   "+t1.myString()+" "+t2.myString());
          if (t1==null) {System.out.println("t1==null");continue;}
          if (t2==null) {System.out.println("t2==null");continue;}
   //System.out.println("  t1 "+ t1.myString()+ " , t2 "+t2.myString());
          Set<PetriNetEdge> outgoingEdges = new LinkedHashSet<>();
          outgoingEdges.addAll(t1.getOutgoing());
          outgoingEdges.addAll(t2.getOutgoing());

          Set<PetriNetEdge> incomingEdges = new LinkedHashSet<>();
          incomingEdges.addAll(t1.getIncoming());
          incomingEdges.addAll(t2.getIncoming());

          PetriNetTransition newTrans = comp.addTransition(action);
          newTrans.clearOwners();
          newTrans.addOwners(t1.getOwners());
          newTrans.addOwners(t2.getOwners());
          //System.out.println("Added "+newTrans.myString());
   //System.out.println("size "+incomingEdges.size()+" "+incomingEdges.size());
          //Set broadcast listening b? edges to optional
          for(PetriNetEdge outE : outgoingEdges) { // outgoing from transition
            //System.out.println("out "+outE.myString());
            PetriNetEdge ed =  comp.addEdge( (PetriNetPlace) outE.getTo(), newTrans,outE.getOptional());
           if (((PetriNetTransition) outE.getFrom()).getLabel().endsWith(Constant.BROADCASTSinput) && optional) {
              ed.setOptional(true);
            }
            //System.out.println("    adding "+ed.myString());
          }
          //System.out.println("  newTran "+newTrans.myString());
          for(PetriNetEdge inE : incomingEdges) { // incoming to transition
            //System.out.println("in  "+inE.myString());
            PetriNetEdge ed = comp.addEdge(newTrans, (PetriNetPlace) inE.getFrom(),inE.getOptional());
            if (((PetriNetTransition) inE.getTo()).getLabel().endsWith(Constant.BROADCASTSinput)&& optional) {
              ed.setOptional(true);
            }
            //System.out.println("    adding "+ed.myString());
          }
          //System.out.println("  newTrans "+newTrans.myString());

        }
    }

  //need to add output when listening is implicit
    if (p2_.size()==0) {
      for (PetriNetTransition t1 : p1_) {
        if (t1.getLabel().endsWith(Constant.BROADCASTSoutput)) {
          PetriNetTransition newTrans = comp.addTransition(t1.getLabel());
          newTrans.clearOwners();
          newTrans.addOwners(t1.getOwners());

          for (PetriNetEdge outE : t1.getOutgoing()) { // outgoing from transition
            //System.out.println("out "+outE.myString());
            PetriNetEdge ed = comp.addEdge((PetriNetPlace) outE.getTo(), newTrans, outE.getOptional());
            //System.out.println("    *adding " + ed.myString());
          }
          for(PetriNetEdge inE : t1.getIncoming()) { // incoming to transition
            //System.out.println("in  "+inE.myString());
            PetriNetEdge ed = comp.addEdge(newTrans, (PetriNetPlace) inE.getFrom(),inE.getOptional());
            //System.out.println("    *adding "+ed.myString());
          }
          //System.out.println("  *newTrans "+newTrans.myString());
        }
      }
    }

    //System.out.println("RepAct CHECK END " +comp.getEnds());
  }

  private static void removeoldTrans(Petrinet comp, Set<PetriNetTransition> toGo)
    throws  CompilationException {
    //System.out.println("Removing ");
    for (PetriNetTransition oldTrans : toGo) {
      //System.out.print(" id "+oldTrans.getId()+" ");
      if (comp.getTransitions().containsValue(oldTrans))  {
        //System.out.println("removing "+oldTrans.myString());
        comp.removeTransition(oldTrans);
      } else {
        //System.out.println("SKIPPING");
      }
    }
  }


  private static boolean containsReceiverOf(String broadcaster, Collection<String> otherPetrinet) {
    for (String reciever : otherPetrinet) {
      if (reciever.endsWith(Constant.BROADCASTSinput)) {
        if (reciever.substring(0, reciever.length() - 1).equals(broadcaster.substring(0, broadcaster.length() - 1))) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean containsBroadcasterOf(String broadcaster, Set<String> otherPetrinet) {
    String broadcastAction = broadcaster.substring(0, broadcaster.length() - 1);
    for (String receiver : otherPetrinet) {
      if (receiver.endsWith(Constant.BROADCASTSoutput)) {
        String action = receiver.substring(0, receiver.length() - 1);
        if (action.equals(broadcastAction)) {
          return true;
        }
      }
    }

    return false;
  }

  private static void clear() {
    //unsynchedActions = new HashSet<>();
    synchronisedActions = new HashSet<>();
    petriPlaceMap = new HashMap<>();
    petriTransMap = new HashMap<>();
  }

  @SneakyThrows(value = {CompilationException.class})
  public static List<Set<String>> addPetrinet(Petrinet addTo, Petrinet petriToAdd) {
    //addTo.validatePNet();
    //petriToAdd.validatePNet();
   //System.out.println("IN AddTo "+addTo.myString());
   //System.out.println("IN ToAdd "+petriToAdd.myString());
    List<Set<String>> roots = addTo.getRoots();
   //System.out.println("roots "+roots);
    Map<PetriNetPlace, PetriNetPlace> placeMap = new HashMap<>();
    Map<PetriNetTransition, PetriNetTransition> transitionMap = new HashMap<>();

    for (PetriNetPlace place : petriToAdd.getPlaces().values()) {
      PetriNetPlace newPlace = addTo.addPlace();
      newPlace.copyProperties(place);

      if (place.isStart()) {
        newPlace.setStart(true);
      }

      placeMap.put(place, newPlace);
    }
    for (PetriNetTransition transition : petriToAdd.getTransitions().values()) {
      PetriNetTransition newTransition = addTo.addTransition(transition.getLabel());
      transitionMap.put(transition, newTransition);
    }

    for (PetriNetEdge edge : petriToAdd.getEdges().values()) {
      //System.out.println(edge.myString());
      if (edge.getFrom() instanceof PetriNetPlace) {
        //System.out.println("tran "+transitionMap.get(edge.getTo()).myString());
        PetriNetEdge e = addTo.addEdge( transitionMap.get(edge.getTo()), placeMap.get(edge.getFrom()),edge.getOptional());
        e.setOptional(edge.getOptional());

      } else {
        //System.out.println("place "+placeMap.get(edge.getTo()).myString());
        PetriNetEdge e = addTo.addEdge( placeMap.get(edge.getTo()), transitionMap.get(edge.getFrom()),edge.getOptional());
        e.setOptional(edge.getOptional());
      }
    }
   //System.out.println("toAdd roots"+petriToAdd.getRoots());
    roots.addAll(petriToAdd.getRoots());
   //System.out.println("roots"+petriToAdd.getRoots());
    addTo.setRoots(roots);
    petriTransMap.putAll(transitionMap);
    petriPlaceMap.put(petriToAdd, placeMap);

    //addTo.validatePNet();
  //System.out.println("OUT AddedTo "+addTo.myString());
    return roots;
  }



  private static Set<String> buildMark(Set<String> m1, Set<String> m2){
    Set<String> out = new HashSet<>();
    out.addAll(m1);
    out.addAll(m2);
    out = out.stream().sorted().collect(Collectors.toSet());
   //System.out.println("Next root "+out);
    return out;
  }
}


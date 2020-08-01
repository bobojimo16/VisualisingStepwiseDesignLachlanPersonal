package mc.client;


import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multiset;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javafx.application.Platform;
import lombok.Getter;
import lombok.Setter;
import mc.Constant;
import mc.client.graph.*;
import mc.client.ui.*;
import mc.compiler.CompilationObject;
import mc.compiler.CompilationObservable;
import mc.compiler.OperationResult;
import mc.processmodels.*;
import mc.processmodels.automata.Automaton;
import mc.processmodels.automata.AutomatonEdge;
import mc.processmodels.conversion.TokenRule;
import mc.processmodels.conversion.TokenRulePureFunctions;
import mc.processmodels.petrinet.Petrinet;
import mc.processmodels.petrinet.components.PetriNetEdge;
import mc.processmodels.petrinet.components.PetriNetPlace;
import mc.processmodels.petrinet.components.PetriNetTransition;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.ui.geom.Point2;
import org.graphstream.ui.geom.Point3;
import org.graphstream.ui.graphicGraph.GraphicElement;
import org.graphstream.ui.graphicGraph.GraphicGraph;
import org.graphstream.ui.layout.Layout;
import org.graphstream.ui.layout.Layouts;
import org.graphstream.ui.view.Camera;
import org.graphstream.ui.view.View;
import org.graphstream.ui.view.Viewer;

import javax.swing.*;

import org.graphstream.graph.*;
import org.graphstream.ui.view.util.ShortcutManager;

/**
 * Created by bealjaco on 29/11/17.
 * Currently called by UseInterfaceController  FMX App
 * dstr  needs to refactored.
 * hateful jung connected here no documentation
 * MUST remove jung! After 2 years still can not find if I can (and how to) change
 * some of the bacis graph features
 * <p>
 * Refractoring work commenced by Lachlan on 1/04/20 for his 489 supervised by David
 * Main intension therin is to replace Jung with new GraphStream framework plus implement new UX features
 */
public class ModelView implements Observer {

    private Set<String> processModelsToDisplay;
    private SortedSet<String> modelsInList; // Processes that are in the modelsList combox
    private Multimap<String, GraphNode> processModelsOnScreenGraphNodeType; //process on the screen
    private Multimap<String, Node> processModelsOnScreenGSType; //process on the screen
    private HashMap<String, Automaton> pmTest = new HashMap<>(); //process on the screen
    private List<String> processesChanged = new ArrayList<>();
    private Map<String, GraphNode> placeId2GraphNode = new TreeMap<>();
    private CompilationObject compiledResult;
    private Map<String, MappingNdMarking> mappings = new HashMap<>();
    private MultiGraph workingCanvasArea; //For GraphStream
    private Viewer workingCanvasAreaViewer;
    private View workingCanvasAreaView;
    private boolean addingAutoNodeStart;
    private boolean addingAutoNodeNeutral;
    private boolean addingAutoNodeEnd;
    private Node latestNode;
    private Node firstNodeClicked;
    private Node seccondNodeClicked;
    private Layout workingLayout;
    private ProcessMouseManager PMM;
    private boolean nodeRecentlyPlaced;
    private UserInterfaceController uic;
    private ArrayList<Node> createdNodes = new ArrayList<>();
    private JPanel workingCanvasAreaContainer;
    private ArrayList<Edge> createdEdges = new ArrayList<>();
    private boolean addingPetriPlaceStart;
    private boolean addingPetriPlaceNeutral;
    private boolean addingPetriPlaceEnd;
    private boolean addingPetriTransition;
    private HashMap graphNodeToHeadPetri = new HashMap<PetriNetPlace, Node>();
    private ArrayList pathColours = new ArrayList();
    private HashMap<String, String> ownersToPID = new HashMap();
    private Set<Node> petriTransitions = new HashSet<>();
    private double syncingTransitionWeightValue = 1;
    private double relatingWeightValue = 1;

    @Setter
    private SettingsController settings; // Contains linkage length and max nodes

    @Setter
    private Consumer<Collection<String>> listOfAutomataUpdater;
    @Setter
    private BiConsumer<List<OperationResult>, List<OperationResult>> updateLog;
    private String firstNodeClass;
    private double zoom;
    private boolean autoPetriRelationModeEnabled = false;
    private ArrayList<Edge> petriAutoRelations = new ArrayList<>();
    public String interactionType = "create";

    private Petrinet testInitialPetri = null;

    public ProcessModel getProcess(String id) {
        return compiledResult.getProcessMap().get(id);
    }

    public void removeProcess(String id) {
        compiledResult.getProcessMap().remove(id);
    }

    public void setReferenceToUIC(UserInterfaceController userInterfaceController) {
        uic = userInterfaceController;
    }


    /**
     * This method is called whenever the observed object is changed. An
     * application calls an <tt>Observable</tt> object's
     * <code>notifyObservers</code> method to have all the object's
     * observers notified of the change.
     *
     * @param o   the observable object.
     * @param arg an argument passed to the <code>notifyObservers</code>
     */
    @Override
    public void update(Observable o, Object arg) {

        if (!(arg instanceof CompilationObject)) {
            throw new IllegalArgumentException("arg object was not of type compilationObject");
        }

        processesChanged.clear();
        compiledResult = (CompilationObject) arg;


        //Extracts set of ProcessModel maps and converts into set of Multiprocess maps called to expand
        // UG Map.Entry  collection of Key,Value pairs
        Set<Map.Entry<String, MultiProcessModel>> toExpand = compiledResult.getProcessMap().entrySet()
            .stream()
            .filter(e -> e.getValue() instanceof MultiProcessModel)
            .map(e -> new Map.Entry<String, MultiProcessModel>() {
                @Override
                public String getKey() {
                    return e.getKey();
                }

                @Override
                public MultiProcessModel getValue() {
                    return (MultiProcessModel) e.getValue();
                }

                @Override
                public MultiProcessModel setValue(MultiProcessModel value) {
                    return null;
                }
            })
            .collect(Collectors.toSet());

//  printing (automata) and (petrinet) names and iterates through toExpand to extract node and edge data into mapping and compiledresult ?
        mappings.clear();
        for (Map.Entry<String, MultiProcessModel> mpm : toExpand) {
            if (!mpm.getKey().endsWith(":*")) continue; //Only off processes in Domain * - prevents duplicates
            for (ProcessType pt : ProcessType.values()) {
                if (mpm.getValue().hasProcess(pt)) {
                    String name = mpm.getKey() + " (" + pt.name().toLowerCase() + ")";
                    mpm.getValue().getProcess(pt).setId(name);
                    mappings.put(name, mpm.getValue().getProcessNodesMapping());
                    compiledResult.getProcessMap().put(name, mpm.getValue().getProcess(pt));
                }
            }
        }


        //Removes the original process map leaving the expanded node and edge data from before
        toExpand.stream().map(Map.Entry::getKey).forEach(compiledResult.getProcessMap()::remove);


        String dispType = settings.getDisplayType();

        //Stores process models in modelsInList depending on wether current display setting wants it

        if (dispType.equals("All")) {
            modelsInList = getProcessMap().entrySet().stream()
                .filter(e -> e.getValue().getProcessType() != ProcessType.AUTOMATA ||
                    ((Automaton) e.getValue()).getNodes().size() <= settings.getMaxNodes())
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));
        } else if (dispType.equals(Constant.AUTOMATA)) {
            modelsInList = getProcessMap().entrySet().stream()
                .filter(e -> e.getValue().getProcessType() == ProcessType.AUTOMATA &&
                    ((Automaton) e.getValue()).getNodes().size() <= settings.getMaxNodes())
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));
        } else {
            modelsInList = getProcessMap().entrySet().stream()
                .filter(e -> e.getValue().getProcessType() != ProcessType.AUTOMATA)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));
        }
        //remove processes marked at skipped and too large models to display
        listOfAutomataUpdater.accept(modelsInList);


    }


    public JPanel updateGraphNew() {

        if (compiledResult == null) {
            return workingCanvasAreaContainer;
        }

        //workingCanvasArea.clear();
        workingCanvasArea.addAttribute("ui.stylesheet", getStyleSheet());
        workingCanvasArea.addAttribute("ui.quality");
        workingCanvasArea.addAttribute("ui.antialias");
//
        //Do Drawing Work On Canvas
        compiledResult.getProcessMap().keySet().stream()
            .filter(processModelsToDisplay::contains)
            .filter(processesChanged::contains)
            .map(compiledResult.getProcessMap()::get)
            .filter(Objects::nonNull)
            .forEach(this::addProcessNew);

        drawCreatedProcesses();

        processesChanged.clear();

        //Return the Now updated canvas to UIC
        return workingCanvasAreaContainer;
    }

    private void drawCreatedProcesses() {

        for (Node cn : createdNodes) {

            if (workingCanvasArea.getNode(cn.getId()) != null) {
                continue;
            }

            Node n = workingCanvasArea.addNode(cn.getId());
            String cnAttributeAutoType = cn.getAttribute("ui.class");
            n.addAttribute("ui.class", cnAttributeAutoType); // dk ytf cant do this directly

            String cnAttributeLabel = cn.getAttribute("ui.label");

            if (cnAttributeLabel != null) { //Is start so label it
                n.addAttribute("ui.label", cnAttributeLabel); // dk ytf cant do this directly
            }
        }

        for (Edge ce : createdEdges) {

            if (workingCanvasArea.getEdge(ce.getId()) != null) {
                continue;
            }

            Edge e = workingCanvasArea.addEdge(ce.getNode0().getId() + "-" + ce.getNode1().getId(), (Node) ce.getNode0(), (Node) ce.getNode1(), true);
            String cnEAttributeLabel = ce.getAttribute("ui.label");
            e.addAttribute("ui.label", cnEAttributeLabel);
        }
    }


    private void addProcessNew(ProcessModel p) {
        switch (p.getProcessType()) {
            case AUTOMATA:
                addAutomataNew((Automaton) p);
                break;
            case PETRINET:
                addPetrinetNew((Petrinet) p);
                break;
        }
    }


    private void addAutomataNew(Automaton automaton) {

        if (workingCanvasArea.getNode(automaton.getId()) != null) {
            System.out.println("Auto On screen");
            return;
        }

        Map<String, GraphNode> nodeMap = new HashMap<>();
        Map<String, Node> nodeMapGS = new HashMap<>();
        //Adds grapth node to display
        automaton.getNodes().forEach(n -> {
            NodeStates nodeTermination = NodeStates.NOMINAL;

            if (n.isStartNode()) {
                GraphNode node = new GraphNode(automaton.getId(), automaton.getId(), nodeTermination, nodeTermination,
                    NodeType.AUTOMATA_NODE, "" + n.getLabelNumber(), n);
                nodeMap.put(n.getId(), node);
            } else {
                GraphNode node = new GraphNode(automaton.getId(), n.getId(), nodeTermination, nodeTermination,
                    NodeType.AUTOMATA_NODE, "" + n.getLabelNumber(), n);
                nodeMap.put(n.getId(), node);
            }

            Node cn;

            if (n.isStartNode()) {
                cn = workingCanvasArea.addNode(automaton.getId());
            } else {
                cn = workingCanvasArea.addNode(n.getId());
            }

            if (n.isStartNode()) {
                cn.addAttribute("ui.label", automaton.getId());
                cn.addAttribute("ui.class", "AutoStart");
            } else if (!n.isStartNode() && !n.isSTOP()) {
                cn.addAttribute("ui.class", "AutoNeutral");
            } else {
                cn.addAttribute("ui.class", "AutoEnd");
            }

            nodeMapGS.put(n.getId(), cn);

        });

        //Connects the node via edges on screen
        automaton.getEdges().forEach(e -> {


            GraphNode to = nodeMap.get(e.getTo().getId());

            GraphNode from = nodeMap.get(e.getFrom().getId());
            String label = e.getLabel();
            String bool;
            String ass;
            if (e.getGuard() != null) {
                bool = e.getGuard().getGuardStr();
                ass = e.getGuard().getAssStr();
            } else {
                bool = "";
                ass = "";
            }
            if (settings.isShowOwners()) {
                label += e.getEdgeOwners();
            }


            Edge edge = workingCanvasArea.addEdge(from.getNodeId() + "-" + to.getNodeId(), from.getNodeId(), to.getNodeId(), true);
            edge.addAttribute("ui.label", label);


        });

        this.pmTest.put(automaton.getId(), automaton);
        this.processModelsOnScreenGraphNodeType.replaceValues(automaton.getId(), nodeMap.values());
        this.processModelsOnScreenGSType.replaceValues(automaton.getId(), nodeMapGS.values());

        if (interactionType.equals("autopetrirelation")) {
            handleAutoPetriRelation();
        }
    }


    private void addPetrinetNew(Petrinet petri) {

        if(testInitialPetri == null){
            testInitialPetri = petri;
        }

        if (processModelsOnScreenGSType.containsKey(petri.getId())) {
            System.out.println("petri On screen");
            return;
        }

        Set<PetriNetPlace> roots = petri.getAllRoots();


        TreeSet<String> petriOwners = petri.getOwners();
        HashMap<String, String> ownerColours = new HashMap<>();

        int colourTracker = 0;
        String colour = "";
        for (String o : petriOwners) {

            if (colourTracker > pathColours.size()) {
                colourTracker = 0;
            }

            colour = (String) pathColours.get(colourTracker);
            ownerColours.put(o, colour);
            colourTracker++;
        }


        Set<PetriNetPlace> petriStarts = petri.getAllRoots();
        int petriStartsSize = petriStarts.size();
        AtomicInteger petriStartSizeTracker = new AtomicInteger();
        petriStartSizeTracker.getAndIncrement();
        Map<PetriNetPlace, Integer> startToIntValue = new HashMap<>();


        Map<String, GraphNode> nodeMap = new HashMap<>();
        Map<String, Node> nodeMapGS = new HashMap<>();


        Multiset<PetriNetPlace> rts = HashMultiset.create(); // .create(rts);
        petri.getPlaces().values().forEach(place -> {




            NodeStates nodeTermination = NodeStates.NOMINAL;
            if (place.isTerminal()) {
                nodeTermination = NodeStates.valueOf(place.getTerminal().toUpperCase());
            }

            if (place.isStart()) {
                rts.add(place);
            }

            String lab = "";
            if (settings.isShowIds()) lab = place.getId();
            // changing the label on the nodes forces the Petri Net to be relayed out.
            GraphNode node = new GraphNode(petri.getId(), place.getId(),
                nodeTermination, nodeTermination, NodeType.PETRINET_PLACE, lab, place);
            placeId2GraphNode.put(place.getId(), node);

            Node n;

            if (place.isStart()) {
                n = workingCanvasArea.addNode(petri.getId() + (petriStartsSize + 1 - petriStartSizeTracker.get()));
                //place.setGSName(petri.getId() + (petriStartsSize + 1 - petriStartSizeTracker.get()));
                startToIntValue.put(place, (petriStartsSize + 1 - petriStartSizeTracker.get()));
                graphNodeToHeadPetri.put(place, n);
                petriStartSizeTracker.getAndIncrement();
            } else {
                place.setId(place.getId() + petri.getId());
                n = workingCanvasArea.addNode(place.getId());
            }


            if (place.isStart()) {
                n.addAttribute("ui.label", petri.getId() + startToIntValue.get(place));
                n.addAttribute("ui.class", "PetriPlaceStart");


            } else if (!place.isStart() && !place.isSTOP()) {
                n.addAttribute("ui.class", "PetriPlace");
            } else {
                n.addAttribute("ui.class", "PetriPlaceEnd");
            }


            n.addAttribute("ui.PID", petri.getId()); // why the fuck is this here? - base PID gets updated later if need be
            n.addAttribute("ui.GraphNode", node);
            nodeMap.put(place.getId(), node);
            nodeMapGS.put(place.getId(), n);



            n.addAttribute("ui.owners", place.getOwners());
        });

        CurrentMarkingsSeen.currentMarkingsSeen.put(petri.getId(), rts);
        CurrentMarkingsSeen.addRootMarking(petri.getId(), rts);


        petri.getTransitions().values().stream().filter(x -> !x.isBlocked())
            .forEach(transition -> {

                String lab = "";
                if (settings.isShowIds()) lab += transition.getId() + "-";
                lab += transition.getLabel() + "";

                GraphNode node = new GraphNode(petri.getId(), transition.getId(),
                    NodeStates.NOMINAL, NodeStates.NOMINAL, NodeType.PETRINET_TRANSITION, lab, transition);
                nodeMap.put(transition.getId(), node);


                transition.setId(transition.getId() + petri.getId());
                Node n = workingCanvasArea.addNode(transition.getId());
                n.addAttribute("ui.class", "PetriTransition");
                n.addAttribute("ui.PID", petri.getId());
                n.addAttribute("ui.label", lab);
                n.addAttribute("ui.GraphNode", node);
                nodeMapGS.put(transition.getId(), n);

                n.addAttribute("ui.owners", transition.getOwners());

                //This is for reuse of initial parelel process name for parrelel process editing
                int colonIndex1 = petri.getId().indexOf(":*");
                String intitialProcessName = petri.getId().substring(0, colonIndex1);


                n.addAttribute("ui.initialProcessName", intitialProcessName);
                petriTransitions.add(n);

            });

        for (PetriNetEdge edge : petri.getEdgesNotBlocked().values()) {



            String lab = "";
            if (settings.isShowIds()) lab += edge.getId() + "-";
            if (edge.getOptional()) {

                lab = "Opt";
                int i = edge.getOptionNum();
                if (i > 0) {
                    lab = lab + i;
                }
            }
            if (settings.isShowOwners()) {
                PetriNetPlace place;
                if (edge.getTo() instanceof PetriNetPlace) {
                    place = (PetriNetPlace) edge.getTo();
                } else {
                    place = (PetriNetPlace) edge.getFrom();
                }
                for (String o : (place).getOwners()) {
                    lab += ("." + o);
                }
            }

            String b;
            String a;
            if (edge.getGuard() != null) {
                b = edge.getGuard().getGuardStr();
                a = edge.getGuard().getAssStr();
            } else {
                b = "";
                a = "";
            }

            DirectedEdge nodeEdge = new DirectedEdge(b, lab, a, UUID.randomUUID().toString());


            Edge e;
            String pColour = "";

            if (edge.getFrom().getType().equals("PetriNetPlace")) {
                PetriNetPlace pnp = (PetriNetPlace) edge.getFrom();
                if (pnp.isStart()) {
                    int startValue = startToIntValue.get(pnp);
                    e = workingCanvasArea.addEdge(petri.getId() + startValue + "-" + edge.getTo().getId(), petri.getId() + startValue, edge.getTo().getId(), true);
                } else {
                    e = workingCanvasArea.addEdge(edge.getFrom().getId() + "-" + edge.getTo().getId(), edge.getFrom().getId(), edge.getTo().getId(), true);
                }


                String placeOwner = pnp.getOwners().first();
                pColour = ownerColours.get(placeOwner);

            } else {
                PetriNetPlace pnp = (PetriNetPlace) edge.getTo();
                if (pnp.isStart()) {
                    int startValue = startToIntValue.get(pnp);
                    e = workingCanvasArea.addEdge(edge.getFrom().getId() + "-" + petri.getId() + startValue, edge.getFrom().getId(), petri.getId() + startValue, true);
                } else {
                    e = workingCanvasArea.addEdge(edge.getFrom().getId() + "-" + edge.getTo().getId(), edge.getFrom().getId(), edge.getTo().getId(), true);
                }

                PetriNetTransition pnt = (PetriNetTransition) edge.getFrom();

                if (pnt.getOwners().size() == 1) {
                    String placeOwner = pnt.getOwners().first();
                    pColour = ownerColours.get(placeOwner);
                } else {
                    String placeOwner = pnp.getOwners().first();
                    pColour = ownerColours.get(placeOwner);
                }
            }

            e.addAttribute("ui.style", "fill-color: " + pColour + ";");


        }

        setSyningEdgeWight(null);


        this.processModelsOnScreenGraphNodeType.replaceValues(petri.getId(), nodeMap.values());
        this.processModelsOnScreenGSType.replaceValues(petri.getId(), nodeMapGS.values());

        if (interactionType.equals("autopetrirelation")) {
            handleAutoPetriRelation();
        }

        Collection<Node> nodes = workingCanvasArea.getNodeSet();
        Collection<Edge> edges = workingCanvasArea.getEdgeSet();


    }

    public void setNewVisualNodeType(String nodeType) {

        // todo switch


        if (nodeType.equals("AutoStart")) {
            addingAutoNodeStart = true;
        } else if (nodeType.equals("AutoNeutral")) {
            addingAutoNodeNeutral = true;
        } else if (nodeType.equals("AutoEnd")) {
            addingAutoNodeEnd = true;
        } else if (nodeType.equals("PetriPlaceStart")) {
            addingPetriPlaceStart = true;
        } else if (nodeType.equals("PetriPlaceNeutral")) {
            addingPetriPlaceNeutral = true;
        } else if (nodeType.equals("PetriPlaceEnd")) {
            addingPetriPlaceEnd = true;
        } else {
            addingPetriTransition = true;
        }


        //Not proud of this hack to force graph mouse listener to respond to mouse release from shape mouse listener:
        Robot robot = null;
        try {
            robot = new Robot();
        } catch (AWTException e) {
            e.printStackTrace();
        }
        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);
    }


    public void dropNode(int xOnScreen, int yOnScreen) {

        if (!addingAutoNodeStart && !addingAutoNodeNeutral && !addingAutoNodeEnd
            && !addingPetriPlaceStart && !addingPetriPlaceNeutral && !addingPetriPlaceEnd && !addingPetriTransition) {
            return;
        }


        Point3 gu = workingCanvasAreaView.getCamera().transformPxToGu(xOnScreen, yOnScreen);
        //workingCanvasAreaViewer.disableAutoLayout();
        latestNode = workingCanvasArea.addNode(String.valueOf(Math.random()));
        latestNode.setAttribute("xyz", gu.x, gu.y, 0);
        //workingLayout.freezeNode(latestNode.getId(), true);

        if (addingAutoNodeStart) {
            latestNode.addAttribute("ui.class", "AutoStart");
            addingAutoNodeStart = false;
        } else if (addingAutoNodeNeutral) {
            latestNode.addAttribute("ui.class", "AutoNeutral");
            addingAutoNodeNeutral = false;
        } else if (addingAutoNodeEnd) {
            latestNode.addAttribute("ui.class", "AutoEnd");
            addingAutoNodeEnd = false;
        } else if (addingPetriPlaceStart) {
            latestNode.addAttribute("ui.class", "PetriPlaceStart");
            addingPetriPlaceStart = false;
        } else if (addingPetriPlaceNeutral) {
            latestNode.addAttribute("ui.class", "PetriPlace");
            addingPetriPlaceNeutral = false;
        } else if (addingPetriPlaceEnd) {
            latestNode.addAttribute("ui.class", "PetriPlaceEnd");
            addingPetriPlaceEnd = false;
        } else if (addingPetriTransition) {
            latestNode.addAttribute("ui.class", "PetriTransition");
            addingPetriTransition = false;
        } else {
            System.out.println("doing nothing");
        }

        createdNodes.add(latestNode);
        nodeRecentlyPlaced = true;


    }

    public void setLatestNodeName(String newProcessNameValue) {
        latestNode.addAttribute("ui.label", newProcessNameValue);

        if (latestNode.getAttribute("ui.class").equals("PetriPlaceStart")) {
            workingCanvasArea.getNode(latestNode.getId()).addAttribute("ui.PID", newProcessNameValue);
        }
    }


    public void determineIfNodeClicked(int x, int y, int clickType) {


        //To handle the extra redundant "click" from the bot prevents unwanted node linking kinda shit implementation though
        if (nodeRecentlyPlaced) {
            nodeRecentlyPlaced = false;
            return;
        }


        if (interactionType.equals("create")) {
            GraphicElement ge = workingCanvasAreaView.findNodeOrSpriteAt(x, y);

            if (ge != null) {
                if (firstNodeClicked == null) {
                    firstNodeClicked = (Node) ge;
                    System.out.println("Selecting First Node: " + firstNodeClicked.getId());

                    if (clickType == 3) {
                        handleNodeDeletion();
                    } else {
                        firstNodeClass = firstNodeClicked.getAttribute("ui.class");

                        firstNodeClicked.removeAttribute("ui.class");
                        //handleProcessEditing(null);

                        if (firstNodeClass.equals("PetriTransition")) {
                            firstNodeClicked.addAttribute("ui.style", "fill-color: #ccff00; shape: box;");
                        } else {
                            firstNodeClicked.addAttribute("ui.style", "fill-color: #ccff00;");
                        }
                    }

                } else {
                    seccondNodeClicked = (Node) ge;
                    System.out.println("Selecting Seccond Node: " + seccondNodeClicked.getId());
                }

            } else {
                System.out.println("Node not Clicked");
                if(firstNodeClicked != null) {
                    firstNodeClicked.addAttribute("ui.class", firstNodeClass);
                }
                firstNodeClicked = null;
            }

            if (firstNodeClicked != null && seccondNodeClicked != null) {
                firstNodeClicked.addAttribute("ui.class", firstNodeClass);


                if (!createdNodes.contains(workingCanvasArea.getNode(firstNodeClicked.getId()))) {
                    handleProcessEditing(firstNodeClicked);
                } else if (!createdNodes.contains(workingCanvasArea.getNode(seccondNodeClicked.getId()))) {
                    handleProcessEditing(seccondNodeClicked);
                }

                doDrawEdge();

                firstNodeClicked = null;
                seccondNodeClicked = null;

               /* Thread t = new Thread() {
                    public void run() {
                        doDrawEdge();
                    }
                };

                t.start();
                try {
                    t.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }*/


               /* firstNodeClicked.addAttribute("ui.class", firstNodeClass);
                seccondNodeClicked.addAttribute("ui.class", seccondNodeClass);*/


            }
        } else {
            handleTokenGame(x, y);
        }
    }

    private void unsettingClicked() {
        firstNodeClicked = null;
        seccondNodeClicked = null;

    }

    private void handleTokenGame(int x, int y) {
        //Do Token Game
        GraphicElement ge = workingCanvasAreaView.findNodeOrSpriteAt(x, y);
        Node nodeSelected = (Node) ge;


        if (nodeSelected != null) {

            for (Node n : createdNodes) {
                System.out.println(n.getId());
                if (n.getId().equals(nodeSelected.getId())) {
                    return;
                }
            }


            String pid = nodeSelected.getAttribute("ui.PID");
            GraphNode gn = nodeSelected.getAttribute("ui.GraphNode");
            ProcessModelObject clk = gn.getRepresentedFeature();


            if (mappings != null && mappings.containsKey(pid)) {
                if ((clk instanceof PetriNetTransition)) {
                    PetriNetTransition pntClicked = ((PetriNetTransition) clk);
                    if (!pntClicked.getLabel().equals(Constant.DEADLOCK)) {
                        Multiset<PetriNetPlace> cm = CurrentMarkingsSeen.currentMarkingsSeen.get(pid);
                        Multiset<PetriNetPlace> newMarking;
                        List<Multiset<PetriNetPlace>> newMarkings;

                        if (TokenRule.isSatisfied(cm, pntClicked)) {
                            newMarkings = TokenRulePureFunctions.newMarking(cm, pntClicked);
                            newMarking = newMarkings.get(0);
                            CurrentMarkingsSeen.currentMarkingsSeen.put(pid, newMarking);

                        }
                    }
                    refreshTransitionColour();

                }
            }

        } else {
            CurrentMarkingsSeen.setCurrentMarkingsSeen(CurrentMarkingsSeen.getRootMarkings());
            refreshTransitionColour();
        }

        //Move Token

        Map<String, Set<String>> pnidToSetPlaceId = new TreeMap<>();
        for (String pid : CurrentMarkingsSeen.currentMarkingsSeen.keySet()) {
            Set<String> mk = CurrentMarkingsSeen.getIds(pid);
            pnidToSetPlaceId.put(pid, mk);
        }


        //Remove Last Tokens:
        removeLastTokens(nodeSelected);

        processModelsOnScreenGSType.asMap().forEach((key, value) -> {

            if (!key.contains("automata")) {

                for (Node vertex : value) {
                    GraphNode VertexGN = (GraphNode) vertex.getAttribute("ui.GraphNode");
                    if (VertexGN.getRepresentedFeature() instanceof PetriNetPlace &&
                        pnidToSetPlaceId.get(VertexGN.getProcessModelId())
                            .contains(((PetriNetPlace) VertexGN.getRepresentedFeature()).getId())
                        && !createdNodes.contains(vertex)) {

                        String petriHeadConversion = "";

                        if (workingCanvasArea.getNode(VertexGN.getRepresentedFeature().getId()) == null) {
                            System.out.println(VertexGN.getRepresentedFeature().getId());
                            Node n = (Node) graphNodeToHeadPetri.get(VertexGN.getRepresentedFeature());
                            petriHeadConversion = n.getId();
                            workingCanvasArea.getNode(petriHeadConversion).addAttribute("ui.petristart");
                        } else {
                            petriHeadConversion = VertexGN.getRepresentedFeature().getId();
                        }

                        System.out.println("k: " + key);

                        System.out.println("pnc: " + petriHeadConversion);

                        //Add New Tokens
                        workingCanvasArea.getNode(petriHeadConversion).addAttribute("ui.token");



                        System.out.println(workingCanvasArea.getNode(petriHeadConversion).getAttribute("ui.class").toString());
                        if (workingCanvasArea.getNode(petriHeadConversion).hasAttribute("ui.petristart")) {
                            workingCanvasArea.getNode(petriHeadConversion).removeAttribute("ui.class");
                            workingCanvasArea.getNode(petriHeadConversion).addAttribute("ui.class", "PetriPlaceTokenStart");
                        }

                        else if(workingCanvasArea.getNode(petriHeadConversion).getAttribute("ui.class").toString().equals("PetriPlace")) {
                            workingCanvasArea.getNode(petriHeadConversion).removeAttribute("ui.class");
                            workingCanvasArea.getNode(petriHeadConversion).addAttribute("ui.class", "PetriPlaceToken");
                        }

                        else if(workingCanvasArea.getNode(petriHeadConversion).getAttribute("ui.class").toString().equals("PetriPlaceEnd")) {
                            workingCanvasArea.getNode(petriHeadConversion).removeAttribute("ui.class");
                            workingCanvasArea.getNode(petriHeadConversion).addAttribute("ui.class", "PetriPlaceEndToken");
                        }

                    }
                }

            }
        });


    }

    private void removeLastTokens(Node nodeSelected) {

        Iterable<? extends Node> currentNodes = workingCanvasArea.getEachNode();

        currentNodes.forEach(node -> {

          /*  if(nodeSelected != null) {

                if ((node.getAttribute("ui.PID").toString().equals(nodeSelected.getAttribute("ui.PID").toString()))) {*/

            if (node.hasAttribute("ui.token")) {
                node.removeAttribute("ui.token");
                if (node.hasAttribute("ui.petristart")) {
                    node.addAttribute("ui.class", "PetriPlaceStart");
                    node.removeAttribute("ui.petristart");
                }

                else if(node.getAttribute("ui.class").toString().equals("PetriPlaceToken")) {
                    node.removeAttribute("ui.class");
                    node.addAttribute("ui.class", "PetriPlace");
                }

                else if(node.getAttribute("ui.class").toString().equals("PetriPlaceEndToken")) {
                    node.removeAttribute("ui.class");
                    node.addAttribute("ui.class", "PetriPlaceEnd");
                }
            }
          /*      }
            }*/

        });
    }

    private void refreshTransitionColour() {
        for (GraphNode gnt : processModelsOnScreenGraphNodeType.values()) {
            if (gnt.getRepresentedFeature() instanceof PetriNetTransition && !createdNodes.contains(workingCanvasArea.getNode(gnt.getRepresentedFeature().getId()))) {
                if (((PetriNetTransition) gnt.getRepresentedFeature()).getLabel().equals(Constant.DEADLOCK)) {
                    //workingCanvasArea.getNode(gnt.getRepresentedFeature().getId()).addAttribute("ui.style", "fill-color: rgb(0,100,255);");
                    //gnt.setNodeColor(NodeStates.NOMINAL);
                } else {
                    if (TokenRule.isSatisfied(CurrentMarkingsSeen.currentMarkingsSeen.get(gnt.getProcessModelId()),
                        ((PetriNetTransition) gnt.getRepresentedFeature()))) {
                        workingCanvasArea.getNode(gnt.getRepresentedFeature().getId()).removeAttribute("ui.class");
                        workingCanvasArea.getNode(gnt.getRepresentedFeature().getId()).addAttribute("ui.style", "fill-color: rgb(0,255,255); shape: box;");
                    } else {
                        workingCanvasArea.getNode(gnt.getRepresentedFeature().getId()).addAttribute("ui.class", "PetriTransition");
                    }
                }
            }

        }
    }

    private void doDrawEdge() {
        String firstNodeType = firstNodeClicked.getAttribute("ui.class");
        String seccondNodeType = seccondNodeClicked.getAttribute("ui.class");

        //Reject Building Processes Backwards

        if (!firstNodeType.equals("PetriPlaceStart") && firstNodeClicked.getInDegree() == 0 && !firstNodeType.contains("Auto")) {
            Platform.runLater(() ->
            {
                uic.reportError("backwardsPetriBuilding");
            });

            return;
        }


        //Reject transitions between petri and autos
        if ((firstNodeType.contains("Petri") && seccondNodeType.contains("Auto"))
            || (firstNodeType.contains("Auto") && seccondNodeType.contains("Petri"))) {
            Platform.runLater(() ->
            {
                uic.reportError("transitionBetweenAutoAndPetri");
            });
            return;
        }

        //If first and seccond node are not transition reject
        if (!firstNodeType.equals("PetriTransition") && !seccondNodeType.equals("PetriTransition")
            && (!firstNodeType.contains("Auto") && !seccondNodeType.contains("Auto"))) {
            Platform.runLater(() ->
            {
                uic.reportError("petriEdgeNoTransition");
            });
            return;
        }

        //If first and seccond are both transitions reject
        if (firstNodeType.equals("PetriTransition") && seccondNodeType.equals("PetriTransition")) {
            Platform.runLater(() ->
            {
                uic.reportError("petriEdgeBothTransitions");
            });
            return;
        }

        //Reject connections to a transisition if petri node is of the same process and transition already has entering connection
        if (firstNodeType.contains("Petri") && seccondNodeType.equals("PetriTransition")) {
            if (seccondNodeClicked.hasAttribute("ui.PID")) {
                if (firstNodeClicked.getAttribute("ui.PID").toString() == seccondNodeClicked.getAttribute("ui.PID").toString()) {
                    if (seccondNodeClicked.getEnteringEdgeSet().size() > 0) {
                        Platform.runLater(() ->
                        {
                            uic.reportError("petriSingleProcessTransitionMultipleEntries");
                        });
                        return;
                    }

                }
            }
        }

        //Reject braching on a transition for a single petri
        if (firstNodeType.equals("PetriTransition") && seccondNodeType.contains("Petri")) {

            if (!firstNodeClicked.hasAttribute("ui.PIDS") && !seccondNodeType.equals("PetriPlaceStart")) {

                Collection<Edge> firstNodeLeavingEdges = firstNodeClicked.getLeavingEdgeSet();

                //Cant understand ytf getleavingedgeset contains entering edges hence this:
                int actualLeavingEdgeCount = 0;
                for (Edge e : firstNodeLeavingEdges) {
                    Node target = e.getTargetNode();

                    if (target != firstNodeClicked) {
                        actualLeavingEdgeCount++;
                    }
                }

                if (actualLeavingEdgeCount > 0) {
                    Platform.runLater(() ->
                    {
                        uic.reportError("petriTransitionBranching");
                    });
                    return;
                }

            }
        }

        //Reject Transitions having more outgoing edges that incoming

        if(firstNodeType.equals("PetriTransition")){
            if(workingCanvasArea.getNode(firstNodeClicked.getId()).getOutDegree()+1 > workingCanvasArea.getNode(firstNodeClicked.getId()).getInDegree()){
                Platform.runLater(() ->
                {
                    uic.reportError("petriTransitionMoreOutGoingThanIn");
                });
                return;

            }

        }

        //Reject place having a different pid than from the new transition, different as need to differntiate between this and inner loops which would have same pid

        if (firstNodeType.equals("PetriTransition") && seccondNodeType.equals("PetriPlace") && workingCanvasArea.getNode(seccondNodeClicked.getId()).hasAttribute("ui.PID")){


            if(!workingCanvasArea.getNode(firstNodeClicked.getId()).getAttribute("ui.PID").toString()
                .equals(workingCanvasArea.getNode(seccondNodeClicked.getId()).getAttribute("ui.PID").toString())){
                Platform.runLater(() ->
                {
                    uic.reportError("syncingOnAPetriPlace");
                });
                return;
            }

        }




        Edge edge = workingCanvasArea.addEdge(firstNodeClicked.getId() + "-" + seccondNodeClicked.getId(), firstNodeClicked.getId(), seccondNodeClicked.getId(), true);

        //Label the Automata Edge (Irrelavnt for Petri as pertri labels are already defined)
        CountDownLatch latch = new CountDownLatch(1);
        if ((firstNodeType.contains("Auto") && seccondNodeType.contains("Auto"))) {
            Platform.runLater(() -> {
                String labelValue = uic.nameEdge();
                edge.addAttribute("ui.label", labelValue);
                latch.countDown();
            });

            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }

        if (!firstNodeType.contains("Auto")) {
            createdEdges.add(edge);
            doPostEdgeUpdates(edge);
        }
    }


    private ArrayList<String> getConnectedPIDS(Node tNode) {
        ArrayList<String> pids = new ArrayList<>();
        Collection<Edge> inGoingEdges = tNode.getEnteringEdgeSet();

        for (Edge e : inGoingEdges) {
            pids.add(e.getNode0().getAttribute("ui.PID"));
        }

        return pids;

    }

    private void doPostEdgeUpdates(Edge edge) {

        //Propogate first nodes pid to the seccond nodes pid, multiple attibutes with ui.PID possible to support "PIDS" ? wat



        if((workingCanvasArea.getNode(firstNodeClicked.getId()).getInDegree() == 1 || (workingCanvasArea.getNode(firstNodeClicked.getId()).getAttribute("ui.class") ).toString().contains("PetriPlace"))
            || firstNodeClicked.getAttribute("ui.class").equals("PetriPlaceStart")) {
            workingCanvasArea.getNode(seccondNodeClicked.getId()).addAttribute("ui.PID", workingCanvasArea.getNode(firstNodeClicked.getId()).getAttribute("ui.PID").toString());
        }


        //When a transition has multiple entering edges it means it is a parrelel transition denoted as PIDS (PID Plural)
        //So if a transition is not yet a PIDS ie when a seccond incomming edge recently added, then give it a PIDS attribute
        //Containing the PID of all incoming edges or if PIDS is already set then just re add existing edge PID with the new one
        if (seccondNodeClicked.getAttribute("ui.class").equals("PetriTransition") && seccondNodeClicked.getEnteringEdgeSet().size() > 1) {
            ArrayList<String> pids = new ArrayList<>();
            pids.add(firstNodeClicked.getAttribute("ui.PID"));
            if (workingCanvasArea.getNode(seccondNodeClicked.getId()).hasAttribute("ui.PIDS")) {
                pids.addAll(seccondNodeClicked.getAttribute("ui.PIDS"));
                workingCanvasArea.getNode(seccondNodeClicked.getId()).setAttribute("ui.PIDS", pids);
            } else {
                ArrayList<String> processes = getConnectedPIDS(workingCanvasArea.getNode(seccondNodeClicked.getId()));
                workingCanvasArea.getNode(seccondNodeClicked.getId()).addAttribute("ui.PIDS", processes);

                String parrelelCompositionName = determineParrelCompositionName(seccondNodeClicked);


                CountDownLatch latch = new CountDownLatch(1);
                if (parrelelCompositionName.isEmpty()) {
                    Platform.runLater(() -> {
                        String processValue = uic.nameParrelelProceses();
                        workingCanvasArea.getNode(seccondNodeClicked.getId()).addAttribute("ui.PIDSName", processValue);
                        latch.countDown();
                    });

                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                } else {
                    workingCanvasArea.getNode(seccondNodeClicked.getId()).addAttribute("ui.PIDSName", parrelelCompositionName);
                }


                //TEst this one day i hope:
                Collection<Edge> outgoingPids = workingCanvasArea.getNode(seccondNodeClicked.getId()).getLeavingEdgeSet();

                for (Edge e : outgoingPids) {
                    e.addAttribute("layout.weight", syncingTransitionWeightValue);
                }

            }
        }

        //The subsequent places of a PIDS transisition need specifying, because which process should they belong to?
        //Answer: let the user decide

        for (Node currentNode : createdNodes) {

            if(!currentNode.hasAttribute("processSet")) {

                if (currentNode.getAttribute("ui.class").equals("PetriTransition")) {
                    Collection<Edge> outGoingEdges = currentNode.getLeavingEdgeSet();
                    if (currentNode.hasAttribute("ui.PIDS")) {
                        ArrayList<String> allPIDS = currentNode.getAttribute("ui.PIDS");
                        ArrayList<String> selectedPIDS = new ArrayList<>();

                        //add existing pids to selected

                        for (Edge e : outGoingEdges) {
                            if (workingCanvasArea.getNode(e.getNode1().getId()).hasAttribute("ui.PID")) {
                                selectedPIDS.add(workingCanvasArea.getNode(e.getNode1().getId()).getAttribute("ui.PID").toString());
                            }
                        }


                        int pidsSize = allPIDS.size();
                        int eCounter = 0;

                        for (Edge e : outGoingEdges) {


                            if (!workingCanvasArea.getNode(e.getNode1().getId()).hasAttribute("processSet") && workingCanvasArea.getNode(e.getNode1().getId()).getOutDegree() == 0
                                && !workingCanvasArea.getNode(e.getNode1().getId()).hasAttribute("ui.PID")) {

                                if (eCounter < pidsSize - 1) {
                                    String petriType = e.getNode1().getAttribute("ui.class");
                                    workingCanvasArea.getNode(e.getNode1().getId()).removeAttribute("ui.class");

                                    String s = doShit(e.getNode1().getId());

                                    System.out.println(s);




                                    final CountDownLatch latchToWaitForJavaFx = new CountDownLatch(1);

                                    Platform.runLater(() -> {

                                        String selectedPID = (uic.doParelelProcessSpecifying(currentNode.getAttribute("ui.PIDS"), e.getNode1().getId()));


                                        workingCanvasArea.getNode(e.getNode1().getId()).addAttribute("ui.class", petriType);
                                        selectedPIDS.add(selectedPID);
                                        workingCanvasArea.getNode(e.getNode1().getId()).addAttribute("ui.PID", selectedPID);
                                        workingCanvasArea.getNode(e.getNode1().getId()).addAttribute("processSet");
                                        latchToWaitForJavaFx.countDown();
                                    });


                                    try {

                                        latchToWaitForJavaFx.await();
                                    } catch (InterruptedException e1) {
                                        e1.printStackTrace();
                                    }


                                } else {
                                    //Must choose remaining one
                                    for (String s : allPIDS) {
                                        if (!selectedPIDS.contains(s)) {
                                            String selectedPID = s;
                                            selectedPIDS.add(selectedPID);
                                            workingCanvasArea.getNode(e.getNode1().getId()).addAttribute("ui.PID", selectedPID);
                                            workingCanvasArea.getNode(e.getNode1().getId()).addAttribute("processSet");
                                            selectedPIDS.add(s);
                                        }
                                    }


                                }
                            }
                            eCounter++;
                        }
                    }
                }
            }
        }

        for (Node currentNode : createdNodes) {
            if(currentNode.hasAttribute("processSet")){
                currentNode.removeAttribute("processSet");
            }
        }


        unsettingClicked();

    }

    private String doShit(String id) {
        workingCanvasArea.getNode(id).addAttribute("ui.style", "fill-color: rgb(0,100,255);");
        return "done";
    }

    private String determineParrelCompositionName(Node seccondNodeClicked) {
        Iterator<Node> searcher = workingCanvasArea.getNode(seccondNodeClicked.getId()).getBreadthFirstIterator(false);
        String parrelCompositionName = "";

        while (searcher.hasNext()) {
            Node current = searcher.next();

            if (current.hasAttribute("ui.PIDS") && current.getId() != seccondNodeClicked.getId()) {
                parrelCompositionName = current.getAttribute("ui.PIDSName").toString();
            }
        }

        return parrelCompositionName;

    }

    private Boolean determineIfIndexedProcess(Iterator<Node> k) {

        Boolean indexed = false;
        while (k.hasNext()) {
            Node current = k.next();

            if(current.hasAttribute("ui.label")){
                if(current.getAttribute("ui.label").toString().contains("[")){
                    indexed = true;
                }
            }

        }

        return indexed;


    }

    private void handleProcessEditing(Node n) {

        Iterator<Node> l = workingCanvasArea.getNode(n.getId()).getBreadthFirstIterator(false);
        Boolean indexed = determineIfIndexedProcess(l);


        Iterator<Node> k = workingCanvasArea.getNode(n.getId()).getBreadthFirstIterator(false);


        if(!indexed) {

            ArrayList<Node> heads = new ArrayList<>();

            while (k.hasNext()) {
                Node current = k.next();

                if (!createdNodes.contains(current)) {
                    createdNodes.add(current);
                }

                if (current.getAttribute("ui.class").equals("PetriPlaceStart")) {
                    heads.add(current);
                }

            }


            for (Node hn : heads) {
                if (!hn.hasAttribute("ui.edited")) {
                    String[] owners = ownersTypeConverter(hn.getAttribute("ui.owners"), false);
                    hn.setAttribute("ui.label", hn.getAttribute("ui.label").toString().replaceAll(" ", ""));
                    ownersToPID.put(owners[0], hn.getAttribute("ui.label").toString().replaceAll(" ", ""));
                    hn.addAttribute("ui.style", "text-background-mode: rounded-box; text-background-color: red;");




                }
            }


            doPIDPropogationOfExistingProcess(heads.get(0));
        }
    }



    private String[] ownersTypeConverter(Object res, Boolean allOwners) {

        String[] owners = new String[20];
        int actualOwnersSize;

        if (!res.getClass().toString().equals("class java.lang.String")) {
            TreeSet<String> ownersTree = (TreeSet<String>) res;

            if (!allOwners) {
                owners[0] = ownersTree.first();
                actualOwnersSize = 1;
            } else {
                int c = 0;
                for (String s : ownersTree) {
                    owners[c] = s;
                    c++;
                }
                actualOwnersSize = c;
            }
        } else {
            String[] ownersString = res.toString().replace("[", "").replace("]", "").split(", ");

            if (!allOwners) {
                owners[0] = ownersString[0];
                actualOwnersSize = 1;
            } else {
                owners = ownersString;
                actualOwnersSize = ownersString.length;
            }
        }



        /*for(int i = 0; i < actualOwnersSize; i++){
            owners[i] = owners[i] + pid;
            i++;
        }*/


        return owners;
    }


    private void doPIDPropogationOfExistingProcess(Node headToAdd) {
        Iterator<Node> k = headToAdd.getBreadthFirstIterator(false);

        while (k.hasNext()) {
            Node current = k.next();

            if (current.hasAttribute("ui.owners")) {

                String[] owners = ownersTypeConverter(current.getAttribute("ui.owners"), true);


                if (current.getAttribute("ui.class").toString().contains("PetriPlace")) {

                    current.addAttribute("ui.PID", owners[0]);


                } else {
                    //Transition
                    if (current.getOutDegree() > 1) {
                        ArrayList<String> processes = new ArrayList<>();
                        processes.addAll(Arrays.asList(owners));


                        current.addAttribute("ui.PIDS", processes);
                    } else {
                        current.addAttribute("ui.PID", owners[0]);
                    }
                }
            }



        }


    }

    /**
     * @param modelLabel The name of the model process to be displayed / added to display.
     */
    public void addDisplayedModel(String modelLabel) {
        assert compiledResult.getProcessMap().containsKey(modelLabel);
        assert modelsInList.contains(modelLabel);
        processesChanged.add(modelLabel);
        processModelsToDisplay.add(modelLabel);
    }

    /* Guesing  dstr */
    public void removeDisplayedModel(String modelLabel) {
        assert compiledResult.getProcessMap().containsKey(modelLabel);
        assert modelsInList.contains(modelLabel);
        processesChanged.add(modelLabel);
        processModelsToDisplay.remove(modelLabel);
    }


    public void clearDisplayedNew() {
        processModelsToDisplay.clear();
        initalise(false);
    }

    public void addAllModels() {
        processModelsToDisplay.clear();
        processesChanged.addAll(compiledResult.getProcessMap().keySet());

        if (modelsInList != null) {
            processModelsToDisplay.addAll(modelsInList);
        }
    }

    public void addAllModelsNew() {
        processModelsToDisplay.clear();
        processesChanged.addAll(compiledResult.getProcessMap().keySet());

        if (modelsInList != null) {
            processModelsToDisplay.addAll(modelsInList);
        }
    }

    public void freezeAllCurrentlyDisplayedNew() {
        workingCanvasAreaViewer.disableAutoLayout();
        Iterable<? extends Node> k = workingCanvasArea.getEachNode();

        k.forEach(node -> {
                workingLayout.freezeNode(node.getId(), true);
            }
        );
    }

    public void unfreezeAllCurrentlyDisplayedNew() {
        workingCanvasAreaViewer.enableAutoLayout();
        Iterable<? extends Node> k = workingCanvasArea.getEachNode();

        k.forEach(node -> {
                workingLayout.freezeNode(node.getId(), false);
            }
        );
    }

    public void removeProcessModelNew(String selectedProcess) {

        HashSet<String> pids = new HashSet<>();

        if (selectedProcess != null && processModelsToDisplay.contains(selectedProcess)) {

            processModelsToDisplay.remove(selectedProcess);
            processesChanged.remove(selectedProcess);
            modelsInList.remove(selectedProcess);


            for (Node n : processModelsOnScreenGSType.get(selectedProcess)) {
                workingCanvasArea.removeNode(n);
                pids.add(n.getAttribute("ui.PID"));
            }
            processModelsOnScreenGSType.removeAll(selectedProcess);
            processModelsOnScreenGraphNodeType.removeAll(selectedProcess);
        }

        ArrayList<Node> nodesToRemove = new ArrayList<>();
        ArrayList<Edge> edgesToRemove = new ArrayList<>();

        for (Node n : createdNodes) {
            if (pids.contains(n.getAttribute("ui.PID"))) {

                if (workingCanvasArea.getNode(n.getId()) != null) {
                    workingCanvasArea.removeNode(n);
                }

                nodesToRemove.add(n);
            }
        }


        for (Node n : nodesToRemove) {
            createdNodes.remove(n);
        }

        for (Edge e : createdEdges) {
            for (Node n : nodesToRemove) {
                if (e.getNode0() == n || e.getNode1() == n) {
                    edgesToRemove.add(e);
                }
            }
        }


        for (Edge e : edgesToRemove) {
            createdEdges.remove(e);
        }


    }


    private Map<String, ProcessModel> getProcessMap() {
        return compiledResult.getProcessMap();
    }

    private void handleNodeDeletion() {

        System.out.println("dodelete");

       /* if (firstNodeClicked == null) {
            return;
        }


        for (Node n : createdNodes) {
            if (n.getId().equals(firstNodeClicked.getId())) {


                if (n.getOutDegree() != 0) {
                    Platform.runLater(() ->
                    {
                        uic.reportError("deletingNonLeaf");
                    });

                    firstNodeClicked = null;
                    return;
                }


                workingCanvasArea.removeNode(firstNodeClicked.getId());
                createdNodes.remove(firstNodeClicked);

            }
        }


        firstNodeClicked = null;*/


    }

    /**
     * Resets all graph varaibles and re-adds default blank state.
     */
    private void initalise(Boolean isLoaded) {
        processModelsToDisplay = new HashSet<>();
        processModelsOnScreenGSType = MultimapBuilder.hashKeys().hashSetValues().build();
        processModelsOnScreenGraphNodeType = MultimapBuilder.hashKeys().hashSetValues().build();

        String[] strings = {"black", "red", "yellow", "orange", "purple", "green"};
        pathColours.addAll(Arrays.asList(strings));

        //Reinitialise The Working Canvas area
        workingCanvasAreaContainer = new JPanel();
        workingCanvasAreaContainer.setLayout(new BorderLayout());

        createdNodes.clear();
        createdEdges.clear();

        if (!isLoaded) {
            workingCanvasArea = new MultiGraph("WorkingCanvasArea"); //field
        } else {
            createdNodes.addAll(workingCanvasArea.getNodeSet());
            createdEdges.addAll(workingCanvasArea.getEdgeSet());
        }







        workingCanvasArea.addAttribute("ui.stylesheet", getStyleSheet());
        workingCanvasArea.addAttribute("ui.quality");
        workingCanvasArea.addAttribute("ui.antialias");
        //workingCanvasArea.addAttribute("layout.stabilization-limit", 1);
        //workingCanvasArea.addAttribute("layout.force", 1);

        workingCanvasAreaViewer = new Viewer(workingCanvasArea, Viewer.ThreadingModel.GRAPH_IN_GUI_THREAD);

        workingLayout = Layouts.newLayoutAlgorithm();
        workingLayout.setForce(1); // 1 by default
        workingCanvasAreaViewer.enableAutoLayout(workingLayout);
        workingCanvasAreaView = workingCanvasAreaViewer.addDefaultView(false);
        PMM = new ProcessMouseManager();
        workingCanvasAreaView.addMouseListener(PMM);
        workingCanvasAreaView.getCamera().setViewPercent(1);
        workingCanvasAreaView.getCamera().setAutoFitView(true);

        Camera cam = workingCanvasAreaView.getCamera();
        cam.setBounds(0, 0, 0, 1, 1, 0);




        ((Component) workingCanvasAreaView).addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {

                e.consume();
                int i = e.getWheelRotation();
                double factor = Math.pow(1.25, i);
                Camera cam = workingCanvasAreaView.getCamera();

                zoom = cam.getViewPercent() * factor;

                if (zoom < 0) {
                    return;
                }

                Point2 pxCenter = cam.transformGuToPx(cam.getViewCenter().x, cam.getViewCenter().y, 0);
                Point3 guClicked = cam.transformPxToGu(e.getX(), e.getY());
                double newRatioPx2Gu = cam.getMetrics().ratioPx2Gu / factor;
                double x = guClicked.x + (pxCenter.x - e.getX()) / newRatioPx2Gu;
                double y = guClicked.y - (pxCenter.y - e.getY()) / newRatioPx2Gu;
                cam.setViewCenter(x, y, 0);


                cam.setViewPercent(zoom);
            }
        });

        workingCanvasAreaViewer.getDefaultView().setShortcutManager(new ShortcutManager() {

            private View view;

            @Override
            public void init(GraphicGraph graph, View view) {
                this.view = view;
                view.addKeyListener(this);
            }

            @Override
            public void release() {
                view.removeKeyListener(this);
            }

            @Override
            public void keyPressed(KeyEvent e) {
                int keyCode = e.getKeyCode();
                if (keyCode == KeyEvent.VK_DELETE) {
                    handleNodeDeletion();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                System.out.println("keyReleased!");
            }

            @Override
            public void keyTyped(KeyEvent e) {
                System.out.println("keyTyped!");
            }
        });



        /*Robot robot = null;
        try {
            robot = new Robot();
        } catch (AWTException e) {
            e.printStackTrace();
        }
        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);*/


        workingCanvasAreaContainer.add((Component) workingCanvasAreaView, BorderLayout.CENTER);
    }

    @Getter
    private static ModelView instance = new ModelView();

    /**
     * Enforcing Singleton.
     */
    private ModelView() {
        CompilationObservable.getInstance().addObserver(this);
        initalise(false);
    }

    public ArrayList<Node> getVisualCreatedPetris() {
        return createdNodes;
    }

    public void switchInteractionType(String type) {
        switch (type) {
            case "create":
                switchToCreateMode();
                break;
            case "token":
                switchToTokenMode();
                break;
            case "autopetrirelation":
                handleAutoPetriRelation();
                break;
            default:
                break;
        }

    }

    public void switchToCreateMode() {
        interactionType = "create";
        removeLastTokens(null);
        clearPetriTransitionHighlighting();
        resetAutoPetriRelations();

    }

    public void switchToTokenMode() {
        interactionType = "token";
        determineIfNodeClicked(Integer.MAX_VALUE, Integer.MAX_VALUE, 1);
        clearPetriTransitionHighlighting();
        resetAutoPetriRelations();
    }

    public void clearPetriTransitionHighlighting() {
        for (GraphNode gnt : processModelsOnScreenGraphNodeType.values()) {
            if (gnt.getRepresentedFeature() instanceof PetriNetTransition) {
                workingCanvasArea.getNode(gnt.getRepresentedFeature().getId()).addAttribute("ui.class", "PetriTransition");
            }
        }


    }

    public void handleAutoPetriRelation() {

        interactionType = "autopetrirelation";
        removeLastTokens(null);
        clearPetriTransitionHighlighting();




        if (interactionType.equals("autopetrirelation")) {
            autoPetriRelationModeEnabled = true;


            Multimap<String, GraphNode> sds = processModelsOnScreenGraphNodeType;

            Set<String> processes = new HashSet<>();



            for (String s : sds.keys()) {
                processes.add(s);
            }



            int i = 0;
            int j = 0;

            ArrayList<String> processesMatches = new ArrayList<>();

            for (String p1 : processes) {
                for (String p2 : processes) {
                    int colonIndex1 = p1.indexOf(":*");
                    int colonIndex2 = p2.indexOf(":*");

                    if (p1.substring(0, colonIndex1).equals(p2.substring(0, colonIndex2)) && i != j && !processesMatches.contains(p1)) {

                        processesMatches.add(p1);
                        processesMatches.add(p2);
                    }
                    j++;
                }

                i++;
                j = 0;
            }

            ArrayList<String> toRemove = new ArrayList<>();

            for (String s : processesMatches) {
                if (s.contains("petrinet")) {
                    toRemove.add(s);
                }
            }

            for (String s : toRemove) {
                processesMatches.remove(s);
            }



            for (String p : processesMatches) {
                Collection<Node> gsProcessA = processModelsOnScreenGSType.get(p);
                ArrayList<Edge> gsProcessEdges = new ArrayList<>();

                for (Node n : gsProcessA) {
                    Collection<Edge> leavingEdges = n.getLeavingEdgeSet();
                    gsProcessEdges.addAll(leavingEdges);
                }

                for (Edge e : gsProcessEdges) {
                    Node autoN = e.getNode1();
                    Collection<Node> gsProcessB = processModelsOnScreenGSType.get(p.replaceAll("automata", "petrinet"));
                    Node petriN = null;


                    for (Node n : gsProcessB) {
                        petriN = n;

                        if (n.hasAttribute("ui.label")) {
                            if (n.getAttribute("ui.label").equals(e.getAttribute("ui.label"))) {

                                //determineIfAutoNodeAndPetriTranIdentical(autoN, petriN);

                                petriN = n;

                                try {


                                    if (workingCanvasArea.getEdge(autoN.getId() + "-" + petriN.getId()) == null) {
                                        Edge eRelation = workingCanvasArea.addEdge(autoN.getId() + "-" + petriN.getId(), autoN, petriN, false);
                                        eRelation.addAttribute("ui.style", "shape: blob; fill-color: rgb(230,0,255);");
                                        petriAutoRelations.add(eRelation);
                                        setRelatingEdgeWight(null);
                                        break;


                                    }
                                } catch (IdAlreadyInUseException er) {
                                    petriN.removeAttribute("ui.class");
                                    petriN.addAttribute("ui.style", "fill-color: rgb(0,100,255);");
                                }
                            }
                        }
                    }
                }
            }




        } else {
            //Remove the relations
            //autoPetriRelationModeEnabled = false;
            resetAutoPetriRelations();

        }


    }

    private void resetAutoPetriRelations() {
        for (Edge e : petriAutoRelations) {
            workingCanvasArea.removeEdge(e);
        }
        petriAutoRelations.clear();
    }

    public Graph getGraph() {
        return workingCanvasArea;
    }

    public JPanel setLoadedGraph(MultiGraph loadedGraph) {
        workingCanvasArea = loadedGraph;
        initalise(true);

        return workingCanvasAreaContainer;

    }


    public HashMap<String, String> getOwnersToPIDMapping() {
        return ownersToPID;
    }

    public void setSyningEdgeWight(Double sync) {


        if (sync != null) {
            syncingTransitionWeightValue = sync;
        }


        //Reduce force on syncing transitions
        for (Node n : petriTransitions) {
            if (n.getOutDegree() > 1) {
                Iterable<Edge> edges = n.getEachEdge();
                edges.forEach(e -> e.addAttribute("layout.weight", syncingTransitionWeightValue));
            }
        }


        for (Node n : createdNodes) {
            if (n.getAttribute("ui.class").toString().equals("PetriTransition") && n.getOutDegree() > 1) {
                Iterable<Edge> edges = n.getEachEdge();
                edges.forEach(e -> e.addAttribute("layout.weight", syncingTransitionWeightValue));
            }
        }

    }

    public void setRelatingEdgeWight(Double relatingWeight) {

        if (relatingWeight != null) {
            relatingWeightValue = relatingWeight;
        }

        for (Edge eRelation : petriAutoRelations) {
            eRelation.addAttribute("layout.weight", relatingWeightValue);

        }
    }

    public void setGraphWeight(Double weight) {
        workingLayout.setForce(weight);

        workingLayout.shake();
    }



    private String getStyleSheet() {

        Boolean trans = false;
        String transString = "";

        if(trans){
            transString = "88";
        }

        return "node {" +
            "text-size: 20;" +
            "text-color: black; " +
            "text-background-color: black;" +
            "text-alignment: above;" +
            "text-style: normal;" +
            "size: 20px; " +
            "fill-mode: gradient-horizontal;" +
            "shadow-color: #000000, #ffffff;" +
            "}" +
            "node.AutoStart {" +
            "fill-color: #1F9F06" + transString + ";" +
            "shape: box;" +
            "}" +
            "node.AutoNeutral {" +
            "fill-color: #b8b4b4" + transString + ";" +
            "shape: box;" +
            "}" +
            "node.AutoEnd {" +
            "fill-color: #5c0a04" + transString + ";" +
            "shape: box;" +
            "}" +
            "edge.autoLoop {" +
            "text-alignment: above;" +
            "}" +
            "edge {" +
            "fill-color: black;" +
            "text-size: 20;" +
            "arrow-shape: arrow;" +
            "}" +
            "graph {" +
            "fill-color: white;" +
            "}" +
            "node.PetriPlace {" +
            "fill-color: gray;" +
            /*"text-visibility-mode: hidden;" +*/
            "}" +
            "node.PetriPlaceStart {" +
            "fill-color: #0d4503;" +
            "}" +
            "node.PetriPlaceEnd {" +
            "fill-color: red;" +
            "text-visibility-mode: hidden;" +
            "}" +
            "node.PetriTransition {" +
            "shape: box; " +
            "fill-color: gray;" +
            "}" +
            "node.highlight {" +
            "fill-color: red;" +
            "}" +
            "node.PetriPlaceToken {" +
            "fill-color: black;" +
            "size: 10px; " +
            "shadow-mode: plain;" +
            "shadow-offset: 0;" +
            "shadow-width: 10;" +
            "shadow-color: gray; " +
            "}" +
            "node.HighlightedTransition {" +
            "shape: box; " +
            "fill-color: #ccff00;" +
            "}" +
            "node.HighlightedNonTransition {" +
            "fill-color: #ccff00;" +
            "}" +
            "node.PetriPlaceTokenStart {" +
            "fill-color: black;" +
            "size: 10px; " +
            "shadow-mode: plain;" +
            "shadow-offset: 0;" +
            "shadow-width: 10;" +
            "shadow-color: #0d4503; " +
            "}" +
            "node.PetriPlaceEndToken {" +
            "fill-color: black;" +
            "size: 10px; " +
            "shadow-mode: plain;" +
            "shadow-offset: 0;" +
            "shadow-width: 10;" +
            "shadow-color: red; " +
            "}" +
            "node.PetriPlaceInnerStart {" +
            "fill-color: red;" +
            "}"


            ;



    }



}



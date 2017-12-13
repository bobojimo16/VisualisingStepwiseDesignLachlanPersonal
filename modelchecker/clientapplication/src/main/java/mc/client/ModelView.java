package mc.client;


import edu.uci.ics.jung.algorithms.layout.DAGLayout;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.control.*;
import edu.uci.ics.jung.visualization.decorators.EdgeShape;
import javafx.embed.swing.SwingNode;
import javafx.geometry.Bounds;
import lombok.Getter;
import lombok.Setter;
import mc.client.graph.AutomataBorderPaintable;
import mc.client.graph.DirectedEdge;
import mc.client.graph.GraphNode;
import mc.client.graph.NodeStates;
import mc.client.ui.DoubleClickHandler;
import mc.client.ui.SeededRandomizedLayout;
import mc.client.ui.SpringlayoutBase;
import mc.compiler.CompilationObject;
import mc.compiler.CompilationObservable;
import mc.compiler.OperationResult;
import mc.process_models.ProcessModel;
import mc.process_models.ProcessType;
import mc.process_models.automata.Automaton;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static java.util.stream.Collectors.toSet;

/**
 * Created by bealjaco on 29/11/17.
 */
public class ModelView implements Observer{

    private Graph<GraphNode,DirectedEdge> graph;

    private Layout<GraphNode,DirectedEdge> layout; // So the user can independantly freeze automata.

    private  VisualizationViewer<GraphNode,DirectedEdge> vv;
    private Bounds windowSize;

    private DoubleClickHandler massSelect;

    private SeededRandomizedLayout layoutInitalizer;

    private Set<String> automataToDisplay;
    private SortedSet<String> visibleAutomata;
    private Map<String,Set<GraphNode>> processModels;


    private CompilationObject compiledResult;

    private static final Font sourceCodePro;

    @Setter
    private Consumer<Collection<String>> listOfAutomataUpdater;
    @Setter
    private BiConsumer<List<OperationResult>,List<OperationResult>> updateLog;



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
        if(!(arg instanceof CompilationObject))
            throw new IllegalArgumentException("arg object was not of type compilationObject");

        compiledResult = (CompilationObject) arg;
        visibleAutomata = new TreeSet<>(getProcessMap().entrySet().stream()
                .filter(e -> e.getValue().getProcessType() != ProcessType.AUTOMATA ||
                        ((Automaton)e.getValue()).getNodes().size() <= 40)
                .map(Map.Entry::getKey)
                .collect(toSet()));

        //remove processes marked at skipped and too large models to display
        listOfAutomataUpdater.accept(visibleAutomata);

        updateLog.accept(compiledResult.getOperationResults(),compiledResult.getEquationResults());
    }

    /**
     * A method to update the graph that is displayed
     *
     * @return the graph component that is displayed
     */
    public VisualizationViewer<GraphNode,DirectedEdge> updateGraph(SwingNode s) {
        if(compiledResult == null)
            return new VisualizationViewer<>(new DAGLayout<>(new DirectedSparseGraph<>()));

        layoutInitalizer.setDimensions(new Dimension((int) s.getBoundsInParent().getWidth(), (int) s.getBoundsInParent().getHeight()));

        compiledResult.getProcessMap().keySet().stream()
                .filter(automataToDisplay::contains)
                .map(compiledResult.getProcessMap()::get)
                .filter(Objects::nonNull)
                .forEach(this::addProcess);

        massSelect.updateProcessModelList(processModels);

        if(windowSize == null || !windowSize.equals(s.getBoundsInParent())) {
            windowSize = s.getBoundsInParent();
            layout.setSize(new Dimension((int) windowSize.getWidth(), (int) windowSize.getHeight()));
        }

        // if the font was imported successfully, set the font (the standard font does not display greek symbols
        // (i.e. tau and delta events)
        if(sourceCodePro!=null) {
            vv.getRenderContext().setEdgeFontTransformer(e -> sourceCodePro);
            vv.getRenderContext().setVertexFontTransformer(e -> sourceCodePro);
        }

        //set the colour of the nodes
        vv.getRenderContext().setVertexFillPaintTransformer(
                node -> NodeStates.valueOf(node.getNodeTermination().toUpperCase()).getColorNodes());


        //autoscale the graph to fit in the display port
        vv.setPreferredSize(new Dimension((int)windowSize.getWidth(),(int)windowSize.getHeight()));

        //This draws the boxes around the automata
        vv.addPreRenderPaintable(new AutomataBorderPaintable(vv,this.processModels));

        return vv;
    }

    private void addProcess(ProcessModel p){
        if(processModels.containsKey(p.getId())) // Dont re-add things that are already there...
            return;

        switch (p.getProcessType()){
            case AUTOMATA:
                addAutomata((Automaton)p);
                break;
            case PETRINET:
                //TODO
                break;
        }
    }
    /**
     * Add an individual automata to the graph
     * @param automata the automata object
     */
    private void addAutomata(Automaton automata){
        //make a new "parent" object for the children to be parents of
        Map<String,GraphNode> nodeMap = new HashMap<>();

        //add all the nodes to the graph
        automata.getNodes().forEach(n -> {
            String nodeTermination = "NOMINAL";
            if(n.isStartNode())
                nodeTermination = "START";
            if(n.isTerminal())
                nodeTermination = n.getTerminal();
            nodeTermination = nodeTermination.toLowerCase();

            //Make sure we are using a human reable label, the parallel compositions fill Id with long strings.
            String nodeLabel = (n.getId().contains("||"))? Integer.toString(n.getLabelNumber()) : n.getId();

            String splitTokens[] = nodeLabel.split("\\.");
            nodeLabel = splitTokens[splitTokens.length-1]; //Remove junk in the label, otherwise it ends up as Test.n1, we only need n1



            GraphNode node = new GraphNode(automata.getId(),nodeLabel,nodeTermination);
            nodeMap.put(n.getId(),node);

            graph.addVertex(node);
        });


        //add the edges to the graph
        automata.getEdges().forEach(e -> {
            GraphNode to   = nodeMap.get(e.getTo().getId());
            GraphNode from = nodeMap.get(e.getFrom().getId());
            graph.addEdge(new DirectedEdge(e.getLabel(),UUID.randomUUID().toString()),from,to);
        });

        this.processModels.put(automata.getId(),new HashSet<>(nodeMap.values()));
    }

    public void addDisplayedAutomata(String modelLabel) {
        assert compiledResult.getProcessMap().containsKey(modelLabel);
        assert visibleAutomata.contains(modelLabel);
        automataToDisplay.add(modelLabel);

    }

    public void clearDisplayed() {
        automataToDisplay.clear();
        initalise();

    }

    public void addAllAutomata() {
        automataToDisplay.clear();

        if(visibleAutomata != null)
            automataToDisplay.addAll(visibleAutomata);
    }

    public void freezeAllCurrentlyDisplayed() {
        if(layout != null ) {
            for(String processModeName : processModels.keySet())
                for(GraphNode vertexToLock : processModels.get(processModeName))
                    layout.lock(vertexToLock, true);
        }
    }

    public void unfreezeAllCurrentlyDisplayed() {
        if(layout != null ) {
            for(String processModeName : processModels.keySet())
                for(GraphNode vertexToLock : processModels.get(processModeName))
                    layout.lock(vertexToLock, false);
        }
    }

    public void freezeProcessModel(String automataLabel) {
        if(layout != null &&  automataToDisplay.contains(automataLabel)) {

            for(GraphNode vertexToLock : processModels.get(automataLabel)) {
                layout.lock(vertexToLock, true);
            }
        }
    }

    public void unfreezeProcessModel(String automataLabel) {
        if(layout != null &&  automataToDisplay.contains(automataLabel)) {
            for(GraphNode vertexToLock : processModels.get(automataLabel)) {
                layout.lock(vertexToLock, false);
            }
        }
    }

    public Map<String, ProcessModel> getProcessMap() {
        return  compiledResult.getProcessMap();
    }


    private void initalise()  {
        automataToDisplay = new HashSet<>();

        layoutInitalizer = new SeededRandomizedLayout();

        graph = new DirectedSparseGraph<>();

        //apply a layout to the graph
        layout = new SpringlayoutBase<>(graph);
        ((SpringlayoutBase)layout).setStretch(0.8);
        ((SpringlayoutBase)layout).setRepulsionRange(1000);

        layout.setInitializer(layoutInitalizer);

        vv = new VisualizationViewer<>(layout);

        vv.getRenderingHints().remove( //As this seems to be very expensive in jung
                RenderingHints.KEY_ANTIALIASING);

        //create a custom mouse controller (both movable, scalable and manipulatable)
        PluggableGraphMouse gm = new PluggableGraphMouse();
        gm.add(new TranslatingGraphMousePlugin(MouseEvent.BUTTON2_MASK));
        gm.add(new TranslatingGraphMousePlugin(MouseEvent.BUTTON3_MASK));
        gm.add(new ScalingGraphMousePlugin(new CrossoverScalingControl(),0,1.1f,0.9f));
        gm.add(new PickingGraphMousePlugin<>());

        vv.setGraphMouse(gm);

        massSelect = new DoubleClickHandler(processModels, vv);
        vv.addGraphMouseListener(massSelect);


        //label the nodes
        vv.getRenderContext().setVertexLabelTransformer(GraphNode::getNodeId);
        vv.getRenderContext().setEdgeLabelTransformer(DirectedEdge::getLabel);

        // Sets edges as lines
        vv.getRenderContext().setEdgeShapeTransformer(EdgeShape.line(graph));



        processModels = new HashMap<>();

    }


    @Getter
    private static ModelView instance = new ModelView();

    /**
     * Enforcing Singleton
     */
    private ModelView(){
        CompilationObservable.getInstance().addObserver(this);
        initalise();
    }

    //register font
    static {
        Font source;
        try {
            source = Font.createFont(Font.TRUETYPE_FONT,
                    ModelView.class.getResourceAsStream("/clientres/SourceCodePro-Bold.ttf"))
                    .deriveFont(15f);
        } catch (FontFormatException | IOException e) {
            source = null;
        }
        sourceCodePro = source;
    }
}

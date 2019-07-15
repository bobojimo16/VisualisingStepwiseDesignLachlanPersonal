package mc.client.ui;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.TreeMultiset;
import edu.uci.ics.jung.algorithms.layout.GraphElementAccessor;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.picking.PickedState;
import mc.client.graph.DirectedEdge;
import mc.client.graph.GraphNode;
import mc.client.graph.NodeStates;
import mc.client.graph.NodeType;
import mc.compiler.token.Token;
import mc.processmodels.MappingNdMarking;
import mc.processmodels.ProcessModelObject;
import mc.processmodels.automata.AutomatonNode;
import mc.processmodels.conversion.TokenRule;
import mc.processmodels.petrinet.components.PetriNetPlace;
import mc.processmodels.petrinet.components.PetriNetTransition;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.stream.Collectors;
import java.awt.*;

/**
 * Created by smithjord3 on 12/12/17.
 */
public class CanvasMouseListener implements MouseListener {
  private Map<String, MappingNdMarking> mappings;
  private Multimap<String, GraphNode> processModelVertexes;
  private VisualizationViewer<GraphNode, DirectedEdge> vv;

  private Map<GraphNode, NodeStates> currentlyColored = new HashMap<>();
  private Map<String, Multiset<PetriNetPlace>> currentMarking = new TreeMap<>();


  private String currentMarkingToString(String pid) {
    return markingToString(currentMarking.get(pid));

  }

  private String markingToString(Multiset<PetriNetPlace> m) {
    StringBuilder sb = new StringBuilder();
    m.stream().forEach(x -> sb.append(x.getId() + ", "));
    return sb.toString();
  }

  private String markingToString(Set<PetriNetPlace> m) {
    StringBuilder sb = new StringBuilder();
    m.stream().forEach(x -> sb.append(x.getId() + ", "));
    return sb.toString();
  }

  public CanvasMouseListener(Multimap<String, GraphNode> processModelVertexes_, VisualizationViewer<GraphNode, DirectedEdge> vv_,
                             Map<String, MappingNdMarking> nodeAndMarkingMappings,
                             Map<String, Multiset<PetriNetPlace>> currentMarking_) {
    processModelVertexes = processModelVertexes_;
    vv = vv_;
    currentMarking = currentMarking_;
    mappings = nodeAndMarkingMappings;
  }

  public void updateProcessModelList(Multimap<String, GraphNode> processModels) {
    processModelVertexes = processModels;
  }

  /**
   * Invoked when the mouse button has been clicked (pressed
   * and released) on a component.
   * We need to store old Color and reinstate but only for Place
   * as Transition has color changed by MotionListener
   */
  @Override
  public void mouseClicked(MouseEvent e) {
    System.out.println("mouseClick");
    //System.out.println(mappings.keySet().stream().map(x -> x + "-- " + mappings.get(x).toString()).collect(Collectors.joining(", ")));
    System.out.println("pMV " + processModelVertexes.keySet().stream().map(x -> x + processModelVertexes.get(x).stream().map(y -> y.getNodeId()).collect(Collectors.joining(", "))).collect(Collectors.joining("\n ")));
    GraphNode currentNodeClicked = getVertex(e.getPoint());
    MappingNdMarking thisMapping;
    if (currentNodeClicked != null) {
      String pid = currentNodeClicked.getProcessModelId();
      System.out.println("CLICKED " + currentNodeClicked.toString());
      ProcessModelObject clk = currentNodeClicked.getRepresentedFeature();
      if (!(clk instanceof PetriNetTransition)) {
        //System.out.println("Clicked on "+ currentNodeClicked.getNodeId());
        // if (!currentlyColored.containsKey(currentNodeClicked)) { // If we've clicked on a new node
        for (GraphNode currentColoredNode : currentlyColored.keySet()) {
          currentColoredNode.setNodeColor(currentlyColored.get(currentColoredNode)); // Reset the previous to unselected state
          System.out.println("ReColor to " + currentColoredNode.toString());
        }
        currentlyColored.clear();  // contains Places and Nodes
        //  }
        currentlyColored.put(currentNodeClicked, currentNodeClicked.getNodeColor());// Store the node, and the state
        currentNodeClicked.setNodeColor(NodeStates.SELECT); // Before setting it with the selected state
        System.out.println("currentlyColored - " + currentNodeClicked.getNodeId() + " " + currentNodeClicked.getNodeColor());

      }
      System.out.println("Id " + pid);
      System.out.println("\n clk " + clk.getId());
      //System.out.println(thisMapping.toString());
      if (mappings!=null && mappings.containsKey(pid)) {
        thisMapping = mappings.get(pid);
        if (!(clk instanceof PetriNetTransition)) {
          if (thisMapping != null) {
            //If this node/place has a mapping associated with it select those also.
            Collection<GraphNode> vertexes = vv.getGraphLayout().getGraph().getVertices();

            if (clk instanceof AutomatonNode) {
              //Node
              Map<AutomatonNode, Multiset<PetriNetPlace>> mapping = thisMapping.getNodeToMarking();
              //System.out.println("A->P " + MappingNdMarking.n2m2String(mapping));
              for (PetriNetPlace place : mapping.get(clk)) {
                // N^2, might be a problem for larger displays
                //System.out.println("place " + place.getId());
                for (GraphNode g : vertexes) {
                  //System.out.println("Vertex " +g.getRepresentedFeature().getId());
                  if (place.getId() == g.getRepresentedFeature().getId()) {
                    //System.out.println("A " + g.getNodeId());
                    currentlyColored.put(g, g.getNodeColor());
                    g.setNodeColor(NodeStates.SELECT);
                    System.out.println("currentlyColored A " + g.getNodeId() + " " + g.getNodeColor());
                    break;
                  }
                }
              }
            } else if (clk instanceof PetriNetPlace) {
              //Place
              Map<Multiset<PetriNetPlace>, AutomatonNode> mapping = thisMapping.getMarkingToNode();
              String out =
                mapping.keySet().stream().map(x -> "{" + x.stream().map(y -> y.getId()).
                  collect(Collectors.joining(", ")) + "} ->" +
                  mapping.get(x).getId()).collect(Collectors.joining("\n"));
              //System.out.println("P->A \n" + out);
              for (Multiset<PetriNetPlace> marking : mapping.keySet()) {
                Set<String> markingId = marking.stream().map(x -> x.getId()).collect(Collectors.toSet());
                //System.out.println("marking " + marking.stream().map(x -> x.getId()).collect(Collectors.joining(", ")));
                if (markingId.contains(clk.getId())) {
                  //System.out.println("found " + clk.getId());
                  for (GraphNode g : vertexes) {
                    if (mapping.get(marking) == g.getRepresentedFeature()) {
                      currentlyColored.put(g, g.getNodeColor());
                      System.out.println("currentlyColored P " + g.getNodeId() + " " + g.getNodeColor());
                      g.setNodeColor(NodeStates.SELECT);
                      break;
                    }
                  }
                }
              }
            }
          }

        } else {
          // PetriNetTransition
          PetriNetTransition pntClicked = ((PetriNetTransition) clk);
          System.out.println("In Net " + pid + " From node " + pntClicked.getId() + " " + pntClicked.getLabel());
          Multiset<PetriNetPlace> cm = currentMarking.get(pid);
          Multiset<PetriNetPlace> newMarking;
          if (TokenRule.isSatisfied(cm, pntClicked)) {
            newMarking = TokenRule.newMarking(cm, pntClicked);
            currentMarking.put(pid, newMarking);
          }
          /*  Set<PetriNetTransition> satisfiedTrans =
              TokenRule.satisfiedTransitions(cm);
            if (satisfiedTrans.contains(pntClicked)) {
              newMarking = TokenRule.newMarking(cm, pntClicked);
              currentMarking.put(pid, newMarking);
            }
            */
          //System.out.println("current " + currentMarkingToString(pid));
        }
      }


      if (processModelVertexes.containsKey(pid)) {
        PickedState<GraphNode> pickedVertexState = vv.getPickedVertexState(); // The graph has 'picking' support.
        // So we can just add them to the picked list,
        // and let pickingGraphMousePlugin deal with it
        processModelVertexes.get(pid).stream()
          .filter(v -> v != currentNodeClicked)
          .forEach(v -> pickedVertexState.pick(v, true));

      }

    } else { // IF we've clicked, but it isnt on a node
      for (GraphNode currentColoredNode : currentlyColored.keySet()) {
        if (currentColoredNode.getRepresentedFeature() instanceof PetriNetTransition)  continue;
        System.out.println("blank " + currentColoredNode.toString());
        // if (currentColoredNode.getRepresentedFeature() instanceof PetriNetPlace)
        currentColoredNode.setNodeColor(currentlyColored.get(currentColoredNode));
        System.out.println("      " + currentColoredNode.toString());
      }
      // Reset the previous to unselected Place
      currentlyColored.clear();
      for (GraphNode gn : processModelVertexes.values()) {
        if (gn.getRepresentedFeature() instanceof PetriNetTransition){
          gn.setNodeColor(NodeStates.NOMINAL);
        }
      }
    }
    // needs to be here for Transitions
    //System.out.println("DoubleClickHandler  mappings");
    //System.out.println(mappings.keySet().stream().map(x->"\n"+x+"\n-- "+mappings.get(x).toString()).collect(Collectors.joining(", ")));

  }

  /**
   * Invoked when a mouse button has been pressed on a component.
   */
  @Override
  public void mousePressed(MouseEvent e) {
  }

  /**
   * Invoked when a mouse button has been released on a component.
   */
  @Override
  public void mouseReleased(MouseEvent e) {
  }

  /**
   * Invoked when the mouse enters a component.
   */
  @Override
  public void mouseEntered(MouseEvent e) {
  /*
    enteredNode = getVertex(e.getPoint());
    //System.out.println("Entered ");
    if (enteredNode != null) {
      //System.out.println("Entered Type " + enteredNode.getType());
      if (enteredNode.getType().equals(NodeType.PETRINET_TRANSITION)) {
        //System.out.println("Entered " + enteredNode.getRepresentedFeature().getId());
        enteredColour = enteredNode.getNodeColor();
        enteredNode.setNodeColor(NodeStates.SELECT);
      }
    } */
  }

  /**
   * Invoked when the mouse exits a component.
   * Component as in Button checkbox not area on canvas
   */
  @Override
  public void mouseExited(MouseEvent e) {
   /* //System.out.println("Exited");
    if (enteredNode != null) {
      if (enteredNode.getType().equals(NodeType.PETRINET_TRANSITION)) {
        //System.out.println("Exited " + enteredNode.getRepresentedFeature().getId());
        enteredNode.setNodeColor(enteredColour);
      }
    }
    */
  }

  /**
   * Transform the point to the coordinate system in the
   * VisualizationViewer, then use either PickSuuport
   * (if available) or Layout to find a Vertex
   *
   * @return The vertex that was clicked on
   */
  private GraphNode getVertex(Point2D point) {
    // adjust for scale and offset in the VisualizationViewer
    //vv.getRenderContext().getBasicTransformer().inverseViewTransform(point);
    GraphElementAccessor<GraphNode, DirectedEdge> pickSupport = vv.getPickSupport();
    Layout<GraphNode, DirectedEdge> layout = vv.getGraphLayout();
    GraphNode v = null;
    if (pickSupport != null) {
      v = pickSupport.getVertex(layout, point.getX(), point.getY());
    }
    return v;
  }
}

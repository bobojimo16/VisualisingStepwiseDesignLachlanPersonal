package mc.client;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class VisualPetriToProcessCodeHelper {
    private String cumulativeProcessCode;
    private Node currentPetriHead;

    public String doConversion(ArrayList<Node> visualCreatedProcesses) {
        clearVisited(visualCreatedProcesses);
        ArrayList<Node> HeadPetriNodes = new ArrayList<>();
        cumulativeProcessCode = "";


        //Remove Nodes that arn't Petri Head Nodes
        for (Node n : visualCreatedProcesses) {
            if (n.getAttribute("ui.class").equals("PetriPlaceStart")) {
                HeadPetriNodes.add(n);
            }
        }

        //For each head petri node perform dfsrecursive search
        for (Node n : HeadPetriNodes) {
            currentPetriHead = n;
            String petriID = n.getAttribute("ui.label");
            cumulativeProcessCode += petriID.toUpperCase() + " = ";
            doDFSRecursive(n);
        }

        cumulativeProcessCode+=".";
        return cumulativeProcessCode;
    }

    private void clearVisited(ArrayList<Node> visualCreatedProcesses) {
        for(Node n: visualCreatedProcesses){
            n.removeAttribute("visited");
        }
    }


    private void doDFSRecursive(Node n) {
        boolean isCyclic = false;

        if(n.hasAttribute("visited")){
            System.out.println("visited");
        }

        n.setAttribute("visited", "true");

        if(n.getAttribute("ui.class").equals("PetriPlace")){
            isCyclic = determinePlaceIsCyclic(n);
        }

        if(isCyclic){
            //Inner process loop
            String innerProcessName = currentPetriHead.getAttribute("ui.label").toString().toUpperCase() + "InnerProcess";
            cumulativeProcessCode += innerProcessName + ",\n" + innerProcessName + " = ";
            n.setAttribute("ui.label", innerProcessName);
            n.setAttribute("ui.class", "PetriPlaceInnerStart");
        }

        //When Leaf
        if (n.getAttribute("ui.class").equals("PetriPlaceEnd")) {
            //leaf
            String nextValue = doValueEvaluation(n);
            return;
        }

        //Iterate through this nodes leaving edges and store them in edges list
        Iterator<? extends Edge> k = n.getLeavingEdgeIterator();
        ArrayList<Edge> edges = new ArrayList<>();
        for (Iterator<? extends Edge> it = k; it.hasNext(); ) {
            edges.add(it.next());
        }

        // Open bracket for each branch
        if(edges.size() > 1){
            cumulativeProcessCode += "(";
        } else {
            System.out.println(edges.size());
        }

        for(int i = 0; i < edges.size(); i++){

            Node outGoingNode = edges.get(i).getNode1();
            //Node outGoing = current;

            //if node is not a place then get its value
            if(!outGoingNode.getAttribute("ui.class").equals("PetriPlace")) {
                String nextValue = doValueEvaluation(outGoingNode);
                cumulativeProcessCode += nextValue;
            }

            //And repeat this process for outgoingnode

            if(!outGoingNode.hasAttribute("visited")) {
                doDFSRecursive(outGoingNode);
            } else {
                //Loop detected
                System.out.println("loopy");

                if(outGoingNode == currentPetriHead) {
                    //inner process loop
                    cumulativeProcessCode += outGoingNode.getAttribute("ui.label").toString().toUpperCase();
                } else {

                }


                //System.out.println(cumulativeProcessCode);

            }

            //Place a pipe for each child node of parent that is not the final child node in order i.e dont place pipe on
            //final child
            if(i < edges.size() - 1 ) {
                if(n.hasAttribute("ui.label") ){
                    System.out.println((String)n.getAttribute("ui.label"));
                }
                cumulativeProcessCode += " | ";
            }
        }

        // Close bracket for each branch
        if(edges.size() > 1){
            cumulativeProcessCode += ")";
        }
    }

    private boolean determinePlaceIsCyclic(Node n) {
        Collection<Edge> incommingEdges = n.getEnteringEdgeSet();
        return incommingEdges.size() > 1;
    }

    private String doValueEvaluation(Node n) {
        String value = "";
        if (n.getAttribute("ui.class").equals("PetriTransition")) {
            value = n.getAttribute("ui.label") + " -> ";
        } else if (n.getAttribute("ui.class").equals("PetriPlaceEnd")) {
            value = "STOP";
        } else if (n.getAttribute("ui.class").equals("PetriPlaceInnerStart")) {
            //Loop Place Finisher
            value = n.getAttribute("ui.label");
        }

        return value;

    }
}

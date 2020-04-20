package mc.client;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Node;

import java.util.ArrayList;
import java.util.Iterator;

public class VisualPetriToProcessCodeHelper {
    private boolean continueSearch = true;
    private String cumulativeProcessCode = "";

    public String doConversion(ArrayList<Node> visualCreatedProcesses) {
        ArrayList<Node> HeadPetriNodes = new ArrayList<>();

        String nextValue = "";

        //Remove Nodes that arn't Petri Head Nodes
        for (Node n : visualCreatedProcesses) {
            if (n.getAttribute("ui.class").equals("PetriPlaceStart")) {
                HeadPetriNodes.add(n);
            }
        }

        for (Node n : HeadPetriNodes) {
            String petriID = n.getAttribute("ui.label");
            cumulativeProcessCode += petriID.toUpperCase() + " = ";
            n.setAttribute("visited", "true");

            doBFSRecursive(n);


        }

        return cumulativeProcessCode;

    }


    private void doBFSRecursive(Node n) {



        //When Leaf
        if (n.getAttribute("ui.class").equals("PetriPlaceEnd")) {
            //leaf
            String nextValue = doValueEvaluation(n);




            return;
        }






        Iterator<? extends Edge> k = n.getLeavingEdgeIterator();

        ArrayList<Edge> edges = new ArrayList<>();
        for (Iterator<? extends Edge> it = k; it.hasNext(); ) {
            edges.add(it.next());
        }

        if(edges.size() > 1){
            cumulativeProcessCode += "(";
        }



        for(int i = 0; i < edges.size(); i++){

            Node current = edges.get(i).getNode1();
            Node outGoing = current;



            if(!current.getAttribute("ui.class").equals("PetriPlace")) {


                String nextValue = doValueEvaluation(outGoing);
                cumulativeProcessCode += nextValue;
            }

            doBFSRecursive(outGoing);




            if(i < edges.size() - 1 ) {
                cumulativeProcessCode += " | ";
            }
        }

        if(edges.size() > 1){
            cumulativeProcessCode += ")";
        }





        /*Iterator<? extends Node> k = n.getBreadthFirstIterator();
        n.setAttribute("visited", "true");

        while (k.hasNext()) {

            Node current = k.next();

            if (current.hasAttribute("visited")) {
                continue;
            }

            String nextValue = doValueEvaluation(current);
            cumulativeProcessCode += nextValue;

            //doBFSRecursive(current);

            *//*if (k.hasNext()) {
                cumulativeProcessCode += " | ";
            }*//*

        }

        //Leaf*/


    }

    private String doValueEvaluation(Node n) {
        String value = "";
        if (n.getAttribute("ui.class").equals("PetriTransition")) {
            value = n.getAttribute("ui.label") + " -> ";
        } else if (n.getAttribute("ui.class").equals("PetriPlaceEnd")) {
            value = "STOP";
        }

        System.out.println("value:" + value);
        return value;

    }
}


/*Iterator<? extends Node> k = n.getDepthFirstIterator();

            while (!nextValue.equals("STOP.")) {
                Node dfsTraverserA = k.next();

                dfsTraverserA.setAttribute("visited", "true");

                Iterator<? extends Node> l = dfsTraverserA.getBreadthFirstIterator();
                while (l.hasNext()) {
                    Node bfsTraverserB = l.next();

                    //Unsure Why dfsTraverser is a member of its own bfs ??? strange hence skip
                    if(bfsTraverserB.hasAttribute("visited")){
                        continue;
                    }

                    System.out.println((String) bfsTraverserB.getAttribute("ui.class"));

                    if(bfsTraverserB.getAttribute("ui.class").equals("PetriPlace")){
                        System.out.println("Skipping Place");
                        continue;
                    }
                    nextValue = doValueEvaluation(bfsTraverserB);
                    cumulativeProcessCode += nextValue;
                    if(l.hasNext()) {
                        cumulativeProcessCode += " | ";
                    }
                }
            }*/

package mc.client;

import mc.client.ui.TrieNode;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Node;

import java.util.*;

public class VisualPetriToProcessCodeHelper {
    private String cumulativeProcessCode;
    private Node currentPetriHead;
    private int innerProcessCounter = 1;
    private ArrayList<Node> allNodes;
    private ArrayList<String[]> processes = new ArrayList<>();
    private ArrayList<Integer> processesTextFinalSize = new ArrayList<>();
    private String[] processesText = new String[100];
    private int processesTextSize = 0;
    private boolean innerProcessDetected;
    private Set<String> processesInParelel = new HashSet<>();


    public String doConversion(ArrayList<Node> visualCreatedProcesses) {
        allNodes = visualCreatedProcesses;

        //performParelelSplitting(allNodes);

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
            innerProcessCounter = 1;
            doClearingJobs(visualCreatedProcesses);
            String petriID = n.getAttribute("ui.label");
            processesText[0] += petriID.toUpperCase() + " = ";
            doDFSRecursive(n, processesTextSize);
            processes.add(processesText);
            processesTextFinalSize.add(processesTextSize);
            processesTextSize = 0;
            processesText = new String[100];
        }

        int c = 0;
        for (String[] process : processes) {
            for (int i = 0; i <= processesTextFinalSize.get(c); i++) {
                if (i != process.length) {
                    if(i != processesTextFinalSize.get(c)) {
                        cumulativeProcessCode += process[i] + "," + "\n";
                    } else {
                        cumulativeProcessCode += process[i] + "." + "\n";
                    }
                } else {
                    cumulativeProcessCode += process[i];
                }

            }
            cumulativeProcessCode += "\n";

            c++;
        }

        cumulativeProcessCode += "ParrelelProcesses = ";

        int i = 0;
        for(String s: processesInParelel){
            if(i == 0) {
                cumulativeProcessCode += s.toUpperCase();
            } else {
                cumulativeProcessCode += " || " + s.toUpperCase();
            }

            i++;
        }

        cumulativeProcessCode += ".";

        //cumulativeProcessCode += ".";
        return cumulativeProcessCode;
    }

    private void doClearingJobs(ArrayList<Node> visualCreatedProcesses) {
        for (Node n : allNodes) {
            n.removeAttribute("visited");

            if (n.getAttribute("ui.class").equals("PetriPlaceInnerStart")) {
                n.setAttribute("ui.class", "PetriPlace");
            }
        }

        for (int i = 0; i < 100; i++) {
            processesText[i] = "";
        }


    }

    private void identifyProcessesInParelel(Node n) {
        Collection<Edge> pidsEnteringEdges = n.getEnteringEdgeSet();

        for(Edge e: pidsEnteringEdges){
            processesInParelel.add(e.getNode0().getAttribute("ui.PID"));
        }



    }


    private void doDFSRecursive(Node n, int currentLineForWriting) {

        if (!allNodes.contains(n)) {
            allNodes.add(n);
        }
        boolean isCyclic = false;

        if(!n.hasAttribute("ui.PIDS")) {

            if (n.getAttribute("ui.PID").toString() != currentPetriHead.getAttribute("ui.PID").toString()) {
                return;
            }
        } else {
            identifyProcessesInParelel(n);
        }


        n.setAttribute("visited", "true");

        if (n.getAttribute("ui.class").equals("PetriPlace")) {
            isCyclic = determinePlaceIsCyclic(n);
        }

        if (isCyclic) {
            //Inner process loop


            String innerProcessName = currentPetriHead.getAttribute("ui.label").toString().toUpperCase() + "InnerProcess"
                + innerProcessCounter;

            innerProcessCounter++;

            processesText[currentLineForWriting] += (innerProcessName);
            processesTextSize++;
            processesText[processesTextSize] += innerProcessName + " = ";
            currentLineForWriting = processesTextSize;
            innerProcessDetected = true;


           /* if(!branchDetected) {


                //cumulativeProcessCode += innerProcessName + ",\n" + innerProcessName + " = ";
            } else {
                processesText[processesTextSize] += (innerProcessName + "),");
                processesTextSize++;
                processesText[processesTextSize] += innerProcessName + " = (";
                branchDetected = false;
            }*/

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

        if (n.hasAttribute("ui.PIDS")) {

        }

        // Open bracket for each branch
        if (edges.size() > 1 && !n.hasAttribute("ui.PIDS")) {
            //branch detected
            processesText[processesTextSize] += "(";

            //must now order the edges to so that loop paths are traversed first after this if block
            //Correction: ended up over engineering this as I found better way of handling this with tracking currentLine
            //in recursion will leave it in as slightly cleaner
            PriorityQueue<EdgeAndBool> edgesOrdered = reorderEdges(edges);
            edges.clear();

            while (!edgesOrdered.isEmpty()) {
                Edge ec = edgesOrdered.poll().e;
                edges.add(ec);
            }
        }


        for (int i = 0; i < edges.size(); i++) {

            Node outGoingNode = edges.get(i).getNode1();
            //Node outGoing = current;

            //if node is not a place then get its value
            if (!outGoingNode.getAttribute("ui.class").equals("PetriPlace")) {
                String nextValue = doValueEvaluation(outGoingNode);
                processesText[currentLineForWriting] += nextValue;
                //cumulativeProcessCode += nextValue;
            }

            //And repeat this process for outgoingnode

            if (!outGoingNode.hasAttribute("visited")) {
                doDFSRecursive(outGoingNode, currentLineForWriting);
            } else {
                //Loop detected

                if (outGoingNode == currentPetriHead) {
                    //inner process loop
                    processesText[currentLineForWriting] += outGoingNode.getAttribute("ui.label").toString().toUpperCase();
                    //cumulativeProcessCode += outGoingNode.getAttribute("ui.label").toString().toUpperCase();
                } else {

                }


            }

            //Place a pipe for each child node of parent that is not the final child node in order i.e dont place pipe on
            //final child
            if (i < edges.size() - 1 && !n.hasAttribute("ui.PIDS")) {

                processesText[currentLineForWriting] += " | ";
                //cumulativeProcessCode += " | ";
            }
        }

        // Close bracket for each branch
        if (edges.size() > 1 && !n.hasAttribute("ui.PIDS")) {
            processesText[currentLineForWriting] += ")";
            //cumulativeProcessCode += ")";
        }


    }



    private PriorityQueue<EdgeAndBool> reorderEdges(ArrayList<Edge> edges) {
        PriorityQueue<EdgeAndBool> queue = new PriorityQueue<>(new EdgeAndBoolEvaulator());


        for (Edge e : edges) {
            EdgeAndBool currentEAB = determineBranchPriority(e, e);
            queue.add(currentEAB);
        }

        return queue;
    }

    private EdgeAndBool determineBranchPriority(Edge initialEdge, Edge currentEdge) {

        if (currentEdge.getNode1().hasAttribute("visited")) {
            return new EdgeAndBool(initialEdge, "HeadLoop");
        }

        if (determinePlaceIsCyclic(currentEdge.getNode1())) {
            return new EdgeAndBool(initialEdge, "InnerLoop");
        }

        Iterator<? extends Edge> k = currentEdge.getNode1().getLeavingEdgeIterator();
        ArrayList<Edge> edges = new ArrayList<>();
        for (Iterator<? extends Edge> it = k; it.hasNext(); ) {
            edges.add(it.next());
        }

        for (Edge nextNodeEdge : edges) {
            return determineBranchPriority(initialEdge, nextNodeEdge);
        }

        return new EdgeAndBool(initialEdge, "Normal");

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


class EdgeAndBool {

    public Edge e;
    public String type;

    public EdgeAndBool(Edge n, String type) {
        this.e = n;
        this.type = type;

    }

}

class EdgeAndBoolEvaulator implements Comparator<EdgeAndBool> {
    @Override
    public int compare(EdgeAndBool o1, EdgeAndBool o2) {
        int potentialInversion = -1;
        if (o1.type.equals("HeadLoop") && o2.type.equals("Normal")) {
            return 1 * potentialInversion;
        } else if (o1.type.equals("HeadLoop") && o2.type.equals("InnerLoop")) {
            return 1 * potentialInversion;
        } else if (o1.type.equals("HeadLoop") && o2.type.equals("HeadLoop")) {
            return 1 * potentialInversion;
        } else if (o1.type.equals("Normal") && o2.type.equals("HeadLoop")) {
            return -1 * potentialInversion;
        } else if (o1.type.equals("Normal") && o2.type.equals("InnerLoop")) {
            return 1 * potentialInversion;
        } else if (o1.type.equals("Normal") && o2.type.equals("Normal")) {
            return 1 * potentialInversion;
        } else if (o1.type.equals("InnerLoop") && o2.type.equals("HeadLoop")) {
            return -1 * potentialInversion;
        } else if (o1.type.equals("InnerLoop") && o2.type.equals("Normal")) {
            return -1 * potentialInversion;
        } else {
            return 1 * potentialInversion;
        }
    }
}


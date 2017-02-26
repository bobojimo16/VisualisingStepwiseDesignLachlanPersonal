package mc.compiler;

import mc.compiler.ast.*;
import mc.exceptions.CompilationException;
import mc.process_models.ProcessModel;
import mc.process_models.automata.Automaton;
import mc.process_models.automata.AutomatonGenerator;
import mc.process_models.automata.operations.AutomataOperations;

import java.util.*;

/**
 * Created by sheriddavi on 27/01/17.
 */
public class EquationEvaluator {

    private int equationId;

    private AutomataOperations automataOperations;

    public EquationEvaluator(){
        this.automataOperations = new AutomataOperations();
    }

    public List<OperationResult> evaluateEquations(List<OperationNode> operations, Interpreter interpreter, String code) throws CompilationException {
        reset();
        List<OperationResult> results = new ArrayList<OperationResult>();
        Map<String, ProcessModel> generated = new HashMap<>();
        AutomatonGenerator generator = new AutomatonGenerator();
        for(OperationNode operation : operations){
            String firstId = OperationEvaluator.findIdent(operation.getFirstProcess(), code);
            String secondId = OperationEvaluator.findIdent(operation.getSecondProcess(), code);
            List<String> firstIds = OperationEvaluator.collectIdentifiers(operation.getFirstProcess());
            List<String> secondIds = OperationEvaluator.collectIdentifiers(operation.getSecondProcess());
            List<Automaton> automata = new ArrayList<Automaton>();
            Set<String> ids = new HashSet<>(firstIds);
            ids.addAll(secondIds);
            for (String id: ids) {
                generated.put(id,generator.generateAutomaton(5,5,id));
            }

            automata.add((Automaton) interpreter.interpret("automata", operation.getFirstProcess(), getNextEquationId(), generated));
            automata.add((Automaton) interpreter.interpret("automata", operation.getSecondProcess(), getNextEquationId(), generated));

            if (Objects.equals(operation.getOperation(), "traceEquivalent")) {
                List<Automaton> automata1 = new ArrayList<>();
                for (Automaton a: automata) {
                    automata1.add(new AutomataOperations().nfaToDFA(a));
                }
                automata = automata1;
            }
            boolean result = automataOperations.bisimulation(automata);
            if (operation.isNegated()) {
                result = !result;
            }
            results.add(new OperationResult(operation.getFirstProcess(),operation.getSecondProcess(), firstId, secondId, operation.getOperation(), operation.isNegated(), result,automata));

        }
        return results;
    }

    private String getNextEquationId(){
        return "eq" + equationId++;
    }

    private void reset(){
        equationId = 0;
    }
}

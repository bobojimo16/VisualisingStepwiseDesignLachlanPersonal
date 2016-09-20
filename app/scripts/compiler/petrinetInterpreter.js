'use strict';

function interpretPetriNet(process, processesMap, variableMap, processId, isFairAbstraction){
	var root = constructPetriNet(processId, process.ident.ident);
	var localProcessesMap = constructLocalProcesses(process.ident.ident , process.local);

	// interpret the main process
	interpretNode(process.process, root, process.ident.ident);

	// interpret locally defined processes
	for(var ident in localProcessesMap){
		var localProcess = localProcessesMap[ident];
		interpretNode(localProcess.process, localProcess.place, process.ident.ident);
	}

	// interpret hiding set if one was defined
	if(process.hiding !== undefined){
		processHiding(processesMap[process.ident.ident], process.hiding);
	}

	function constructPetriNet(id, ident){
		var net = new PetriNet(id);
		var root = net.addPlace();
		root.addMetaData('startPlace', true);
		net.addRoot(root.id);
		processesMap[ident] = net;
		return root;	
	}

	function constructLocalProcesses(ident, localProcesses){
		var processes = {};
		for(var i = 0; i < localProcesses.length; i++){
			var astNode = localProcesses[i].ident;
			// check if process has indices defined
			if(astNode.ranges !== undefined){
				constructIndexedLocalProcess(astNode.ident, ident, astNode.ranges.ranges, localProcesses[i].process);
			}
			else{
				processes[astNode.ident] = { place:processesMap[ident].addPlace(), process:localProcesses[i].process };
			}
		}

		return processes;

		function constructIndexedLocalProcess(ident, globalIdent, ranges, process){
			if(ranges.length !== 0){
				var iterator = new IndexIterator(ranges[0]);
				while(iterator.hasNext){
					var element = iterator.next;
					var newIdent = ident + '[' + element + ']';
					ranges = (ranges.length > 1) ? ranges.slice(1) : [];
					constructIndexedLocalProcess(newIdent, globalIdent, ranges, process);
				}
			}
			else{
				processes[ident] = { place:processesMap[globalIdent].addPlace(), process:process };
			}
		}
	}

	function interpretNode(astNode, currentNode, ident){
		var type = astNode.type;
		// determine the type of node to process
		if(type === 'process'){
			interpretLocalProcess(astNode, currentNode, ident);
		}
		else if(type === 'index'){
			interpretIndex(astNode, currentNode, ident);
		}
		else if(type === 'sequence'){
			interpretSequence(astNode, currentNode, ident);
		}
		else if(type === 'choice'){
			interpretChoice(astNode, currentNode, ident);
		}
		else if(type === 'composite'){
			interpretComposite(astNode, currentNode, ident);
		}
		else if(type === 'if-statement'){
			interpretIfStatement(astNode, currentNode, ident);
		}
		else if(type === 'function'){
			interpretFunction(astNode, currentNode, ident);
		}
		else if(type === 'identifier'){
			interpretIdentifier(astNode, currentNode, ident);
		}
		else if(type === 'label'){
			interpretLabel(astNode, currentNode, ident);
		}
		else if(type === 'terminal'){
			interpretTerminal(astNode, currentNode, ident);
		}
		else{
			throw new InterpreterException('Invalid type \'' + type + '\' received');
		}

		// check if a labelling has been defined
		if(astNode.label !== undefined){
			// label is an action label node
			processLabelling(processesMap[ident], astNode.label.action);
		}

		// check if a relabelling has been defined
		if(astNode.relabel !== undefined){
			processRelabelling(processesMap[ident], astNode.relabel.set);
		}
	}

	function interpretLocalProcess(astNode, currentNode, ident){
		throw new InterpreterException('Functionality for interpreting a local process is currently not implemented');
	}

	function interpretIndex(astNode, currentNode, ident){
		var iterator = new IndexIterator(astNode.range);
		while(iterator.hasNext){
			var element = iterator.next;
			variableMap[astNode.variable] = element;
			interpretNode(astNode.process, currentNode, ident);
		}

		//throw new InterpreterException('Functionality for interpreting a range is currently not implemented');
	}

	function interpretSequence(astNode, currentNode, ident){
		// check that the first or second part of the sequence is defined
		if(astNode.from === undefined){
			// throw error
		}

		if(astNode.to === undefined){
			// throw error
		}

		// check that from is an action label
		if(astNode.from.type !== 'action-label'){
			// throw error
		}

		var id = processesMap[ident].nextTransitionId;
		var action = processActionLabel(astNode.from.action);
		var next = processesMap[ident].addPlace();
		processesMap[ident].addTransition(id, action, [currentNode], [next]);
		interpretNode(astNode.to, next, ident);
	}

	function interpretChoice(astNode, currentPlace, ident){
		//interpretNode(astNode.process1, currentPlace, ident);
		//interpretNode(astNode.process2, currentPlace, ident);
		var process1 = ident + '.process1';
		var root1 = constructPetriNet(processesMap[ident].id + 'a', process1);
		interpretNode(astNode.process1, root1, process1);

		var process2 = ident + '.process2';
		var root2 = constructPetriNet(processesMap[ident].id + 'b', process2);
		interpretNode(astNode.process2, root2, process2);

		processesMap[ident].addPetriNet(processesMap[process1]);
		processesMap[ident].addPetriNet(processesMap[process2]);

		var roots1 = processesMap[process1].roots.map(p => processesMap[ident].getPlace(p.id));
		var roots2 = processesMap[process2].roots.map(p => processesMap[ident].getPlace(p.id));

		for(var i = 0; i < roots1.length; i++){
			for(var j = 0; j < roots2.length; j++){
				processesMap[ident].combinePlaces(roots1[i], roots2[j]);
			}
			processesMap[ident].removePlace(roots1[i].id);
		}

		for(var i = 0; i < roots2.length; i++){
			processesMap[ident].removePlace(roots2[i].id);
		}

		delete processesMap[process1];
		delete processesMap[process2];
	}

	function interpretComposite(astNode, currentPlace, ident){
		// interpret the two processes to be composed together
		var process1 = ident + '.process1';
		var root1 = constructPetriNet(processesMap[ident].id + 'a', process1);
		interpretNode(astNode.process1, root1, process1);

		var process2 = ident + '.process2';
		var root2 = constructPetriNet(processesMap[ident].id + 'b', process2);
		interpretNode(astNode.process2, root2, process2);
		
		// compose processes together
		processesMap[ident] = parallelComposition(processesMap[ident].id, processesMap[process1], processesMap[process2]);

		// delete unneeded processes
		delete processesMap[process1];
		delete processesMap[process2];
	}

	function interpretIfStatement(astNode, currentPlace, ident){
		var guard = processGuardExpression(astNode.guard);
		if(guard){
			interpretNode(astNode.trueBranch, currentPlace, ident);
		}
		else if(astNode.falseBranch !== undefined){
			interpretNode(astNode.falseBranch, currentPlace, ident);
		}
		else{
			currentPlace.addMetaData('isTerminal', 'stop');
		}
	}

	function interpretFunction(astNode, currentPlace, ident){
		var type = astNode.func;
		if(type === 'abs'){
			var process1 = ident + '.abs';
			var root1 = constructPetriNet(processesMap[ident].id + 'abs', process1);
			interpretNode(astNode.process, root1, process1);
			processesMap[ident] = abstraction(processesMap[process1].clone, isFairAbstraction);
			delete processesMap[process1];
		}
		else if(type === 'simp'){
			throw new InterpreterException('the simplification function for Petri nets has not been implemented yet');
		}
		else{
			throw new InterpreterException('\'' + type + '\' is not a valid function type');
		}
	}

	function interpretIdentifier(astNode, currentPlace, ident){
		var current = astNode.ident;
		current = processActionLabel(current);
		// check if the process is referencing itself
		if(current === ident){
			processesMap[ident].mergePlaces(processesMap[ident].roots, [currentPlace]);
		}
		// check if the process is referencing a locally defined process
		else if(localProcessesMap[current] !== undefined){
			processesMap[ident].mergePlaces([localProcessesMap[current].place], [currentPlace]);
		}
		// check if the process is referencing a globally defined process
		else if(processesMap[current] !== undefined){
			// check that referenced process is of the same type
			if(processesMap[ident].type === processesMap[current].type){
				processesMap[ident].addPetriNet(processesMap[current].clone, [currentPlace]);
			}
			else{
				throw new InterpreterException('Cannot reference type \'' + processesMap[current].type + '\' from type \'petrinet\'');
			}
		}
		else{
			throw new InterpreterException('The identifier \'' + current + '\' has not been defined');
		}
	}

	function interpretTerminal(astNode, currentPlace, ident){
		if(astNode.terminal === 'STOP'){
			currentPlace.addMetaData('isTerminal', 'stop');
		}
		else if(astNode.terminal === 'ERROR'){
			throw new InterpreterException('Functionality for interpreting error terminals is currently not implemented');
		}
		else{
			// throw error
		}
	}

	/**
	 * Labels each of the transitions in the specified petri net with
	 * the specified label.
	 *
	 * @param {petrinet} net - the petri net to label
	 * @param {string} label - the new label;
	 */
	function processLabelling(net, label){
		var labelSets = net.labelSets;
		// give every transition in the petri net the new label
		for(var i = 0; i < labelSets.length; i++){
			var oldLabel = labelSets[i].label;
			net.relabelTransition(oldLabel, label + '.' + oldLabel);
		}
	}

	/** 
	 * Relabels transtions in the specified Petri net based on the contents of
	 * the specified relabel set. The relabel set is made up of objects containing
	 * the old transition label and the new transition label.
	 *
	 * @param {petrinet} net - the petrinet to relabel
	 * @param {object[]} relabelSet - an array of objects { oldLabel, newLabel }
	 */
	function processRelabelling(net, relabelSet){
		for(var i = 0; i < relabelSet.length; i++){
			// labels are defined as action label nodes
			net.relabelTransition(relabelSet[i].oldLabel.action, relabelSet[i].newLabel.action);
		}
	}

	/**
	 * Relabels transitions in the specified Petri net based on the contents of the
	 * specified hiding set. Depending on the type of the hiding set, all the transitions
	 * with labels in the hiding set are marked as hidden or all the transitions with labels
	 * not in the hiding set are marked as hidden.
	 *
	 * @param {petrinet} net - the petri net to process
	 * @param {object} hidingSet - an object containing a hiding type and an array of actions
	 */
	function processHiding(net, hidingSet){
		var labelSets = net.labelSets;
		var set = hidingSet.set;
		if(hidingSet.type === 'includes'){
			processInclusionHiding(labelSets, set);
		}
		else if(hidingSet.type === 'excludes'){
			processExclusionHiding(labelSets, set);
		}

		/**
		 * Sets all the transitions with labels in the specified set to be hidden.
		 */
		function processInclusionHiding(labelSets, set){
			for(var label in labelSets){
				for(var j = 0; j < set.length; j++){
					// check if the labels match
					if(label === set[j]){
						net.relabelTransition(set[j], TAU);
					}
				}
			}	
		}

		/**
		 * Sets all the transitions with labels not in the specified set to be hidden.
		 */
		function processExclusionHiding(labelSets, set){
			for(var label in labelSets){
				var match = false;
				for(var j = 0; j < set.length; j++){
					// check if the labels match
					if(label === set[j]){
						match = true;
						break;
					}
				}

				// relabel if the current label did not match any labels in the set
				if(!match){
					net.relabelTransition(labelSets[i].label, TAU);
				}
			}	
		}
	}

	/**
	 * Evaluates and returns the specified expression. Returns the result as a boolean if
	 * specified, otherwise returns the result as a number.
	 *
	 * @param {string} - the expression to evaluate
	 * @return {string} - the processed action label
	 */
	function processActionLabel(action){
		// replace any variables declared in the expression with its value
		var regex = '[\$][<]*[a-zA-Z0-9]*[>]*';
		var match = action.match(regex);
		while(match !== null){
			var expr = evaluate(variableMap[match[0]]);
			action = action.replace(match[0], expr);
			match = action.match(regex);
		}

		return action;
	}

	function processGuardExpression(expr){
		// replace any variables declared in the expression with its value
		var regex = '[\$][<]*[a-zA-Z0-9]*[>]*';
		var match = expr.match(regex);
		while(match !== null){
			expr = expr.replace(match[0], variableMap[match[0]]);
			match = expr.match(regex);
		}

		expr = evaluate(expr);
		return (expr === 0) ? false : true;
	}

	function reset(){
		processesMap = {};
	}

	/**
	 * Constructs and returns an 'InterpreterException' based off of the
	 * specified message. Also contains the location in the code being parsed
	 * where the error occured.
	 *
	 * @param {string} message - the cause of the exception
	 * @param {object} location - the location where the exception occured
	 */
	function InterpreterException(message, location){
		this.message = message;
		this.location = location;
		this.toString = function(){
			return 'InterpreterException: ' + message;
		};	
	}
}
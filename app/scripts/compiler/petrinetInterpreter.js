'use strict';

/**
 * Interprets the specified process into a Petri net process model.
 *
 * @param{Process} process - the process to be interpreted into a petri net
 * @param{string -> ProcessModel} - a mapping from process identifier to process model
 * @param{string} processId - the id for the constructed petri net
 * @param{Object} context - contains information from the main application
 */
function interpretPetriNet(process, processesMap, context){
	const processStack = [] // stack containing interpreteted petri nets;
	let override = false;

	const identifier = process.ident.ident;
	const net = new PetriNet(identifier);
	const root = net.addPlace();
	root.addMetaData('startPlace', 1);
	net.addRoot(root.id);

	// interpret the process
	interpretNode(process.process, net, root);
	
	// 
	processReferences(net);

	// check if a hiding set was specified for this process
	if(process.hiding !== undefined){
		processHiding(net, process.hiding);
	}

	// add the constructed petri net to the processes map
	processesMap[identifier] = net;

	/**
	 * Constructs a new Petri net that is to be considered a subset of the
	 * specified Petri net. interprets the specified abstract syntax tree into
	 * the sub Petri net.
	 *
	 * @param{ASTNode} subProcess - the root of the ast for the sub process
	 * @param{PetriNet} net - the petri net that the sub net is a subset of
	 */
	function interpretSubPetriNet(subProcess, net){
		// setup the sub net
		const subNet = new PetriNet(net.id);
		subNet.placeId = net.placeId;
		subNet.transitionId = net.transitionId;

		// setup the sub root
		const subRoot = subNet.addPlace();
		subRoot.metaData.startPlace = 1;
		subNet.addRoot(subRoot.id);

		// interpret the sub process
		interpretNode(subProcess, subNet, subRoot);

		// check if this process was overridden
		if(override){
			override = false;
			return;
		}

		// update the main net
		net.placeId = subNet.placeId;
		net.transitionId = subNet.transitionId;

		processStack.push(subNet);
	}

	/**
	 * Delegates the interpretation of the specified abstract syntax tree node to the
	 * relevant function based on the type of abstract syntax tree node it is.
	 *
	 * @param{CompositeNode} astNode - the composite ast node
	 * @param{PetriNet} net - the current petri net being constructed
	 * @param{PetriNetPlace} currentPlace - the current place in the net to expand off of
	 * @param{PetriNetTransition} lastTransition - the last transition added to the net (potentially undefined)
	 */
	function interpretNode(astNode, net, currentPlace, lastTransition){
		processReferencePointer(astNode, currentPlace);
		switch(astNode.type){
			case 'sequence':
				interpretSequence(astNode, net, currentPlace, lastTransition);
				break;
			case 'choice':
				interpretChoice(astNode, net, currentPlace, lastTransition);
				break;
			case 'composite':
				interpretComposite(astNode, net, currentPlace, lastTransition);
				break;
			case 'function':
				interpretFunction(astNode, net, currentPlace, lastTransition);
				break;
			case 'identifier':
				interpretIdentifier(astNode, net, currentPlace, lastTransition);
				break;
			case 'reference':
				interpretReference(astNode, net, currentPlace, lastTransition);
				break;
			case 'terminal':
				currentPlace.metaData.isTerminal = astNode.terminal;
				break;
			default:
				break;
		}

		// check if a labelling has been defined for this ast node
		if(astNode.label !== undefined){
			processLabelling(net, astNode.label.action)
		}

		// check if a relabelling has been defined for this ast node
		if(astNode.relabel !== undefined){
			processRelabelling(net, astNode.relabel.set);
		}
	}

	/**
	 * Interprets a sequence abstract syntax tree node. Constructs the next transition in the sequence
	 * and progresses onto the next abstract syntax tree node. If the next abstract syntax tree node is a
	 * ReferenceNode, then an arc is constructed from the newly constructed transition to the referenced place.
	 *
	 * @param{SequenceNode} astNode - the sequence ast node
	 * @param{PetriNet} net - the current petri net being constructed
	 * @param{PetriNetPlace} currentPlace - the current place in the net to expand off of
	 * @param{PetriNetTransition} lastTransition - the last transition added to the net (potentially undefined)
	 */
	function interpretSequence(astNode, net, currentPlace, lastTransition){
		const nextPlace = net.addPlace();
		const id = net.nextTransitionId;
		const label = astNode.from.action;
		const transition = net.addTransition(id, label, [currentPlace], [nextPlace]);
		interpretNode(astNode.to, net, nextPlace, transition);
	}

	/**
	 * Interprets a choice abstract syntax tree node. Processes each branch of the
	 * choice as a separate Petri net and then performs a cross product of the roots
	 * from each branch. If the cross products need to be connected to the specified net
	 * then arcs are added from the last transition added to the net to the constructed
	 * cross products.
	 *
	 * @param{ChoiceNode} astNode - the choice ast node
	 * @param{PetriNet} net - the current petri net being constructed
	 * @param{PetriNetPlace} currentPlace - the current place in the net to expand off of
	 * @param{PetriNetTransition} lastTransition - the last transition added to the net (potentially undefined)
	 */
	function interpretChoice(astNode, net, currentPlace, lastTransition){
		interpretSubPetriNet(astNode.process1, net);
		interpretSubPetriNet(astNode.process2, net);
		
		const subNet2 = processStack.pop();
		const subNet1 = processStack.pop();

		net.addPetriNet(subNet1);
		const roots1 = subNet1.roots;
		net.addPetriNet(subNet2);
		const roots2 = subNet2.roots;

		// remove start place data from roots
		const roots = roots1.concat(roots2);
		for(let i = 0; i < roots.length; i++){
			net.removeRoot(roots[i].id);
			delete roots[i].metaData.startPlace;
		}

		// form a cross product of the roots from each branch
		const crossProducts = [];
		for(let i = 0; i < roots1.length; i++){
			for(let j = 0; j < roots2.length; j++){
				crossProducts.push(net.combinePlaces(roots1[i], roots2[j]));
			}
		}

		// remove the previous roots as they are no longer needed
		for(let i = 0; i < roots.length; i++){
			net.removePlace(roots[i].id);
		}

		for(let i = 0; i < crossProducts.length; i++){
			net.combinePlaces(currentPlace, crossProducts[i]);
		}

		crossProducts.push(currentPlace);
		for(let i = 0; i < crossProducts.length; i++){
			net.removePlace(crossProducts[i].id);
		}
	}

	/**
	 * Interprets a composite abstract syntax tree node. Interprets both sub processes
	 * that are defined and then runs the parallel composisition algorithm with both
	 * sub processes. If the composition needs to be connected to the specified net then
	 * arcs are added from the last transition added to the roots of the composed net.
	 *
	 * @param{CompositeNode} astNode - the composite ast node
	 * @param{PetriNet} net - the current petri net being constructed
	 * @param{PetriNetPlace} currentPlace - the current place in the net to expand off of
	 * @param{PetriNetTransition} lastTransition - the last transition added to the net (potentially undefined)
	 */
	function interpretComposite(astNode, net, currentPlace, lastTransition){
		// process both processes separately
		interpretSubPetriNet(astNode.process1, net);
		interpretSubPetriNet(astNode.process2, net);

		// composed the processes together
		const net2 = processStack.pop();
		const net1 = processStack.pop();
		const composedNet = parallelComposition(net.id + '.comp', net1, net2);

		const roots = composedNet.roots;
		for(let i = 0; i < roots.length; i++){
			composedNet.removeRoot(roots[i].id);
			delete roots[i].metaData.startPlace;
		}

		net.addPetriNet(composedNet);
		for(let i = 0; i < roots.length; i++){
			net.combinePlaces(currentPlace, roots[i]);
		}

		roots.push(currentPlace);
		for(let i = 0; i < roots.length; i++){
			net.removePlace(roots[i].id);
		}
	}

	function interpretFunction(astNode, net, currentPlace, lastTransition){
		// interpret the process to have function performed on
		interpretSubPetriNet(astNode.process, net);

		let processedNet = processStack.pop();
		switch(astNode.func){
			case 'abs':
				processedNet = abstraction(processedNet, context.isFairAbstraction);
				break;
			case 'simp':
				processedNet = bisimulation(processedNet);
				break;
			case 'tokenRule':

				break;
			case 'petrinet':
				processedNet = automatonToPetriNet(processedNet); // processed net is actually an automata
				break;
			default:
				break;
		}

		const roots = processedNet.roots;
		for(let i = 0; i < roots.length; i++){
			processedNet.removeRoot(roots[i].id);
			delete roots[i].metaData.startPlace;
		}

		net.addPetriNet(processedNet);
		for(let i = 0; i < roots.length; i++){
			net.combinePlaces(currentPlace, roots[i]);
		}

		roots.push(currentPlace);
		for(let i = 0; i < roots.length; i++){
			net.removePlace(roots[i].id);
		}
	}

	function interpretIdentifier(astNode, net, currentPlace, lastTransition){
		const reference = processesMap[astNode.ident].clone;
		
		// check if the reference is not a petri net
		if(reference.type !== 'petrinet'){
			if(lastTransition !== undefined){
				// throw error
			}

			processStack.push(reference);
			override = true;
			return;
		}

		const roots = reference.roots;
		net.addPetriNet(reference);

		for(let i = 0; i < roots.length; i++){
			net.removeRoot(roots[i].id);
			delete roots[i].metaData.startPlace;
		}

		for(let i = 0; i < roots.length; i++){
			net.combinePlaces(currentPlace, roots[i]);
		}

		roots.push(currentPlace);
		for(let i = 0; i < roots.length; i++){
			net.removePlace(roots[i].id);
		}
	}

	function interpretReference(astNode, net, currentPlace, lastTransition){
		if(lastTransition.metaData.references === undefined){
			lastTransition.metaData.references = {};
		}

		lastTransition.metaData.references[astNode.reference + ''] = true;

		net.removePlace(currentPlace.id);
	}

	function processReferencePointer(astNode, place){
		if(astNode.type !== 'reference' && astNode.reference !== undefined){
			if(place.metaData.references === undefined){
				place.metaData.references = {};
			}

			place.metaData.references[astNode.reference + ''] = true;
		}
	}

	function processReferences(net){
		// construct a map from references ids to places
		const referenceMap = {};
		const places = net.places.filter(p => p.metaData.references !== undefined);
		for(let i = 0; i < places.length; i++){
			const references = Object.keys(places[i].metaData.references);
			for(let j = 0; j < references.length; j++){
				const id = references[j];
				if(referenceMap[id] === undefined){
					referenceMap[id] = [];
				}

				referenceMap[id].push(places[i]);
			}
		}

		const transitions = net.transitions.filter(t => t.metaData.references !== undefined);
		for(let i = 0; i < transitions.length; i++){
			const transition = transitions[i];
			const references = transition.metaData.references;
			for(let id in references){
				net.constructConnection(transition, referenceMap[id]);
			}
		}
	}

	function processHiding(net, hidingSet){
		const alphabet = net.alphabet;
		const set = {};

		for(let i = 0; i < hidingSet.set.length; i++){
			set[hidingSet.set[i]] = true;
		}

		for(let label in alphabet){
			if(set[label] !== undefined && hidingSet.type === 'includes'){
				net.relabelTransition(label, TAU);
			}
			else if(set[label] === undefined && hidingSet.type === 'excludes'){
				net.relabelTransition(label, TAU);
			}
		}
	}

	function processLabelling(net, label){
		const alphabet = net.alphabet;
		for(let action in alphabet){
			net.relabelTransition(action, label + '.' + action);
		}
	}

	function processRelabelling(net, relabelSet){
		for(let i = 0; i < relabelSet.length; i++){
			const newLabel = relabelSet[i].newLabel.action;
			const oldLabel = relabelSet[i].oldLabel.action;
			net.relabelTransition(oldLabel, newLabel);
		}
	}
}
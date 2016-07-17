'use strict';

/**
 * A graph data structure representing the breadth first traversal of a process. Used
 * to determine if two processes are equivalent.
 */
class BFTGraph {
	
	/**
	 * Constructs an empty breadth first traversal graph.
	 */
	constructor(){
		this._nodeMap = {};
		this._rootIds = [];
	}

	/**
	 * Returns the root nodes of this breadth first traversal graph.
	 *
	 * @return {node[]} - array of root nodes
	 */
	get roots(){
		var roots = [];
		for(var i = 0; i < this._rootIds.length; i++){
			roots.push(this._nodeMap[this._rootsIds[i]])
		}

		return roots;
	}

	/**
	 * Adds the specified node id to the root ids for this breadth first
	 * traversal graph. The ids are guaranteed to be sorted after the id
	 * is added.
	 *
	 * @param {string} id - the root id
	 */
	addRootId(id){
		this._rootIds.push(id);
		this._rootIds.sort();
	}

	/**
	 * Adds and returns a new node with the specified id and label to this 
	 * breadth first traversal graph.
	 *
	 * @param {string} id - the id
	 * @param {string} label - the label
	 * @param {node} - the constructed node
	 */
	addNode(id, label){
		if(this._nodeMap[id] !== undefined){
			// throw error
		}

		var node = new BFTGraph.Node(id, label);
		this._nodeMap[id] = node;
		return node;
	}

	/**
	 * Returns the node with the specified id from this breadth first traversal
	 * graph.
	 *
	 * @param {string} id - the node id
	 * @return {node} - the node with the specified id
	 */
	getNode(id){
		if(this._nodeMap[id] !== undefined){
			return this._nodeMap[id];
		}

		// throw error
	}
}

/**
 * Represents a node in a breadth first traversal graph.
 */
BFTGraph.Node = class {
	
	/**
	 * Constructs a new node with the specified id and label.
	 *
	 * @param{string} id - the id
	 * @param{string} label - the label
	 */
	constructor(id, label){
		this._id = id;
		this._label = label;
		this._children = [];
	}

	/**
	 * Returns the id associated with this node.
	 *
	 * @return {string} - the node id
	 */
	get id(){
		return this._id;
	}

	/**
	 * Returns the label associated with this node.
	 *
	 * @return {string} - the node label
	 */
	get label(){
		return this._label;
	}

	/**
	 * Returns an array of the node ids that this node can
	 * traverse to.
	 *
	 * @return {node[]} - array of node ids
	 */
	get children(){
		return this._children;
	}

	/**
	 * Adds the specified node id to the array of node ids that
	 * this node can traverse to.
	 *
	 * @param {string} id - the node id
	 */
	addChild(id){
		this._children.push(id);
		this._children.sort();
	}
}
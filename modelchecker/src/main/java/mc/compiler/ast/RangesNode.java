package mc.compiler.ast;

import java.util.List;

import mc.util.Location;

public class RangesNode extends ASTNode {

	// fields
	private List<IndexNode> ranges;

	public RangesNode(List<IndexNode> ranges, Location location){
		super(location);
		this.ranges = ranges;
	}

	public List<IndexNode> getRanges(){
		return ranges;
	}

    public boolean equals(Object obj){
        if(obj == this){
            return true;
        }
        if(obj == null){
            return true;
        }
        if(obj instanceof RangesNode){
            RangesNode node = (RangesNode)obj;
            return ranges.equals(node.getRanges());
        }

        return false;
    }
}
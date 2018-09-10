package mc.compiler.ast;

import lombok.Data;
import lombok.EqualsAndHashCode;
import mc.util.Location;
import java.util.List;
/**
 * a "ForAll" statement, this is a programmatic way of parallel composing multiple processes using
 * event indexing.
 * <p>
 * e.g. {@code ForAllTest = forall [i:1..2] (do[i] -> STOP).}
 * This expands to {@code ForAllTest = (do[1] STOP) || (do[2] -> STOP).}
 * <p>
 * The grammar for this is: {@code FORALL :: "forall" RANGE PROCESS.}
 *
 * A second use is controlling the variable expansion in equations
 * forall{X}(P(X,Y,Z) ) ==> Q(Y,Z)
 *
 * @author Jacob Beal
 * @see CompositeNode
 * @see ASTNode
 * @see mc.compiler.Expander
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ForAllStatementNode extends ASTNode {

  /**
   * The ranges upon which the ForAll statement will apply.
   */
  private RangesNode ranges;
  /**
   *
   */
  private List<IdentifierNode> variables;
  /**
   * The process that shall be iterated through to compose.
   */
  private ASTNode process;

  /**
   * Instantiate a new instance of ForAllNode.
   *
   * @param ranges   The range upon which this node shall apply {@link #ranges}
   * @param process  The process that shall be used to create the composition {@link #process}
   * @param location The location within the users code where this
   *                 node is located {@link ASTNode#location}
   */
  public ForAllStatementNode(RangesNode ranges, ASTNode process, Location location) {
    super(location,"Forall");
    this.ranges = ranges;
    this.process = process;
  }
  public ForAllStatementNode(List<IdentifierNode> variables,  Location location) {
    super(location,"Forall");
    this.variables = variables;
  }
}

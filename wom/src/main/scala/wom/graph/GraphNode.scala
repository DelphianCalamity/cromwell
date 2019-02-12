package wom.graph

import cats.implicits._
import common.collections.EnhancedCollections._
import common.validation.ErrorOr.ErrorOr
import wom.callable.Callable
import wom.callable.Callable._
import wom.graph.GraphNode._
import wom.graph.GraphNodePort.{InputPort, NodeCompletionPort, OutputPort}
import wom.graph.expression.ExpressionNode

trait GraphNode {
  def identifier: WomIdentifier

  /**
    * Alias for identifier.localName.value
    */
  final def localName: String = identifier.localName.value

  /**
    * Alias for identifier.fullyQualifiedName.value
    */
  final def fullyQualifiedName: String = identifier.fullyQualifiedName.value

  final override def equals(other: Any): Boolean = super.equals(other)
  final override def hashCode: Int = super.hashCode()

  /**
    * Inputs that must be available before this graph node can be run.
    */
  def inputPorts: Set[GraphNodePort.InputPort]

  /**
    * Outputs that are generated by this GraphNode
    */
  def outputPorts: Set[GraphNodePort.OutputPort]

  val completionPort = NodeCompletionPort(_ => this)

  def outputByName(name: String): ErrorOr[GraphNodePort.OutputPort] = {
    outputPorts.find(_.name == name) match {
      case Some(port) => port.validNel
      case None => s"No such output: $name".invalidNel
    }
  }

  /**
    * The set of all graph nodes which are (transitively) upstream from this one.
    */
  lazy val upstreamAncestry = calculateUpstreamAncestry(Set.empty, this)

  /**
    * The set of all OuterGraphInputNodes which are somewhere upstream of this Node (in the same graph)
    */
  lazy val upstreamOuterGraphInputNodes: Set[OuterGraphInputNode] = upstreamAncestry.filterByType[OuterGraphInputNode]
  protected def calculateUpstreamPorts: Set[OutputPort] = inputPorts.map(_.upstream)
  lazy val upstreamPorts: Set[OutputPort] = calculateUpstreamPorts
  lazy val upstream: Set[GraphNode] = upstreamPorts.map(_.graphNode)

  def containedCalls: Set[CallNode] = this match {
    case c: CallNode => Set(c)
    case s: ScatterNode => s.innerGraph.nodes.flatMap(_.containedCalls)
    case c: ConditionalNode => c.innerGraph.nodes.flatMap(_.containedCalls)
    case _ => Set.empty
  }
}

object GraphNode {
  // A recursive traversal with a fancy trick to avoid double-counting:
  private def calculateUpstreamAncestry(currentSet: Set[GraphNode], graphNode: GraphNode): Set[GraphNode] = {
    val setWithUpstream = currentSet ++ graphNode.upstream
    val updatesNeeded = graphNode.upstream -- currentSet
    updatesNeeded.foldLeft(setWithUpstream)(calculateUpstreamAncestry)
  }

  def inputPortNamesMatch(required: Set[InputPort], provided: Set[InputPort]): ErrorOr[Unit] = {
    def requiredInputFound(r: InputPort): ErrorOr[Unit] = provided.find(_.name == r.name) match {
      case Some(p) => if (r.womType.isCoerceableFrom(p.womType)) ().validNel else s"Cannot link a ${p.womType.stableName} to the input ${r.name}: ${r.womType}".invalidNel
      case None => s"The required input ${r.name}: ${r.womType.stableName} was not provided.".invalidNel
    }

    required.toList.traverse(requiredInputFound).void
  }

  /**
    * Allows a level of indirection, so that GraphNodePorts can be constructed before their associated GraphNode is
    * constructed. If used, the _graphNode must be set before anything tries to apply 'get'.
    */
  class GraphNodeSetter[A <: GraphNode] {
    var _graphNode: A = _
    private def getGraphNode = _graphNode
    def get: Unit => A = _ => getGraphNode
  }

  private[wom] implicit class EnhancedGraphNodeSet(val nodes: Set[GraphNode]) extends AnyVal {
    /**
      * Interpret this graph's "GraphInputNode"s as "Callable.InputDefinition"s
      */
    def inputDefinitions: Set[_ <: Callable.InputDefinition] = nodes collect {
      case required: RequiredGraphInputNode => RequiredInputDefinition(required.identifier.localName, required.womType)
      case optional: OptionalGraphInputNode => OptionalInputDefinition(optional.identifier.localName, optional.womType)
      case withDefault: OptionalGraphInputNodeWithDefault => OverridableInputDefinitionWithDefault(withDefault.identifier.localName, withDefault.womType, withDefault.default)
    }

    def outputDefinitions: Set[_ <: Callable.OutputDefinition] = nodes collect {
      // TODO: FIXME: Do something for PortBasedGraphOutputNodes
      case gin: ExpressionBasedGraphOutputNode => OutputDefinition(gin.identifier.localName, gin.womType, gin.womExpression)
    }
  }

  /**
    * This pattern is used when new Nodes are wired into a set of Graph nodes, and potentially end up creating new input nodes.
    */
  trait GeneratedNodeAndNewNodes {
    def node: GraphNode
    def newInputs: Set[_ <: ExternalGraphInputNode]

    /**
      * All OuterGraphInputNodes that should be included in the same graph as 'node'.
      */
    def usedOuterGraphInputNodes: Set[_ <: OuterGraphInputNode]
    def newExpressions: Set[ExpressionNode]

    final def nodes: Set[GraphNode] = newExpressions ++ newInputs ++ usedOuterGraphInputNodes ++ Set(node)
  }

  trait GraphNodeWithInnerGraph { this: GraphNode =>
    def innerGraph: Graph
  }
}

/*
 * Copyright 2019 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.blockGraphs;

import org.terasology.blockGraphs.graphDefinitions.BlockGraph;
import org.terasology.blockGraphs.graphDefinitions.GraphNodeComponent;
import org.terasology.blockGraphs.graphDefinitions.GraphType;
import org.terasology.blockGraphs.graphDefinitions.nodes.EdgeNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.GraphNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.TerminusNode;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.math.Side;
import org.terasology.math.geom.Vector3i;
import org.terasology.registry.In;
import org.terasology.registry.Share;
import org.terasology.world.BlockEntityRegistry;
import org.terasology.world.OnChangedBlock;
import org.terasology.world.block.BlockUri;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.terasology.blockGraphs.NodeLinkHelper.NodePosition;
import static org.terasology.blockGraphs.NodeLinkHelper.getSideFrom;
import static org.terasology.blockGraphs.NodeLinkHelper.getSurroundingPos;
import static org.terasology.blockGraphs.NodeLinkHelper.splitEdgeAt;
import static org.terasology.blockGraphs.NodeLinkHelper.tryBiLink;
import static org.terasology.blockGraphs.NodeLinkHelper.updatePosition;

/**
 * This class is the counterpart to the {@link BlockGraphConstructor}.
 * Whilst it deals with constructing graphs from a collection of blocks,
 * this deals with changes made to a graph once it has been constructed.
 * <p>
 * A change to a graph can be split into two major categories, placing and removing.
 * A placing change is one triggered by a block being placed.
 * (More accurately when a block <i>without</i> a graph type is modified)
 * These can either result in a graph simply being enlarged, or in the merging of two graphs
 * A removing change is one triggered by the destruction of a block
 * (More accurately when a block <i>with</i> a graph type is modified)
 * These can either result in a graph shrinking, being destroyed or in the splitting of a graph
 */
@RegisterSystem
@Share(GraphChangeManager.class)
public class GraphChangeManager extends BaseComponentSystem {

    @In
    private BlockEntityRegistry entityRegistry;

    @In
    private BlockGraphManager graphManager;

    @In
    private BlockGraphConstructor graphConstructor;

    /**
     * Called when a block changes.
     * <p>
     * Is used to detect if the change includes a graph block.
     *
     * @see OnChangedBlock
     */
    @ReceiveEvent
    public void OnChangedBlock(OnChangedBlock event, EntityRef entity) {
        if (graphManager.getGraphType(event.getOldType().getURI()) != null) {
            removalEvent(event);
        } else if (graphManager.getGraphType(event.getNewType().getURI()) != null) {
            placingEvent(event);
        } //todo: These may be independent and hence can potentially both happen
        //todo: Consider replacing a block in one graph with another in a different
    }

    /**
     * There was no graph block here before.
     * <p>
     * Scans for nearby graphs and then merges this block into each of them successively
     *
     * @param event The placing event
     */
    private void placingEvent(OnChangedBlock event) {
        GraphType graph = graphManager.getGraphType(event.getNewType().getURI());
        /* Get all neighbouring blocks & scan for graphs */
        Vector3i[] neighbours = getSurroundingPos(event.getBlockPosition());

        List<NodePosition> nodes = new ArrayList<>();
        for (Vector3i neighbour : neighbours) {
            NodePosition node = new NodePosition();
            node.pos = neighbour;
            EntityRef blockEntity = entityRegistry.getExistingEntityAt(neighbour);
            if (!blockEntity.exists() || !blockEntity.hasComponent(GraphNodeComponent.class)) {
                continue;
            }
            GraphNodeComponent component = blockEntity.getComponent(GraphNodeComponent.class);
            if (component.graphUri.getGraphUri() != graph.getUri()) {
                continue;
            }
            node.graph = graphManager.getGraphInstance(component.graphUri);
            node.node = node.graph.getNode(component.nodeId);
            nodes.add(node);
        }
        //TODO: Finish by linking it to the mergeGraphs method below
    }


    private void mergeGraphs(GraphType graphType, BlockUri blockType, Vector3i newPos, List<NodePosition> neighbours) {
        NodePosition position = new NodePosition();
        position.graph = graphManager.newGraphInstance(graphType);
        position.node = position.graph.createTerminusNode(blockType);
        position.pos = newPos;

        for (NodePosition neighbour : neighbours) {
            mergeInto(position,
                    neighbour);
        }
    }

    /**
     * Merges two graphs together.
     * <p>
     * We have a couple of cases, dictated by what node types each node is as well as what each node already connects to
     * (Note: left hand side is always "from". Right hand side is always "to")
     * (Note: Scenarios that are not symmetrical can be flipped)
     * <p>
     * TODO: Simplify this down somehow!!
     * <pre>
     * 1. Terminus == Terminus
     *      1.1  Blank == T === T == Blank
     *          Link To & From
     *      1.2  Blank == T === T == T (== Blank) [implied]
     *          Upgrade First To T into Edge
     *      1.3  Blank == T === T == E
     *          Merge To T and To E
     *      1.4  Blank == T === T == J
     *          Upgrade To T into Edge
     *      1.5  T == T === T == T
     *          Upgrade First From T into Edge
     *          Upgrade First To T into Edge
     *          Merge Both E
     *      1.6  T == T === T == E
     *          Upgrade First From T into Edge
     *          Upgrade To T into Edge
     *          Merge all Three E
     *      1.7  T == T === T == J
     *          Upgrade First From T into Edge
     *          Upgrade To T into Edge
     *          Merge Both E
     *      1.8  E == T === T == E
     *          Upgrade From T into Edge
     *          Upgrade To T into Edge
     *          Merge all Four E
     *      1.9  E == T === T == J
     *          Upgrade From T into Edge
     *          Upgrade To T into Edge
     *          Merge all Three E
     *      1.10 J == T === T == J
     *          Upgrade From T into Edge
     *          Upgrade To T into Edge
     *          Merge Both E
     *
     * 2. Terminus == Edge
     *      Because Edges must be "full" (ie both ends connected) we know that the TO pos will have to be a junction
     *      So we can upgrade it to a Junction and apply the Junction rules
     *      2.a End
     *          Split E into a Junction and an Edge
     *          Apply appropriate rule (T === J)
     *      2.b Middle
     *          Split E into a Junction and two Edges either side
     *          Apply appropriate rule (T === J)
     *
     * 3. Terminus == Junction
     *      We know that the junction cannot be full, else we wouldn't be able to touch it.
     *      It is also irrelevant what it connects to.
     *      3.1 Blank == T === J
     *          Link To & From
     *      3.2 T == T === J
     *          Convert From T into an Edge
     *      3.3 E == T === J
     *          Convert From T into an Edge
     *          Merge both E
     *      3.4 J == T === J
     *          Convert From T into an Edge
     * 4. Edge == Terminus
     * 5. Junction == Terminus
     *      These are covered by flipped cases. Simply switch "To" and "From" in the descriptions
     * 6. Edge == Edge
     *      Edges are always "full" so all cases are the same.
     *      Convert both into Junctions. If in middle of edge, split Edge into two
     *      Link To & From
     * 7. Edge == Junction
     *      Edges are always "full" so all cases are the same.
     *      Convert Edge into Junction. If in middle of edge, split Edge into two
     *      Link To & From
     * 8. Junction == Junction
     *      Just Link To & From
     * </pre>
     *
     * @param from The node that is being merged
     * @param to   The node/graph that is is being merged into
     */
    void mergeInto(NodePosition from, NodePosition to) {
        //TODO: Merge graph instances and INVALIDATE old instance. Ensure references are updated to avoid issues
        switch (from.node.getNodeType()) {
            case TERMINUS:
                mergeFromTerminus(from, to);
                break;
            case EDGE:
                mergeFromEdge(from, to);
                break;
            case JUNCTION:
                mergeFromJunction(from, to);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + from.node.getNodeType());
        }
    }

    void moveGraphNodes(BlockGraph from, BlockGraph to) {
        GraphNode start = from.getNode(from.getNodeIds().iterator().next());


    }

    private void duplicateConnectionBi(GraphNode oldFrom, GraphNode oldTo, GraphNode newFrom, GraphNode newTo) {
        duplicateConnectionUni(oldFrom, oldTo, newFrom, newTo);
        duplicateConnectionUni(oldTo, oldFrom, newTo, newFrom);
    }

    private void duplicateConnectionUni(GraphNode oldFrom, GraphNode oldTo, GraphNode newFrom, GraphNode newTo) {
        switch (oldFrom.getNodeType()) {
            case TERMINUS:
                ((TerminusNode) newFrom).connectionNode = ((TerminusNode) oldFrom).connectionNode;
                ((TerminusNode) newFrom).connectionSide = ((TerminusNode) oldFrom).connectionSide;
                break;
            case EDGE:
                ((EdgeNode) newFrom)
                break;
            case JUNCTION:
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + oldFrom.getNodeType());
        }
    }

    private GraphNode convertNodeToGraph(GraphNode oldNode, BlockGraph newGraph, Set<Integer> visited) {
        GraphNode newNode = newGraph.createNode(oldNode.definitionId, oldNode.getNodeType());
        switch (oldNode.getNodeType()) {
            case TERMINUS:
                if (((TerminusNode) oldNode).isConnected()) {
                    GraphNode otherNode = ((TerminusNode) oldNode).connectionNode;

                } else {
                    // We just have a graph of one terminus
                    // No further work needed
                    updatePosition(newNode, entityRegistry);
                    return newNode;
                }
                break;
            case EDGE:
                break;
            case JUNCTION:
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + oldNode.getNodeType());
        }
    }


    /**
     * Merge a graph at a terminus node, into another graph.
     * <p>
     * If we
     *
     * @param from The position in the first graph, that is merging into `to`
     * @param to   The position in the second graph, that `from` is being merged into
     */
    private void mergeFromTerminus(NodePosition from, NodePosition to) {
        switch (to.node.getNodeType()) {
            case TERMINUS:
                if (((TerminusNode) from.node).isConnected()) {
                    from.node = upgradeTerminusToEdge((TerminusNode) from.node);
                }
                if (((TerminusNode) to.node).isConnected()) {
                    to.node = upgradeTerminusToEdge((TerminusNode) to.node);
                }
                linkNodes(from, to);
                graphConstructor.crunchChain((EdgeNode) to.node, to.graph);
                break;
            case EDGE:
                // Utilise the reverse case to avoid duplicate code
                mergeFromEdge(to, from);
                break;
            case JUNCTION:
                // Utilise the reverse case to avoid duplicate code
                if (((TerminusNode) to.node).isConnected()) {
                    to.node = upgradeTerminusToEdge((TerminusNode) to.node);
                    linkNodes(from, to);
                    graphConstructor.crunchChain((EdgeNode) to.node, to.graph);
                } else {
                    linkNodes(from, to);
                }
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + to.node.getNodeType());
        }
    }

    private void linkNodes(NodePosition from, NodePosition to) {
        Side fromToTo = getSideFrom(from.pos, to.pos);
        if (!tryBiLink(from.node, to.node, fromToTo)) {
            throw new IllegalStateException("Failed to link two Nodes when merging graphs");
        }
    }

    /**
     * To merge an edge, we have the exact same method.
     * Split the edge to produce a junction node at the merge pos. Then recurse back into merging.
     * <p>
     * We can do this because an edge is always "full", ie it always has a connected front an a connected back.
     * This means that no matter what position on the edge we are merging, we will need to produce a junction node.
     *
     * @param from The node to merge from
     * @param to   The node to merge into
     */
    private void mergeFromEdge(NodePosition from, NodePosition to) {
        splitEdgeAt(from, entityRegistry);
        from.node = getNodeAt(from.pos, from.graph);
        mergeInto(from, to);
    }

    private EdgeNode upgradeTerminusToEdge(TerminusNode node) {
        BlockGraph graph = graphManager.getGraphInstance(node.graphUri);
        if (node.isConnected()) {
            GraphNode connection = node.connectionNode;
            Side connectionSide = node.connectionSide;
            graph.removeNode(node);
            EdgeNode edgeNode = graph.createEdgeNode(node.definitionId);
            tryBiLink(edgeNode, connection, connectionSide);
            return edgeNode;
        } else {
            graph.removeNode(node);
            return graph.createEdgeNode(node.definitionId);
        }
    }

    private void mergeFromJunction(NodePosition from, NodePosition to) {
        switch (to.node.getNodeType()) {
            case TERMINUS:
                mergeFromTerminus(to, from);
                break;
            case EDGE:
                mergeFromEdge(from, to); // Use existing code but flip the to and from
                break;
            case JUNCTION:
                linkNodes(from, to);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + to.node.getNodeType());
        }
    }

    private void removalEvent(OnChangedBlock event) {

    }

    /**
     * Gets the node, if any, at the position in block space
     *
     * @param position The position to get the node at
     * @param graph    The graph the node would belong to
     * @return The node if it exists, null otherwise
     */
    private GraphNode getNodeAt(Vector3i position, BlockGraph graph) {
        EntityRef blockEntity = entityRegistry.getExistingEntityAt(position);
        GraphNodeComponent component = blockEntity.getComponent(GraphNodeComponent.class);
        if (component != null && component.graphUri == graph.getUri()) {
            return graph.getNode(component.nodeId);
        } else {
            return null;
        }
    }

}

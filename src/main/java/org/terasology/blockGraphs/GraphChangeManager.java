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
import org.terasology.blockGraphs.graphDefinitions.NodeRef;
import org.terasology.blockGraphs.graphDefinitions.nodes.NodeType;
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

import static org.terasology.blockGraphs.NodeLinkHelper.NodePosition;
import static org.terasology.blockGraphs.NodeLinkHelper.getSideFrom;
import static org.terasology.blockGraphs.NodeLinkHelper.getSurroundingPos;
import static org.terasology.blockGraphs.NodeLinkHelper.splitEdgeAt;
import static org.terasology.blockGraphs.NodeLinkHelper.tryBiLink;

/**
 * This class is the counterpart to the {@link BlockGraphConstructor}.
 * Whilst that deals with constructing graphs from a collection of blocks,
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


    /**
     * Constructs a new graph at a given point and merges it into it's neighbouring graphs.
     *
     * @param graphType  The type of the graphs being merged
     * @param blockType  The type of the node to merge at
     * @param newPos     The position of the new graph
     * @param neighbours The neighbour graphs to merge into
     */
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
     *      1.1 Blank == T === T == Blank
     *          Link To & From
     *      1.2 Blank == T === T == T (== Blank) [implied]
     *          Upgrade First To T into Edge
     *      1.3 Blank == T === T == E
     *          Merge To T and To E
     *      1.4 Blank == T === T == J
     *          Upgrade To T into Edge
     *      1.5 T == T === T == T
     *          Upgrade First From T into Edge
     *          Upgrade First To T into Edge
     *          Merge Both E
     *      1.6 T == T === T == E
     *          Upgrade First From T into Edge
     *          Upgrade To T into Edge
     *          Merge all Three E
     *      1.7 T == T === T == J
     *          Upgrade First From T into Edge
     *          Upgrade To T into Edge
     *          Merge Both E
     *      1.8 E == T === T == E
     *          Upgrade From T into Edge
     *          Upgrade To T into Edge
     *          Merge all Four E
     *      1.9 E == T === T == J
     *          Upgrade From T into Edge
     *          Upgrade To T into Edge
     *          Merge all Three E
     *      1.10 J == T === T == J
     *          Upgrade From T into Edge
     *          Upgrade To T into Edge
     *          Merge Both E
     *
     * 2. Terminus == Edge
     *      Because Edges must be "full" (ie both ends connected) we know that the TO pos will have to be a junction,
     *      so we can upgrade it to a Junction and apply the Junction rules
     *      2.1 End
     *          Split E into a Junction and an Edge
     *          Apply appropriate rule (T === J)
     *      2.2 Middle
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
     * @param to   The node/graph that it is being merged into
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

    /**
     * Merge a graph at a terminus node, into another graph.
     *
     * @param from The position in the graph merged from.
     * @param to   The position in the graph being merged into.
     */
    private void mergeFromTerminus(NodePosition from, NodePosition to) {
        switch (to.node.getNodeType()) {
            case TERMINUS:
                /* If either of the nodes are connected then we need to upgrade them into edges to handle having two connections. */
                if (from.node.asTerminus().isConnected()) {
                    upgradeTerminusToEdge(from.node);
                }
                if (to.node.asTerminus().isConnected()) {
                    upgradeTerminusToEdge(to.node);
                }

                /* Now we link the two nodes */
                linkNodes(from, to);

                /* If one of the nodes was upgraded then we want to crunch */
                if (to.node.getNodeType() == NodeType.EDGE) {
                    graphConstructor.crunchChain(to.node, to.graph);
                } else if (from.node.getNodeType() == NodeType.EDGE) {
                    graphConstructor.crunchChain(from.node, to.graph);
                }
                break;
            case EDGE:
                // Utilise the reverse case to avoid duplicate code
                mergeFromEdge(to, from);
                break;
            case JUNCTION:
                /* Upgrade 'FROM' node if it is a connected terminus */
                if (from.node.asTerminus().isConnected()) {
                    upgradeTerminusToEdge(from.node);
                }
                linkNodes(from, to);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + to.node.getNodeType());
        }
    }

    /**
     * Attempts to link two positions together.
     * This link is bi-directional, so 'FROM' is linked to 'TO', and 'TO' is linked to 'FROM'
     *
     * @param from The first node in the link
     * @param to   The second node in the link
     */
    private void linkNodes(NodePosition from, NodePosition to) {
        Side fromToTo = getSideFrom(from.pos, to.pos);
        if (!tryBiLink(from, to, fromToTo)) {
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

    /**
     * Converts a terminus node into an edge node.
     * This will produce an edge node that is not 'full'.
     * This is technically a violation of what an edge node is and as such the node returned by this method should be linked to another one directly after returning
     *
     * @param node The node to upgrade
     * @return The node as an edge
     */
    private NodeRef upgradeTerminusToEdge(NodeRef node) {
        BlockGraph graph = graphManager.getGraphInstance(node.getGraphUri());
        TerminusNode oldNode = node.getNode();

        // Record information to allow us to reconnect the node
        NodeRef connection = oldNode.connectionNode;
        Side connectionSide = oldNode.connectionSide;
        Vector3i pos = oldNode.worldPos;
        boolean wasConnected = oldNode.isConnected();

        node = graph.replaceNode(node, NodeType.EDGE);
        node.asEdge().worldPositions.add(pos);

        /* If this node is connected, we need to re-add the connection */
        if (wasConnected) {
            tryBiLink(node, connection, connectionSide);
            //We know this new edge will only have one connection so this works
            if (node.asEdge().frontNode != null) {
                node.asEdge().frontPos = pos;
            } else {
                node.asEdge().backPos = pos;
            }
        }

        return node;
    }

    /**
     * Merge a junction node into another.
     * This method uses existing code in all bar the JUNCTION <=> JUNCTION case.
     *
     * @param from The junction node to merge
     * @param to   The node to merge into
     */
    private void mergeFromJunction(NodePosition from, NodePosition to) {
        switch (to.node.getNodeType()) {
            case TERMINUS:
                mergeFromTerminus(to, from);
                break;
            case EDGE:
                mergeFromEdge(to, from); // Use existing code but flip the to and from //TODO: why flip?
                break;
            case JUNCTION:
                linkNodes(from, to); // We can simply try to link them
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + to.node.getNodeType());
        }
    }


    private void removalEvent(OnChangedBlock event) {
        //TODO: implement
    }

    /**
     * Gets the node, if any, at the position in block space
     *
     * @param position The position to get the node at
     * @param graph    The graph the node would belong to
     * @return The node if it exists, null otherwise
     */
    private NodeRef getNodeAt(Vector3i position, BlockGraph graph) {
        EntityRef blockEntity = entityRegistry.getExistingEntityAt(position);
        GraphNodeComponent component = blockEntity.getComponent(GraphNodeComponent.class);
        if (component != null && component.graphUri == graph.getUri()) {
            return graph.getNode(component.nodeId);
        } else {
            return null;
        }
    }

}

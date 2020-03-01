/*
 * Copyright 2018 MovingBlocks
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

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.blockGraphs.graphDefinitions.BlockGraph;
import org.terasology.blockGraphs.graphDefinitions.GraphNodeComponent;
import org.terasology.blockGraphs.graphDefinitions.GraphType;
import org.terasology.blockGraphs.graphDefinitions.GraphUri;
import org.terasology.blockGraphs.graphDefinitions.NodeRef;
import org.terasology.blockGraphs.graphDefinitions.nodeDefinitions.NodeDefinition;
import org.terasology.blockGraphs.graphDefinitions.nodes.EdgeNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.GraphNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.JunctionNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.NodeType;
import org.terasology.blockGraphs.graphDefinitions.nodes.TerminusNode;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.math.Side;
import org.terasology.math.geom.Vector3i;
import org.terasology.registry.In;
import org.terasology.registry.Share;
import org.terasology.world.BlockEntityRegistry;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockManager;
import org.terasology.world.block.BlockUri;

import java.util.Set;
import java.util.stream.Collectors;

import static org.terasology.blockGraphs.NodeLinkHelper.doUniLink;
import static org.terasology.blockGraphs.NodeLinkHelper.doesNodeHaveSpace;
import static org.terasology.blockGraphs.NodeLinkHelper.getSideFrom;
import static org.terasology.blockGraphs.NodeLinkHelper.getSurroundingPos;
import static org.terasology.blockGraphs.NodeLinkHelper.tryBiLink;

@RegisterSystem
@Share(BlockGraphConstructor.class)
public class BlockGraphConstructor extends BaseComponentSystem {
    private static final Logger logger = LoggerFactory.getLogger(BlockGraphConstructor.class);

    @In
    private BlockGraphManager graphManager;
    @In
    private WorldProvider worldProvider;
    @In
    private BlockManager blockManager;
    @In
    private BlockEntityRegistry blockEntityRegistry;

    private NodeRef crunchEdge(EdgeEnd frontEdge, EdgeEnd backEdge, Set<NodeRef> edges, BlockGraph graph) {
        NodeRef finalEdge = graph.createEdgeNode(frontEdge.edgeEnd.getDefinitionId());
        EdgeNode rawNode = finalEdge.getNode();

        edges.stream()/* Update the world blocks */
                .map(ref -> (EdgeNode) ref.getNode())
                .map(edgeNode -> edgeNode.frontPos)
                .map(blockEntityRegistry::getExistingEntityAt)
                .forEach(entityRef -> {
                    GraphNodeComponent component = entityRef.getComponent(GraphNodeComponent.class);
                    component.nodeId = finalEdge.getNodeId();
                    entityRef.saveComponent(component);
                });

        /* Record the positions of each point in the edge (in order) */
        Set<Vector3i> positions = edges.stream()
                .map(ref -> (EdgeNode) ref.getNode())
                .map(edgeNode -> edgeNode.frontPos)
                .collect(Collectors.toSet());
        Vector3i currPos = backEdge.getConnectionPos();
        positions.remove(currPos);
        rawNode.worldPositions.add(currPos);
        while (!positions.isEmpty()) {
            Vector3i nextPos = null;
            for (Vector3i neighbour : getSurroundingPos(currPos)) {
                if (positions.contains(neighbour)) {
                    nextPos = neighbour;
                    break;
                }
            }
            if (nextPos == null) {
                throw new IllegalStateException("Current pos had no neighbours in the edge.");
            }
            positions.remove(nextPos);
            rawNode.worldPositions.add(nextPos);
            currPos = nextPos;
        }

        Side frontSide = frontEdge.getConnectionSide();
        Side backSide = backEdge.getConnectionSide();

        /* Record the connection points _before_ we remove all the nodes as that will invalidate the edge refs */
        rawNode.frontPos = frontEdge.getConnectionPos();
        rawNode.backPos = frontEdge.getConnectionPos();

        /* Remove all the old edges
        * This goes first so that the end of the 'OTHER' node is empty*/
        edges.forEach(graph::removeNode);

        if (frontEdge.edgeEnd == backEdge.edgeEnd) {
            // It's a circle
            rawNode.linkNode(finalEdge, Side.FRONT, frontSide);
            rawNode.linkNode(finalEdge, Side.BACK, frontSide.reverse());
        } else {
            rawNode.linkNode(frontEdge.other, Side.FRONT, frontSide);
            rawNode.linkNode(backEdge.other, Side.BACK, backSide);

            doUniLink(frontEdge.other, finalEdge, frontSide.reverse());
            doUniLink(backEdge.other, finalEdge, backSide.reverse());
        }
        return finalEdge;
    }

    public NodeRef crunchChain(NodeRef startNode, BlockGraph targetGraph) {
        return runFrom(startNode, targetGraph, null);
    }

    private NodeRef runFrom(NodeRef startNode, BlockGraph targetGraph, Set<Integer> visitedNodes) {
        Set<NodeRef> edges = Sets.newHashSet();
        edges.add(startNode);

        // Chase down the front
        EdgeEnd frontEnd = raceDown(startNode, startNode.asEdge().frontNode, edges);
        // Chase down the back
        EdgeEnd backEnd = raceDown(startNode, startNode.asEdge().backNode, edges);

        /* Add all the intermediate edges */
        if (visitedNodes != null) {
            visitedNodes.addAll(
                    edges.stream()
                            .map(NodeRef::getNodeId)
                            .collect(Collectors.toList()));
        }

        /* Then crunch it all into one new edge */
        return crunchEdge(frontEnd, backEnd, edges, targetGraph);
    }

    public void crunchGraph(BlockGraph targetGraph) {
        Set<Integer> visitedNodes = Sets.newHashSet();
        Set<NodeRef> frontier = Sets.newHashSet();
        frontier.add(targetGraph.getNodeIds().stream().findAny().map(targetGraph::getNode).orElse(null));

        while (!frontier.isEmpty()) {
            NodeRef node = Iterables.getFirst(frontier, null);
            if (node == null) {
                throw new IllegalStateException("Node in crunch frontier was null"); //Should be impossible
            }
            frontier.remove(node);
            visitedNodes.add(node.getNodeId());

            if (node.getNodeType() == NodeType.EDGE) {

                node = runFrom(node, targetGraph, visitedNodes);

                visitedNodes.add(node.getNodeId());
            }

            /* Add all connections we've not seen before */
            frontier.addAll(
                    node.getConnections()
                            .stream()
                            .filter(neighbour -> !visitedNodes.contains(neighbour.getNodeId()))
                            .collect(Collectors.toList()));
        }
    }

    private NodeRef nextNode(NodeRef edge, NodeRef back) {
        if (edge.asEdge().backNode == back) {
            return edge.asEdge().frontNode;
        } else {
            return edge.asEdge().backNode;
        }
    }

    private class EdgeEnd {
        public NodeRef other;
        public NodeRef edgeEnd;

        public EdgeEnd(NodeRef edgeEnd, NodeRef other) {
            this.other = other;
            this.edgeEnd = edgeEnd;
        }

        public Side getConnectionSide() {
            return edgeEnd.asEdge().frontNode == other ? edgeEnd.asEdge().frontSide : edgeEnd.asEdge().backSide;
        }

        public Vector3i getConnectionPos() {
            return edgeEnd.asEdge().frontNode == other ? edgeEnd.asEdge().frontPos : edgeEnd.asEdge().backPos;
        }
    }

    /**
     * Travels down an edge, recording the nodes it moves through
     *
     * @param currentNode The node to start at
     * @param nextNode    The next node to shift into
     * @param edges       The running set of visited edges
     * @return The final edge in this direction and it's next connection
     */
    private EdgeEnd raceDown(NodeRef currentNode, NodeRef nextNode, Set<NodeRef> edges) {
        NodeRef startNode = currentNode; // Record the start

        while (true) {
            edges.add(currentNode);
            if (nextNode.getNodeType() == NodeType.EDGE && currentNode.getDefinitionId() == nextNode.getDefinitionId()) {
                // Next node is an edge and the same type of edge, so shift into it
                NodeRef lastNode = currentNode;
                currentNode = nextNode;
                nextNode = nextNode(currentNode, lastNode);
            } else {
                // Next node is not the same so we've reached the end
                return new EdgeEnd(currentNode, nextNode);
            }

            // Check if we've looped a circle
            if (currentNode == startNode) {
                // This won't trigger on the first iteration because of the if statement above
                return new EdgeEnd(currentNode, nextNode);
            }
        }
    }

    private NodeRef newNodeAt(Vector3i pos, BlockGraph targetGraph, BlockUri blockUri) {
        NodeRef baseNode = targetGraph.createTerminusNode(blockUri);
        baseNode.asTerminus().worldPos = pos;

        EntityRef entity = blockEntityRegistry.getBlockEntityAt(pos);
        GraphNodeComponent component = new GraphNodeComponent();

        component.graphUri = targetGraph.getUri();
        component.nodeId = baseNode.getNodeId();

        entity.addComponent(component);
        return baseNode;
    }

    /**
     * Runs a flood fill on the entire graph from the given position to construct a new graph
     * This will not respect existing graphs using those blocks, if there are any
     *
     * @return The URI of the newly created graph or null if that failed
     */
    public GraphUri constructEntireGraph(Vector3i position) {
        Block startBlock = worldProvider.getBlock(position);
        GraphType graphType = graphManager.getGraphType(startBlock.getURI());
        if (graphType != null) {
            BlockGraph newGraph = graphManager.newGraphInstance(graphType);
            floodFillFromPoint(position, newGraph);
            return newGraph.getUri();
        } else {
            logger.error("Unable to find graph type for block, " + startBlock + " at the given position");
            return null;
        }
    }

    /**
     * @param startPos
     * @param targetGraph
     */
    private void floodFillFromPoint(Vector3i startPos, BlockGraph targetGraph) {
        Set<Vector3i> frontier = Sets.newHashSet();

        frontier.add(startPos); // We don't get here unless start pos has a valid node def
        while (!frontier.isEmpty()) {
            // We only add positions that could be a node.
            // This way we don't get stuck in an infinite loop of looking out
            Vector3i currentPos = Iterables.getFirst(frontier, null);
            frontier.remove(currentPos);
            Block block = worldProvider.getBlock(currentPos);
            NodeDefinition definition = targetGraph.getGraphType().getDefinition(block.getURI());

            if (definition == null) {
                // The block cannot be a member, so we ignore
                throw new IllegalStateException("Position with no node definition somehow added to frontier");
            }

            // Pos could be a member of this graph
            /* CASES:
             *   - The block is already a member of this graph (CANNOT HAPPEN AS POS WOULDN'T GET PICKED)
             *   - The block is a member of a different graph
             *   - The block is not a member of this graph
             */
            EntityRef blockEntity = blockEntityRegistry.getExistingEntityAt(currentPos);
            if (blockEntity.exists() && blockEntity.hasComponent(GraphNodeComponent.class)) {
                // Is a member of SOME graph
                GraphNodeComponent component = blockEntity.getComponent(GraphNodeComponent.class);
                if (component.graphUri == targetGraph.getUri()) {
                    // A member of THIS graph
                    throw new IllegalStateException("Position belonging to this graph somehow added to frontier");
                }
                if (component.graphUri.getGraphUri() != targetGraph.getUri().getGraphUri()) {
                    // A member of a DIFFERENT graph _Type_
                    throw new IllegalStateException("Position belonging to incompatible graph somehow added to frontier");
                }
                // TODO: Consider when this would actually happen?
                // A member of a different, but still compatible, graph
                // TODO: merge the two
                // Handle merging graphs: Update all Graph URI to point to the new ID
                // Need to restrict who can make new graph types
                continue; // We don't want to investigate neighbours of this pos
            } else {
                // Is not a member of any graph so we can add it to ours
                NodeRef baseNode = newNodeAt(currentPos, targetGraph, block.getURI());
                linkToNeighbours(baseNode, currentPos, targetGraph);
            }

            // Add all valid positions around this one to the frontier
            Vector3i[] neighbours = getSurroundingPos(currentPos);
            for (Vector3i neighbour : neighbours) {
                if (shouldConsiderPoint(neighbour, targetGraph)) {
                    frontier.add(neighbour);
                }
            }
        }
    }

    /**
     * Checks if a position should be considered for inclusion into the frontier.
     * Any position that is either
     * a) A block with a valid node type
     * b) Not already a node in the current graph (other graphs are fine)
     *
     * @param pos The position to check
     * @return True if it should be added, false otherwise.
     */
    private boolean shouldConsiderPoint(Vector3i pos, BlockGraph graph) {
        Block block = worldProvider.getBlock(pos);
        if (graph.getGraphType().getDefinition(block.getURI()) != null) {
            // Block at position can be a node in this graph
            EntityRef blockEntity = blockEntityRegistry.getExistingEntityAt(pos);
            if (!blockEntity.hasComponent(GraphNodeComponent.class)) {
                return true; // Block can be a node but is not
            }

            GraphNodeComponent component = blockEntity.getComponent(GraphNodeComponent.class);
            if (component.graphUri == graph.getUri()) {
                return false; // Position is a node in the SAME graph INSTANCE
                //Ie: We cannot merge the graphs
            }
            if (component.graphUri.getGraphUri() == graph.getUri().getGraphUri()) {
                return true; // Position is a node, and has the SAME graph TYPE
                // (but DIFFERENT graph INSTANCE)
                // Ie: We can merge the graphs
            }
            return false; // Is a node in a DIFFERENT graph TYPE
        }
        return false; // Could not be a node in this graph
    }


    private void linkToNeighbours(NodeRef node, Vector3i pos, BlockGraph graph) {
        Vector3i[] positions = getSurroundingPos(pos);
        for (Vector3i position : positions) {
            NodeRef neighbour = getNodeAt(position, graph);
            if (neighbour != null) {
                NodeRef linkedNode = tryForceLink(node, pos, neighbour, position);
                if (linkedNode == null) {
                    // The link failed, so just skip the position
                } else {
                    // The link worked, so update the reference and repeat
                    node = linkedNode;
                }
            } else {
                // There is no node from this graph there
            }
        }
    }


    /**
     * We can always upgrade unless it's a junction. Furthermore, an upgrade will ALWAYS have space for our node.
     * This is because the node we are trying to connect cannot be in the same position as a node that has already been connected.
     * <p>
     * Hence the terminal -> edge upgrade will have space
     * and the edge -> junction upgrade will too.
     *
     * @param node The node to upgrade
     * @return True if we can upgrade. False otherwise
     */
    private boolean canUpgrade(NodeRef node) {
        return node.getNodeType() != NodeType.JUNCTION;
    }

    private NodeRef upgradeNode(NodeRef node) {
        BlockGraph graph = graphManager.getGraphInstance(node.getGraphUri());
        switch (node.getNodeType()) {
            case TERMINUS:
                /* Record the connection, and then remove the node from the graph */
                NodeRef otherNode = node.asTerminus().connectionNode;
                Side otherSide = node.asTerminus().connectionSide;

                graph.replaceNode(node, NodeType.EDGE);

                /* Reform the connection */
                if (otherNode != null) {
                    tryBiLink(node, otherNode, otherSide);
                }

                return node;
            case EDGE:



                /* Record the connection, and then remove the node from the graph */
                //TODO: May have a problem. This will potentially fail if edges are allowed to be larger than 1.
                //TODO: Can fix by adding a second check in `canUpgrade` or by splitting up all edges into 1 size chunks
                //TODO: Revisit when we merge existing graphs together
                NodeRef frontNode = node.asEdge().frontNode;
                Side frontSide = node.asEdge().frontSide;
                NodeRef backNode = node.asEdge().backNode;
                Side backSide = node.asEdge().backSide;

                graph.replaceNode(node, NodeType.JUNCTION);

                if (frontNode != null) {
                    tryBiLink(node, frontNode, frontSide);
                }
                if (backNode != null) {
                    tryBiLink(node, backNode, backSide);
                }

                return node;
            case JUNCTION:
                // We can't upgrade this one
                throw new IllegalStateException("Can't upgrade a junction");
            default:
                throw new IllegalStateException("Unexpected value: " + node.getNodeType());
        }
    }

    /**
     * Attempts to force two nodes to link.
     * Upgrades the nodes if needed.
     *
     * @param thisNode  The node to link from
     * @param thisPos   The position of the node to link from
     * @param otherNode The node to link to
     * @param otherPos  The position of the node to link to
     * @return The linked node (may not be the same ref). Null if failed
     */
    private NodeRef tryForceLink(NodeRef thisNode, Vector3i thisPos, NodeRef otherNode, Vector3i otherPos) {
        Side thisToOther = getSideFrom(thisPos, otherPos);

        if ((doesNodeHaveSpace(thisNode, thisToOther) || canUpgrade(thisNode))
                && (doesNodeHaveSpace(otherNode, thisToOther.reverse()) || canUpgrade(otherNode))) {
            /* Upgrade the nodes we need to upgrade */
            if (!doesNodeHaveSpace(thisNode, thisToOther)) {
                upgradeNode(thisNode);
                updateWorldRef(thisNode, thisPos);
            }
            if (!doesNodeHaveSpace(otherNode, thisToOther.reverse())) {
                upgradeNode(otherNode);
                updateWorldRef(otherNode, otherPos);
            }
            /* Do the link with the upgraded nodes */
            if (tryBiLink(thisNode, otherNode, thisToOther)) {
                return thisNode;
            }
        }
        return null; // We can't link them as one doesn't have space or something failed
    }

    private void updateWorldRef(NodeRef node, Vector3i pos) {
        EntityRef entity = blockEntityRegistry.getExistingEntityAt(pos);
        GraphNodeComponent component = entity.getComponent(GraphNodeComponent.class);

        component.graphUri = node.getGraphUri();
        component.nodeId = node.getNodeId();

        entity.saveComponent(component);

        switch (node.getNodeType()) {
            case TERMINUS:
                node.asTerminus().worldPos = pos;
                break;
            case EDGE:
                node.asEdge().backPos = pos;
                node.asEdge().frontPos = pos;
                break;
            case JUNCTION:
                node.asJunction().worldPos = pos;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + node.getNodeType());
        }
    }

    /**
     * Gets the node, if any, at the position in block space
     *
     * @param position The position to get the node at
     * @param graph    The graph the node would belong to
     * @return The node if it exists, null otherwise
     */
    private NodeRef getNodeAt(Vector3i position, BlockGraph graph) {
        EntityRef blockEntity = blockEntityRegistry.getExistingEntityAt(position);
        GraphNodeComponent component = blockEntity.getComponent(GraphNodeComponent.class);
        if (component != null && component.graphUri == graph.getUri()) {
            return graph.getNode(component.nodeId);
        } else {
            return null;
        }
    }

}

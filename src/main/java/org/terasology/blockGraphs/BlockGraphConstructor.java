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

    private TerminusNode newNodeAt(Vector3i pos, BlockGraph targetGraph, BlockUri blockUri) {
        TerminusNode baseNode = targetGraph.createTerminusNode(blockUri);
        baseNode.worldPos = pos;

        EntityRef entity = blockEntityRegistry.getBlockEntityAt(pos);
        GraphNodeComponent component = new GraphNodeComponent();

        component.graphUri = targetGraph.getUri();
        component.nodeId = baseNode.nodeId;

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
                // The block cannot be a member so we ignore
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
            } else {
                // Is not a member of any graph so we can add it to ours
                TerminusNode baseNode = newNodeAt(currentPos, targetGraph, block.getURI());
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


    private void linkToNeighbours(GraphNode node, Vector3i pos, BlockGraph graph) {
        Vector3i[] positions = getSurroundingPos(pos);
        for (Vector3i position : positions) {
            GraphNode neighbour = getNodeAt(position, graph);
            if (neighbour != null) {
                GraphNode linkedNode = tryForceLink(node, pos, neighbour, position);
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
     * Checks if a node can have a new node linked to on the given side.
     *
     * @param thisNode    The node to be linked
     * @param thisToOther The side to link on
     * @return True if the link can be made, false if there is already a connection
     */
    private boolean doesNodeHaveSpace(GraphNode thisNode, Side thisToOther) {
        switch (thisNode.getNodeType()) {
            case TERMINUS:
                if (thisNode.getConnections().size() >= 1) {
                    return false;
                }
                break;
            case EDGE:
                if (thisNode.getConnections().size() >= 2) {
                    return false;
                }
                break;
            case JUNCTION:
                if (thisNode.getConnections().size() >= 6) {
                    return false;
                } else if (((JunctionNode) thisNode).getNodeForSide(thisToOther) != null) {
                    // Junction is tricky because it cares about the side
                    return false;
                }
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + thisNode.getNodeType());
        }
        return true;
    }

    /**
     * Checks if we can link the nodes together in both directions
     *
     * @param thisNode    The first node to link
     * @param otherNode   The second node to link
     * @param thisToOther The side from the first node to the second
     * @return True if a bi-directional link can be made
     */
    private boolean canLinkNodes(GraphNode thisNode, GraphNode otherNode, Side thisToOther) {
        return doesNodeHaveSpace(thisNode, thisToOther) && doesNodeHaveSpace(otherNode, thisToOther.reverse());
    }

    /**
     * Links one node with another. Doesn't respect any pre-existing connections on the given side
     * TODO: Extract into GraphNode class?
     *
     * @param thisNode    The node to link from
     * @param otherNode   The node to link to
     * @param thisToOther The side to link via
     */
    private void doUniLink(GraphNode thisNode, GraphNode otherNode, Side thisToOther) {
        switch (thisNode.getNodeType()) {
            case TERMINUS:
                ((TerminusNode) thisNode).linkNode(otherNode, thisToOther);
                break;
            case EDGE:
                EdgeNode thisEdge = (EdgeNode) thisNode;
                if (thisEdge.frontNode == null) {
                    thisEdge.linkNode(otherNode, Side.FRONT, thisToOther);
                } else if (thisEdge.backNode == null) {
                    thisEdge.linkNode(otherNode, Side.BACK, thisToOther);
                }
                break;
            case JUNCTION:
                JunctionNode thisJunction = (JunctionNode) thisNode;
                thisJunction.linkNode(otherNode, thisToOther);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + thisNode.getNodeType());
        }
    }

    /**
     * Tries to link this node an another node.
     * If this node is a terminus, or junction try and link on the side it's physically on
     * If this node is an edge, try to link at the front, and then the back
     * Respect any existing connections
     * Repeat for the other node to make the link two way
     *
     * @param thisNode    The first node
     * @param otherNode   The second node to link
     * @param thisToOther The side from the first node to the second
     * @return True if they were linked, false otherwise
     */
    private boolean tryBiLink(GraphNode thisNode, GraphNode otherNode, Side thisToOther) {
        if (canLinkNodes(thisNode, otherNode, thisToOther)) {
            doUniLink(thisNode, otherNode, thisToOther);
            doUniLink(otherNode, thisNode, thisToOther.reverse());
            return true;
        } else {
            return false;
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
    private boolean canUpgrade(GraphNode node) {
        return node.getNodeType() != NodeType.JUNCTION;
    }

    private GraphNode upgradeNode(GraphNode node) {
        BlockGraph graph = graphManager.getGraphInstance(node.graphUri);
        switch (node.getNodeType()) {
            case TERMINUS:
                EdgeNode edgeNode = graph.createEdgeNode(node.definitionId);

                /* Record the connection, and then remove the node from the graph */
                GraphNode otherNode = ((TerminusNode) node).connectionNode;
                Side otherSide = ((TerminusNode) node).connectionSide;
                graph.removeNode(node);

                /* Reform the connection */
                tryBiLink(edgeNode, otherNode, otherSide);

                node.nodeId = edgeNode.nodeId; // Update the ref to point to the new node
                return edgeNode;
            case EDGE:
                JunctionNode junctionNode = graph.createJunctionNode(node.definitionId);

                /* Record the connection, and then remove the node from the graph */
                //TODO: May have a problem. This will potentially fail if edges are allowed to be larger than 1.
                //TODO: Can fix by adding a second check in `canUpgrade` or by splitting up all edges into 1 size chunks
                //TODO: Revisit when we merge existing graphs together
                GraphNode frontNode = ((EdgeNode) node).frontNode;
                Side frontSide = ((EdgeNode) node).frontSide;
                GraphNode backNode = ((EdgeNode) node).backNode;
                Side backSide = ((EdgeNode) node).backSide;

                graph.removeNode(node);

                tryBiLink(junctionNode, frontNode, frontSide);
                tryBiLink(junctionNode, backNode, backSide);

                node.nodeId = junctionNode.nodeId; // Update the ref to point to the new node
                return junctionNode;
            case JUNCTION:
                // We can't upgrade this one
                return null;
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
    private GraphNode tryForceLink(GraphNode thisNode, Vector3i thisPos, GraphNode otherNode, Vector3i otherPos) {
        Side thisToOther = GraphNode.getSideBetween(thisPos, otherPos);

        if ((doesNodeHaveSpace(thisNode, thisToOther) || canUpgrade(thisNode))
                && (doesNodeHaveSpace(otherNode, thisToOther.reverse()) || canUpgrade(otherNode))) {
            /* Upgrade the nodes we need to upgrade */
            if (!doesNodeHaveSpace(thisNode, thisToOther)) {
                thisNode = upgradeNode(thisNode);
                updateWorldRef(thisNode, thisPos);
            }
            if (!doesNodeHaveSpace(otherNode, thisToOther.reverse())) {
                otherNode = upgradeNode(otherNode);
                updateWorldRef(otherNode, otherPos);
            }
            /* Do the link with the upgraded nodes */
            if (tryBiLink(thisNode, otherNode, thisToOther)) {
                return thisNode;
            }
        }
        return null; // We can't link them as one doesn't have space or something failed
    }

    private void updateWorldRef(GraphNode node, Vector3i pos) {
        EntityRef entity = blockEntityRegistry.getExistingEntityAt(pos);
        GraphNodeComponent component = entity.getComponent(GraphNodeComponent.class);

        component.graphUri = node.graphUri;
        component.nodeId = node.nodeId;

        entity.saveComponent(component);
    }

    /**
     * Gets the node, if any, at the position in block space
     *
     * @param position The position to get the node at
     * @param graph    The graph the node would belong to
     * @return The node if it exists, null otherwise
     */
    private GraphNode getNodeAt(Vector3i position, BlockGraph graph) {
        EntityRef blockEntity = blockEntityRegistry.getExistingEntityAt(position);
        GraphNodeComponent component = blockEntity.getComponent(GraphNodeComponent.class);
        if (component != null && component.graphUri == graph.getUri()) {
            return graph.getNode(component.nodeId);
        } else {
            return null;
        }
    }

    private Vector3i[] getSurroundingPos(Vector3i worldPos) {
        return Side.getAllSides().stream().map(side -> new Vector3i(side.getVector3i()).add(worldPos)).toArray(Vector3i[]::new);
    }


//
//    /**
//     * Adds the block at the given point to the graph
//     *
//     * @param position    The position of the block to add
//     * @param targetGraph The graph to add the block too
//     * @return The node at the given position.
//     */
//    private JunctionNode addPointToGraph(Vector3f position, BlockGraph targetGraph) {
//        EntityRef blockEntity = blockEntityRegistry.getBlockEntityAt(position);
//        GraphNodeComponent nodeComponent = blockEntity.getComponent(GraphNodeComponent.class);
//
//        /* Only add the position to this graph if it isn't already */
//        if (nodeComponent == null || nodeComponent.graphUri != targetGraph.getUri()) {
//            JunctionNode newNode = targetGraph.createNode(
//                    worldProvider.getBlock(position).getURI());
//            newNode.setWorldPos(new Vector3i(position));
//
//            nodeComponent = new GraphNodeComponent();
//            nodeComponent.graphUri = targetGraph.getUri();
//            nodeComponent.nodeId = newNode.getNodeId();
//            blockEntity.addOrSaveComponent(nodeComponent);
//
//            return newNode;
//        } else {
//            return graphManager.getGraphNode(nodeComponent.graphUri, nodeComponent.nodeId);
//        }
//    }
//
//    private void updateNodeConnections(JunctionNode node) {
//        /* Handle any nodes that this one has made stop being an edge */
//        for (Map.Entry<Side, JunctionNode> entry : node.getConnectingNodes().entrySet()) {
//            JunctionNode nodeConnection = entry.getValue();
//            Side connectionSide = entry.getKey();
//            if (nodeConnection.wasEdge()) {
//                handleDeEdging(nodeConnection);
//            }
//        }
//    }
//
//    /**
//     * Handles converting a node from an edge into an edge and a junction (or similar)
//     *
//     * @param oldEdge The old node that is now being updated
//     */
//    private void handleDeEdging(JunctionNode oldEdge) {
//        int oldId = oldEdge.getNodeId();
//        Vector3i currentPos = oldEdge.getFrontPos();
//        Vector3i edgeBack = oldEdge.getBackPos();
//        BlockGraph graph = graphManager.getGraphInstance(oldEdge.getGraphUri());
//        graph.removeNode(oldEdge);
//        Vector3i priorPos = null;
//        Set<JunctionNode> nodesToRelink = new HashSet<>();
//
//        JunctionNode currentNode = graph.createNode(worldProvider.getBlock(currentPos).getURI());
//        currentNode.setFrontPos(currentPos);
//        currentNode.setBackPos(currentPos);
//        while (currentPos != edgeBack) { /* Loop until we reach the end of the edge */
//            Map<Side, Integer> nodeMap = getNeighbouringNodes(currentPos, graph.getUri());
//            if (nodeMap.size() == 0) { /* There is only one node in the edge */
//                // This shouldn't be possible but may as well handle it
//                break;
//            } else if (nodeMap.size() <= 2) { /* If the current block only has two connections, we can link it */
//                GraphNodeComponent nodeComponent = blockEntityRegistry
//                        .getBlockEntityAt(currentPos)
//                        .getComponent(GraphNodeComponent.class);
//                nodeComponent.nodeId = currentNode.getNodeId();
//                currentNode.setBackPos(currentPos);
//
//            } else { /* We have multiple connections. So we make a junction and continue */
//                /* TODO: Need to possibly re-structure edges into a separate class to handle the intricacies like how they connect (ie, by what side)
//                 * TODO: Possibly a alternative abstract class to NODE extending form it, or from a common interface
//                 * TODO: Issue arises from the fact that both connections to an edge can be the same and thus clash in the nodes list.
//                 * TODO: Possibly just use Side.FRONT & Side.BACK but this would be overloading the class and honestly just confusing
//                 * TODO: Having to do logic for the edge is also confusing. Move methods to static and call from node type?`
//                 */
//                JunctionNode graphNode = graph.createNode(worldProvider.getBlock(currentPos).getURI());
//                nodesToRelink.add(graphNode);
//                GraphNodeComponent nodeComponent = blockEntityRegistry
//                        .getBlockEntityAt(currentPos)
//                        .getComponent(GraphNodeComponent.class);
//                nodeComponent.nodeId = graphNode.getNodeId();
//
//                Vector3i[] newPos = incrementPos(currentPos, priorPos, nodeMap, oldId);
//                currentPos = newPos[0];
//                priorPos = newPos[1];
//
//                currentNode = graph.createNode(worldProvider.getBlock(currentPos).getURI());
//
//            }
//            /* This is kinda ugly honestly */
//            Vector3i[] newPos = incrementPos(currentPos, priorPos, nodeMap, oldId);
//            currentPos = newPos[0];
//            priorPos = newPos[1];
//        }
//
//        nodesToRelink.forEach(this::linkToNeighbourNodes);
//    }
//
//    /**
//     * @param currentPos The current position being scanned
//     * @param priorPos   The prior position, or null otherwise
//     * @param fullMap    A map of all the connections
//     * @param oldId      The ID of the old edge that is being replaced
//     * @return The new position to scan and the old position
//     */
//    private Vector3i[] incrementPos(Vector3i currentPos, Vector3i priorPos, Map<Side, Integer> fullMap, int oldId) {
//        /* Remove all the connections that are not part of the original edge */
//        Map<Side, Integer> filteredMap = fullMap.entrySet()
//                .stream()
//                .filter(entry -> entry.getValue() == oldId)
//                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
//
//        /* Remove the way we came */
//        if (priorPos != null && filteredMap.size() == 2) { /* If we are not on the first iteration, or last iteration */
//            filteredMap.remove(
//                    Side.inDirection(
//                            priorPos.x - currentPos.x, //If there is an error here, flip the subtraction
//                            priorPos.y - currentPos.y,
//                            priorPos.z - currentPos.z));
//        }
//
//        /* Move in the last remaining direction */
//        return new Vector3i[]{currentPos,
//                filteredMap.keySet()
//                        .iterator()
//                        .next()
//                        .getAdjacentPos(priorPos)};
//    }
//
//    /**
//     * Gets the id's of any neighbouring nodes to this one.
//     * Nodes are only counted if they are a part of the same graph
//     * <p>
//     * Also note that each block in an edge node will count.
//     * This means that the result might return the same node for multiple sides if that is the case
//     *
//     * @param nodePos  The position to scan around
//     * @param graphUri The URI of the graph to check against.
//     * @return A mapping between the side and the node's ID
//     */
//    private Map<Side, Integer> getNeighbouringNodes(Vector3i nodePos, GraphUri graphUri) {
//        Vector3i sidePos = new Vector3i();
//        Map<Side, Integer> sideNodes = new HashMap<>(7);
//
//        for (Side side : Side.values()) {
//            sidePos.set(nodePos).add(side.getVector3i());
//            GraphNodeComponent nodeComponent = blockEntityRegistry.getBlockEntityAt(sidePos)
//                    .getComponent(GraphNodeComponent.class);
//            if (nodeComponent != null && nodeComponent.graphUri == graphUri) {
//                sideNodes.put(side, nodeComponent.nodeId);
//            }
//        }
//
//        return sideNodes;
//    }
//
//    /**
//     * Checks and updates any node connections around a given node
//     *
//     * @param checkingNode The node to update around
//     */
//    private void linkToNeighbourNodes(JunctionNode checkingNode) {
//        Map<Side, Integer> nodeMap = getNeighbouringNodes(checkingNode.getWorldPos(), checkingNode.getGraphUri());
//        for (Map.Entry<Side, Integer> entry : nodeMap.entrySet()) {
//            JunctionNode otherNode = graphManager.getGraphNode(checkingNode.getGraphUri(), entry.getValue());
//            checkingNode.linkNode(otherNode);
//        }
//    }
//
//
//    /**
//     * Adds a single block to the new graph, and handle potential effects of this by updating any involved graphs
//     *
//     * @return The URI's of all the graphs that were updated
//     */
//    public List<GraphUri> addBlockToGraph() {
//        //TODO: Implement
//        return null;
//    }
//
//    /**
//     * Removes a block from the given graph and handles any effects of this, by updating any involved graphs
//     *
//     * @return The URI's of all the graphs that were updated, deleted or created
//     */
//    public List<GraphUri> removeBlockFromGraph() {
//        //TODO: Implement
//        return null;
//    }
}

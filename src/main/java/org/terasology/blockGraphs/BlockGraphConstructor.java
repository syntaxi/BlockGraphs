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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.blockGraphs.graphDefinitions.BlockGraph;
import org.terasology.blockGraphs.graphDefinitions.GraphNodeComponent;
import org.terasology.blockGraphs.graphDefinitions.GraphType;
import org.terasology.blockGraphs.graphDefinitions.GraphUri;
import org.terasology.blockGraphs.graphDefinitions.nodes.GraphNode;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.math.Side;
import org.terasology.math.geom.Vector3f;
import org.terasology.math.geom.Vector3i;
import org.terasology.registry.In;
import org.terasology.registry.Share;
import org.terasology.world.BlockEntityRegistry;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Runs a flood fill on the entire graph from the given position to construct a new graph
     * This will not respect existing graphs using those blocks, if there are any
     *
     * @return The URI of the newly created graph or null if that failed
     */
    public GraphUri constructEntireGraph(Vector3f position) {
        Block startBlock = worldProvider.getBlock(position);
        Optional<GraphType> graphType = graphManager.getGraphType(startBlock.getURI());
        if (graphType.isPresent()) {
            BlockGraph newGraph = graphManager.newGraphInstance(graphType.get());
            floodFillFromPoint(position, newGraph);
            return newGraph.getUri();
        } else {
            logger.error("Unable to find graph type for block, " + startBlock);
            return null;
        }
    }

    private void floodFillFromPoint(Vector3f position, BlockGraph targetGraph) {
        GraphNode newNode = addPointToGraph(position, targetGraph);
        linkToNeighbourNodes(newNode);
        updateNodeConnections(newNode);
    }

    /**
     * Adds the block at the given point to the graph
     *
     * @param position    The position of the block to add
     * @param targetGraph The graph to add the block too
     * @return The node at the given position.
     */
    private GraphNode addPointToGraph(Vector3f position, BlockGraph targetGraph) {
        EntityRef blockEntity = blockEntityRegistry.getBlockEntityAt(position);
        GraphNodeComponent nodeComponent = blockEntity.getComponent(GraphNodeComponent.class);

        /* Only add the position to this graph if it isn't already */
        if (nodeComponent == null || nodeComponent.graphUri != targetGraph.getUri()) {
            GraphNode newNode = targetGraph.createNode(
                    worldProvider.getBlock(position).getURI());
            newNode.setWorldPos(new Vector3i(position));

            nodeComponent = new GraphNodeComponent();
            nodeComponent.graphUri = targetGraph.getUri();
            nodeComponent.nodeId = newNode.getNodeId();
            blockEntity.addOrSaveComponent(nodeComponent);

            return newNode;
        } else {
            return graphManager.getGraphNode(nodeComponent.graphUri, nodeComponent.nodeId);
        }
    }

    private void updateNodeConnections(GraphNode node) {
        /* Handle any nodes that this one has made stop being an edge */
        for (Map.Entry<Side, GraphNode> entry : node.getConnectingNodes().entrySet()) {
            GraphNode nodeConnection = entry.getValue();
            Side connectionSide = entry.getKey();
            if (nodeConnection.wasEdge()) {
                handleDeEdging(nodeConnection);
            }
        }
    }

    /**
     * Handles converting a node from an edge into an edge and a junction (or similar)
     *
     * @param oldEdge The old node that is now being updated
     */
    private void handleDeEdging(GraphNode oldEdge) {
        int oldId = oldEdge.getNodeId();
        Vector3i currentPos = oldEdge.getFrontPos();
        Vector3i edgeBack = oldEdge.getBackPos();
        BlockGraph graph = graphManager.getGraphInstance(oldEdge.getGraphUri());
        graph.removeNode(oldEdge);
        Vector3i priorPos = null;
        Set<GraphNode> nodesToRelink = new HashSet<>();

        GraphNode currentNode = graph.createNode(worldProvider.getBlock(currentPos).getURI());
        currentNode.setFrontPos(currentPos);
        currentNode.setBackPos(currentPos);
        while (currentPos != edgeBack) { /* Loop until we reach the end of the edge */
            Map<Side, Integer> nodeMap = getNeighbouringNodes(currentPos, graph.getUri());
            if (nodeMap.size() == 0) { /* There is only one node in the edge */
                // This shouldn't be possible but may as well handle it
                break;
            } else if (nodeMap.size() <= 2) { /* If the current block only has two connections, we can link it */
                GraphNodeComponent nodeComponent = blockEntityRegistry
                        .getBlockEntityAt(currentPos)
                        .getComponent(GraphNodeComponent.class);
                nodeComponent.nodeId = currentNode.getNodeId();
                currentNode.setBackPos(currentPos);

            } else { /* We have multiple connections. So we make a junction and continue */
                /* TODO: Need to possibly re-structure edges into a separate class to handle the intricacies like how they connect (ie, by what side)
                 * TODO: Possibly a alternative abstract class to NODE extending form it, or from a common interface
                 * TODO: Issue arises from the fact that both connections to an edge can be the same and thus clash in the nodes list.
                 * TODO: Possibly just use Side.FRONT & Side.BACK but this would be overloading the class and honestly just confusing
                 * TODO: Having to do logic for the edge is also confusing. Move methods to static and call from node type?`
                 */
                GraphNode junctionNode = graph.createNode(worldProvider.getBlock(currentPos).getURI());
                nodesToRelink.add(junctionNode);
                GraphNodeComponent nodeComponent = blockEntityRegistry
                        .getBlockEntityAt(currentPos)
                        .getComponent(GraphNodeComponent.class);
                nodeComponent.nodeId = junctionNode.getNodeId();

                Vector3i[] newPos = incrementPos(currentPos, priorPos, nodeMap, oldId);
                currentPos = newPos[0];
                priorPos = newPos[1];

                currentNode = graph.createNode(worldProvider.getBlock(currentPos).getURI());

            }
            /* This is kinda ugly honestly */
            Vector3i[] newPos = incrementPos(currentPos, priorPos, nodeMap, oldId);
            currentPos = newPos[0];
            priorPos = newPos[1];
        }

        nodesToRelink.forEach(this::linkToNeighbourNodes);
    }

    /**
     * @param currentPos The current position being scanned
     * @param priorPos   The prior position, or null otherwise
     * @param nodeMap    A map of all the connections
     * @param oldId      The ID of the old edge that is being replaced
     * @return The new position to scan and the old position
     */
    private Vector3i[] incrementPos(Vector3i currentPos, Vector3i priorPos, Map<Side, Integer> nodeMap, int oldId) {
        /* Remove all the connections that are not part of the original edge */
        nodeMap = nodeMap.entrySet()
                .stream()
                .filter(entry -> entry.getValue() == oldId)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        /* Remove the way we came */
        if (priorPos != null && nodeMap.size() == 2) { /* If we are not on the first iteration, or last iteration */
            nodeMap.remove(
                    Side.inDirection(
                            priorPos.x - currentPos.x, //If there is an error here, flip the subtraction
                            priorPos.y - currentPos.y,
                            priorPos.z - currentPos.z));
        }

        /* Move in the last remaining direction */
        return new Vector3i[]{currentPos,
                nodeMap.keySet()
                        .iterator()
                        .next()
                        .getAdjacentPos(priorPos)};
    }

    /**
     * Gets the id's of any neighbouring nodes to this one.
     * Nodes are only counted if they are a part of the same graph
     * <p>
     * Also note that each block in an edge node will count.
     * This means that the result might return the same node for multiple sides if that is the case
     *
     * @param nodePos  The position to scan around
     * @param graphUri The URI of the graph to check against.
     * @return A mapping between the side and the node's ID
     */
    private Map<Side, Integer> getNeighbouringNodes(Vector3i nodePos, GraphUri graphUri) {
        Vector3i sidePos = new Vector3i();
        Map<Side, Integer> sideNodes = new HashMap<>(7);

        for (Side side : Side.values()) {
            sidePos.set(nodePos).add(side.getVector3i());
            GraphNodeComponent nodeComponent = blockEntityRegistry.getBlockEntityAt(sidePos)
                    .getComponent(GraphNodeComponent.class);
            if (nodeComponent != null && nodeComponent.graphUri == graphUri) {
                sideNodes.put(side, nodeComponent.nodeId);
            }
        }

        return sideNodes;
    }

    /**
     * Checks and updates any node connections around a given node
     *
     * @param checkingNode The node to update around
     */
    private void linkToNeighbourNodes(GraphNode checkingNode) {
        Map<Side, Integer> nodeMap = getNeighbouringNodes(checkingNode.getWorldPos(), checkingNode.getGraphUri());
        for (Map.Entry<Side, Integer> entry : nodeMap.entrySet()) {
            GraphNode otherNode = graphManager.getGraphNode(checkingNode.getGraphUri(), entry.getValue());
            checkingNode.linkNode(otherNode);
        }
    }


    /**
     * Adds a single block to the new graph, and handle potential effects of this by updating any involved graphs
     *
     * @return The URI's of all the graphs that were updated
     */
    public List<GraphUri> addBlockToGraph() {
        //TODO: Implement
        return null;
    }

    /**
     * Removes a block from the given graph and handles any effects of this, by updating any involved graphs
     *
     * @return The URI's of all the graphs that were updated, deleted or created
     */
    public List<GraphUri> removeBlockFromGraph() {
        //TODO: Implement
        return null;
    }
}

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

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        checkNeighboursFor(newNode);
        updateNodeConnections(newNode);
    }

    /**
     * Adds the block at the given point to the graph
     *
     * @param position    The position of the block to add
     * @param targetGraph The graph toa dd the block too
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
                GraphNode newNode = graphManager.getGraphInstance(nodeConnection.getGraphUri())
                        .createNode(nodeConnection.getBlockForNode());
                Vector3i splitPos = new Vector3i(node.getWorldPos()).add(connectionSide.getVector3i());
                newNode.setWorldPos(splitPos);

            }
        }
    }

    /**
     * Checks and updates any node connections around a given node
     *
     * @param checkingNode The node to update around
     */
    private void checkNeighboursFor(GraphNode checkingNode) {
        Vector3i position = new Vector3i();
        for (Side side : Side.values()) {
            position.set(checkingNode.getWorldPos()).add(side.getVector3i());
            GraphNodeComponent nodeComponent = blockEntityRegistry.getBlockEntityAt(position)
                    .getComponent(GraphNodeComponent.class);

            /* If the neighbouring block is a node in this same graph update both */
            if (nodeComponent != null && nodeComponent.graphUri == checkingNode.getGraphUri()) {
                GraphNode otherNode = graphManager.getGraphNode(nodeComponent.graphUri, nodeComponent.nodeId);
                checkingNode.linkNode(otherNode);
            }
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

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
package org.terasology.blockGraphs.graphDefinitions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.blockGraphs.graphDefinitions.nodes.BlankNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.JunctionNode;
import org.terasology.math.Side;
import org.terasology.world.block.BlockUri;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an instance of a block graph.
 * All block graphs use this class, but have differing {@link JunctionNode} implementations
 */
public class BlockGraph {
    private static final Logger logger = LoggerFactory.getLogger(BlockGraph.class);

    private GraphType graphType;
    private GraphUri uri;

    private int nextId = 1;
    private Map<Integer, JunctionNode> nodes = new HashMap<>();

    public BlockGraph(GraphType graphType, GraphUri uri) {
        this.graphType = graphType;
        this.uri = uri;
        nodes.put(0, BlankNode.BLANK_NODE);
    }

    public JunctionNode getNode(int id) {
        return nodes.get(id);
    }

    public GraphUri getUri() {
        return uri;
    }

    public GraphType getGraphType() {
        return graphType;
    }

    /**
     * Creates a new node for the given block.
     * <p>
     * Will fail if there is no node type linked to the block or the node type could not be instantiated.
     * In the latter case, a {@link BlankNode} implementation is returned
     *
     * @param block The block to create a node for
     * @return The new node type
     */
    public JunctionNode createNode(BlockUri block) {
        try {
            JunctionNode newNode = graphType.getNodeForBlock(block).newInstance();
            newNode.setNodeId(nextId++);
            return newNode;
        } catch (InstantiationException | IllegalAccessException e) {
            logger.error("Unable to create instance of node", e);
            return BlankNode.BLANK_NODE;
        }
    }

    /**
     * Handles the removal of the node from the graph
     *
     * @param node The node to remove
     */
    public void removeNode(JunctionNode node) {
        for (Side side : node.getConnectingNodes().keySet()) {
            node.unlinkNode(side);
        }
        nodes.remove(node.getNodeId());
    }
}

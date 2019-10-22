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
import org.terasology.blockGraphs.graphDefinitions.nodes.EdgeNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.GraphNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.JunctionNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.NodeType;
import org.terasology.blockGraphs.graphDefinitions.nodes.TerminusNode;
import org.terasology.world.block.BlockUri;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents an instance of a block graph.
 * All block graphs use this class, but have differing {@link JunctionNode} implementations
 */
public class BlockGraph {
    private static final Logger logger = LoggerFactory.getLogger(BlockGraph.class);

    private GraphType graphType;
    private GraphUri uri;

    private int nextId = 1;
    private Map<Integer, GraphNode> nodes = new HashMap<>();

    public BlockGraph(GraphType graphType, GraphUri uri) {
        this.graphType = graphType;
        this.uri = uri;
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public GraphNode getNode(int id) {
        return nodes.get(id);
    }

    public Set<Integer> getNodeIds() {
        return nodes.keySet();
    }

    public <T extends GraphNode> T getNodeAsType(int id) {
        return (T) getNode(id);
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
     *
     * @param block The block to create a node for
     * @return The new node type
     */
    public JunctionNode createJunctionNode(BlockUri block) {
        return createJunctionNode(graphType.getDefinitionId(block));
    }

    public JunctionNode createJunctionNode(int definition) {
        JunctionNode node = new JunctionNode(uri, nextId++, definition);
        nodes.put(node.nodeId, node);
        return node;
    }

    /**
     * @see #createJunctionNode(BlockUri)
     */
    public EdgeNode createEdgeNode(BlockUri block) {
        return createEdgeNode(graphType.getDefinitionId(block));
    }

    public EdgeNode createEdgeNode(int definition) {
        EdgeNode node = new EdgeNode(uri, nextId++, definition);
        nodes.put(node.nodeId, node);
        return node;
    }

    /**
     * @see #createJunctionNode(BlockUri)
     */
    public TerminusNode createTerminusNode(BlockUri block) {
        return createTerminusNode(graphType.getDefinitionId(block));
    }

    public TerminusNode createTerminusNode(int definition) {
        TerminusNode node = new TerminusNode(uri, nextId++, definition);
        nodes.put(node.nodeId, node);
        return node;
    }

    public GraphNode createNode(int definition, NodeType type) {
        switch (type) {
            case JUNCTION:
                return createJunctionNode(definition);
            case EDGE:
                return createEdgeNode(definition);
            case TERMINUS:
                return createTerminusNode(definition);
            default:
                throw new IllegalStateException("Unexpected value: " + type);
        }
    }

    public GraphNode createNode(BlockUri block, NodeType type) {
        return createNode(graphType.getDefinitionId(block), type);
    }

    /**
     * Handles the removal of the node from the graph
     *
     * @param node The node to remove
     */
    public void removeNode(GraphNode node) {
        /* Remove all connections into this node */
        node.getConnections().forEach(linked -> linked.unlinkNode(node));
        /* Remove all connections out of this node */
        node.unlinkAll();
        /* Remove the node */
        nodes.remove(node.nodeId);
        node.nodeId = -1;
    }
}

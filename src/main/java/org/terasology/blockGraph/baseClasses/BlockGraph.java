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
package org.terasology.blockGraph.baseClasses;

import org.terasology.blockGraph.GraphType;
import org.terasology.blockGraph.GraphUri;
import org.terasology.world.block.BlockUri;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an instance of a block graph.
 * All block graphs use this class, but have differing {@link GraphNode} implementations
 */
public class BlockGraph {

    private GraphType graphType;
    private GraphUri uri;

    private int nextId = 1;
    private List<GraphNode> nodes = new ArrayList<>();

    public BlockGraph(GraphType graphType, GraphUri uri) {
        this.graphType = graphType;
        this.uri = uri;
    }

    public GraphNode getNode(int id) {
        return nodes.get(id);
    }

    public GraphUri getUri() {
        return uri;
    }

    public GraphType getGraphType() {
        return graphType;
    }

    /**
     * Ease of use wrapper method.
     *
     * @see GraphType#getNodeForBlock(BlockUri)
     */
    public Class<? extends GraphNode> getNodeForBlock(BlockUri block) {
        return graphType.getNodeForBlock(block);
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
    public GraphNode createNode(BlockUri block) {
        try {
            //TODO: Actually implement this
            return graphType.getNodeForBlock(block).newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            return BlankNode.BLANK_NODE;
        }
    }
}

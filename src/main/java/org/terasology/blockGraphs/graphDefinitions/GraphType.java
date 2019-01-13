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

import org.terasology.blockGraphs.graphDefinitions.nodeDefinitions.NodeDefinition;
import org.terasology.blockGraphs.graphDefinitions.nodes.GraphNode;
import org.terasology.engine.SimpleUri;
import org.terasology.world.block.BlockUri;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stores information about a specific graph type
 * Notably stores information about the specific block to node type linking
 */
public class GraphType {
    /* Static information for graph type */
    private List<NodeDefinition> nodeDefinitions = new ArrayList<>();
    private Map<BlockUri, Integer> blockMapping = new HashMap<>();
    private SimpleUri uri;
    //Todo: Set all blocks keep active

    public GraphType(SimpleUri uri) {
        this.uri = uri;
    }

    /**
     * Adds a new node type to this graph.
     * Requires an instance of the node type due to an inability to declare a method abstract & static
     *
     * @param nodeType An instance of the new node type.
     */
    public void addNodeType(NodeDefinition nodeType) {
        int index = nodeDefinitions.size();
        nodeDefinitions.add(nodeType);
        blockMapping.put(nodeType.getBlockForNode(), index);
    }

    /**
     * Get the graph node linked to that block type.
     * Will error if there is no class linked to the block type
     *
     * @param block The URI of the block to look up
     * @return The class for that node, or null if there is none
     */
    public NodeDefinition getDefinition(BlockUri block) {
        return nodeDefinitions.get(blockMapping.get(block));
    }

    public NodeDefinition getDefinition(GraphNode node) {
        return nodeDefinitions.get(node.getDefinitionId());
    }

    public NodeDefinition getDefinition(int id) {
        return nodeDefinitions.get(id);
    }

    public int getDefinitionId(BlockUri block) {
        return blockMapping.get(block);
    }

    public SimpleUri getUri() {
        return uri;
    }

    public Set<BlockUri> getAllBlocks() {
        return blockMapping.keySet();
    }
}


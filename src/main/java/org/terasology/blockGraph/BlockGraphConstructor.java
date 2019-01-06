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
package org.terasology.blockGraph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.blockGraph.baseClasses.BlockGraph;
import org.terasology.blockGraph.baseClasses.GraphNode;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.math.geom.Vector3f;
import org.terasology.registry.In;
import org.terasology.registry.Share;
import org.terasology.world.BlockEntityRegistry;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockManager;
import org.terasology.world.block.BlockUri;

import java.util.List;
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

    }

    /**
     * Adds the block at the given point to the graph
     *
     * @param position    The position of the block to add
     * @param targetGraph The graph toa dd the block too
     */
    private void addPointToGraph(Vector3f position, BlockGraph targetGraph) {
        BlockUri block = worldProvider.getBlock(position).getURI();

        GraphNode newNode = targetGraph.createNode(block);
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

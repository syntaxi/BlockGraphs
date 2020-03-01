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
package org.terasology.blockGraphs.graphDefinitions.nodeDefinitions;

import org.terasology.blockGraphs.graphDefinitions.NodeRef;
import org.terasology.blockGraphs.graphDefinitions.nodes.EdgeSide;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.math.Side;
import org.terasology.world.block.BlockUri;

public interface NodeDefinition {
    /**
     * To simulate data being passed through the system slowly, data can be held at a node for a period of time
     *
     * @param node The node being processed
     * @return The number of milliseconds to hold the data at the node for
     */
    default int holdDataFor(NodeRef node) {
        return -1;
    }

    /**
     * @return The block type that this node should be linked to
     */
    BlockUri getBlockForNode();

    /**
     * Called when the data enters the node to allow the implementations to modify the data if they wish.
     * This includes when the data initially enters a network. In this case, <code>entry</code> will be null
     * <p>
     * This is called <i>before</i>
     * - {@link #processEdge(NodeRef, EntityRef, EdgeSide)}
     * - {@link #processTerminus(NodeRef, EntityRef)}
     * - or {@link #processJunction(NodeRef, EntityRef, Side)}
     *
     * @param node  The node being processed
     * @param data  The data entering
     * @param entry The side the data has entered by
     */
    default void dataEnterNode(NodeRef node, EntityRef data, Side entry) {
    }

    /**
     * Returns which side the data should move into
     * <p>
     * This is called <i>after</i> {@link #dataEnterNode(NodeRef, EntityRef, Side)}
     *
     * @param node  The node being processed
     * @param data  The data being moved
     * @param entry The side the data entered via
     * @return The side the data should leave by or null if the data should leave the network
     */
    Side processJunction(NodeRef node, EntityRef data, Side entry);

    /**
     * Returns which side the data should leave from.
     * Only applies when the node is in edge format
     * <p>
     * This is called <i>after</i> {@link #dataEnterNode(NodeRef, EntityRef, Side)}
     *
     * @param node  The node being processed
     * @param data  The data being moved
     * @param entry The end of the node entered by. Either FRONT or BACK
     * @return The side to leave by
     */
    EdgeSide processEdge(NodeRef node, EntityRef data, EdgeSide entry);

    /**
     * Returns if the data should exit the network or bounce back the way it came
     * Only applies if the ndoe is in terminus format
     * <p>
     * This is called <i>after</i> {@link #dataEnterNode(NodeRef, EntityRef, Side)}
     *
     * @param node The node being processed
     * @param data The data being moved
     * @return True if the data should leave and false if the data should leave the way it entered
     */
    boolean processTerminus(NodeRef node, EntityRef data);

}

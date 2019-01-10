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
package org.terasology.blockGraphs.graphDefinitions.nodes;

import org.terasology.blockGraphs.dataMovement.EdgeMovementOptions;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.math.Side;
import org.terasology.world.block.BlockUri;

public interface NodeType {
    /**
     * To simulate data being passed through the system slowly, data can be held at a node for a period of time
     *
     * @param node The node being processed
     * @return The number of milliseconds to hold the data at the node for
     */
    default int holdDataFor(EntityRef node) {
        return -1;
    }

    /**
     * @return The block type that this node should be linked to
     */
    BlockUri getBlockForNode();

    /**
     * Called when the data enters the node to allow the implementations to modify the data if they wish
     * <p>
     * This is called <i>before</i>
     * - {@link #processEdge(EntityRef, EntityRef, Side)}
     * - {@link #processTerminus(EntityRef, EntityRef)}
     * - or {@link #processJunction(EntityRef, EntityRef, Side)}
     *
     * @param node  The node being processed
     * @param data  The data entering
     * @param entry The side the data has entered by
     */
    default void dataEnterNode(EntityRef node, EntityRef data, Side entry) {
    }

    /**
     * Called when the data is initially inserted into the network via this node
     * Should determine which side the data should leave by
     *
     * @param node The node being processed
     * @param data The data being entered
     * @return The side the data should leave by or null if the data should leave the network
     */
    default Side dataEnterNetwork(EntityRef node, EntityRef data) {
        return processJunction(node, data, null);
    }

    /**
     * Returns which side the data should move into
     * <p>
     * This is called <i>after</i> {@link #dataEnterNode(EntityRef, EntityRef, Side)}
     *
     * @param node  The node being processed
     * @param data  The data being moved
     * @param entry The side the data entered via
     * @return The side the data should leave by or null if the data should leave the network
     */
    Side processJunction(EntityRef node, EntityRef data, Side entry);

    /**
     * Returns which side the data should leave from.
     * Only applies when the node is in edge format
     * <p>
     * This is called <i>after</i> {@link #dataEnterNode(EntityRef, EntityRef, Side)}
     *
     * @param node  The node being processed
     * @param data  The data being moved
     * @param entry The side entered by
     * @return The side to leave by
     */
    EdgeMovementOptions processEdge(EntityRef node, EntityRef data, Side entry);

    /**
     * Processes the case where the data has just entered a junction node.
     * <p>
     * This is called <i>after</i> {@link #dataEnterNode(EntityRef, EntityRef, Side)}
     *
     * @param node The node being processed
     * @param data The data being moved
     * @return True if the data should leave and false if the data should leave the way it entered
     */
    boolean processTerminus(EntityRef node, EntityRef data);

}

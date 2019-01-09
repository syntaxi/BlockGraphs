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
import org.terasology.utilities.random.FastRandom;
import org.terasology.world.block.BlockUri;

import java.util.ArrayList;

/**
 * The most basic implementation of a node.
 * Simply randomly routes data in junctions and straight through otherwise
 */
public class BaseGraphNode extends GraphNode {

    @Override
    public BlockUri getBlockForNode() {
        return null;
    }

    @Override
    public void dataEnterNode(EntityRef data, Side entry) {
        // Do nothing
    }

    @Override
    public Side dataEnterNetwork(EntityRef data) {
        return processJunction(data, null);
    }

    @Override
    public Side processJunction(EntityRef data, Side entry) {
        /* Return a random side out of the available options */
        return new FastRandom().nextItem(new ArrayList<>(getConnectingNodes().keySet()));
    }

    @Override
    public EdgeMovementOptions processEdge(EntityRef data, Side entry) {
        /* Choose opposite option */
        return EdgeMovementOptions.OTHER;
    }

    @Override
    public boolean processTerminus(EntityRef data) {
        /* Always pop the data out */
        return true;
    }
}

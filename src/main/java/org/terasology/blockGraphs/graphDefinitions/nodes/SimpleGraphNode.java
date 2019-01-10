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
public class SimpleGraphNode implements NodeDefinition {

    public BlockUri getBlockForNode() {
        return null;
    }

    public Side dataEnterNetwork(EntityRef node, EntityRef data) {
        return processJunction(node, data, null);
    }

    public Side processJunction(EntityRef node, EntityRef data, Side entry) {
        /* Return a random side out of the available options */
        return new FastRandom().nextItem(new ArrayList<>(getConnectingNodes().keySet()));
    }

    public EdgeMovementOptions processEdge(EntityRef node, EntityRef data, Side entry) {
        /* Choose opposite option. Ie, move data through */
        return EdgeMovementOptions.OTHER;
    }

    public boolean processTerminus(EntityRef node, EntityRef data) {
        /* Always pop the data out */
        return true;
    }
}

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
package org.terasology.blockGraph.graphDefinitions.nodes;

import org.terasology.blockGraph.dataMovement.EdgeMovementOptions;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.math.Side;
import org.terasology.world.block.BlockUri;

/**
 * Blank implementation of GraphNode
 * Is a singleton to avoid creating an unnecessary amount of blank instances
 */
public final class BlankNode extends GraphNode {
    public static final BlankNode BLANK_NODE = new BlankNode();

    private BlankNode() {

    }

    @Override
    public BlockUri getBlockForNode() {
        return null;
    }

    @Override
    public void dataEnterNode(EntityRef data, Side entry) {

    }

    @Override
    public Side dataEnterNetwork(EntityRef data) {
        return null;
    }

    @Override
    public Side processJunction(EntityRef data, Side entry) {
        return null;
    }

    @Override
    public EdgeMovementOptions processEdge(EntityRef data, Side entry) {
        return null;
    }

    @Override
    public boolean processTerminus(EntityRef data) {
        return false;
    }
}
/*
 * Copyright 2019 MovingBlocks
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
package org.terasology.blockGraphs;

import org.terasology.blockGraphs.graphDefinitions.nodes.JunctionNode;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.math.Side;
import org.terasology.world.block.BlockUri;

public class TestLeftDefinition extends TestDefinition {
    public static final BlockUri BLOCK_URI = new BlockUri("BlockGraphs:TestLeftBlock");

    @Override
    public BlockUri getBlockForNode() {
        return BLOCK_URI;
    }

    @Override
    public Side processJunction(JunctionNode node, EntityRef data, Side entry) {
        return Side.LEFT;
    }
}

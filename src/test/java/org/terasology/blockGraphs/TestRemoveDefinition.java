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

import org.terasology.blockGraphs.graphDefinitions.NodeRef;
import org.terasology.blockGraphs.graphDefinitions.nodes.EdgeNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.EdgeSide;
import org.terasology.blockGraphs.graphDefinitions.nodes.GraphNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.JunctionNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.TerminusNode;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.math.Side;
import org.terasology.world.block.BlockUri;

public class TestRemoveDefinition extends TestUpwardsDefinition {
    public static final BlockUri BLOCK_URI = new BlockUri("BlockGraphs:TestRemovalBlock");

    private boolean shouldEject = false;

    @Override
    public BlockUri getBlockForNode() {
        return BLOCK_URI;
    }

    @Override
    public void dataEnterNode(NodeRef node, EntityRef data, Side entry) {
        super.dataEnterNode(node, data, entry);
        if (data.hasComponent(EjectOnTriggerComponent.class)) {
            shouldEject = data.getComponent(EjectOnTriggerComponent.class)
                    .trigger.test(node);
        }
    }

    @Override
    public boolean processTerminus(NodeRef node, EntityRef data) {
        if (shouldEject) {
            return true;
        }
        return super.processTerminus(node, data);
    }

    @Override
    public EdgeSide processEdge(NodeRef node, EntityRef data, EdgeSide entry) {
        if (shouldEject) {
            return null;
        }
        return super.processEdge(node, data, entry);
    }

    @Override
    public Side processJunction(NodeRef node, EntityRef data, Side entry) {
        if (shouldEject) {
            return null;
        }
        return super.processJunction(node, data, entry);
    }
}

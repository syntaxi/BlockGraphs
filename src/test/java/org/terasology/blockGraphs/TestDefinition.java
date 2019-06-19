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

import org.terasology.blockGraphs.dataMovement.GraphPositionComponent;
import org.terasology.blockGraphs.graphDefinitions.nodeDefinitions.NodeDefinition;
import org.terasology.blockGraphs.graphDefinitions.nodes.EdgeNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.EdgeSide;
import org.terasology.blockGraphs.graphDefinitions.nodes.GraphNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.TerminusNode;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.math.Side;

public abstract class TestDefinition implements NodeDefinition {

    @Override
    public void dataEnterNode(GraphNode node, EntityRef data, Side entry) {
        NodePathTestComponent component = data.getComponent(NodePathTestComponent.class);
        component.nodePath.add(data.getComponent(GraphPositionComponent.class).currentNode);
    }

    @Override
    public EdgeSide processEdge(EdgeNode node, EntityRef data, EdgeSide entry) {
        return entry.getOpposite(); //Move through
    }

    @Override
    public boolean processTerminus(TerminusNode node, EntityRef data) {
        GraphPositionComponent graphComponent = data.getComponent(GraphPositionComponent.class);
        if (graphComponent.isEntering) {
            return false; //bounce back
        } else {
            NodePathTestComponent pathComponent = data.getComponent(NodePathTestComponent.class);
            pathComponent.isFinished = true;
            return true; //exit
        }
    }
}

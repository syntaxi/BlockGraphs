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
package org.terasology.blockGraph.dataMovement;

import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import org.terasology.blockGraph.graphDefinitions.nodes.GraphNode;
import org.terasology.engine.Time;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.math.Side;
import org.terasology.registry.In;
import org.terasology.registry.Share;

import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap;

/**
 * Handles moving all the data through the graphs.
 * <p>
 * We don't use the DelayManager system to avoid polluting that with both small duration requests and large numbers of requests
 */
@Share(GraphMovementSystem.class)
@RegisterSystem
public class GraphMovementSystem extends BaseComponentSystem implements UpdateSubscriberSystem {

    private SortedMap<Long, EntityRef> delays = Maps.newTreeMap(Ordering.natural());

    @In
    private Time time;
    private List<EntityRef> entitiesToProcess = new LinkedList<>();

    @Override
    public void update(float delta) {
        if (time.getGameTimeInMs() >= delays.firstKey()) {
            /* Get all entities to process this frame */
            do {
                entitiesToProcess.add(delays.remove(delays.firstKey()));
            } while (time.getGameTimeInMs() >= delays.firstKey());

            /* Process them */
            entitiesToProcess.stream().filter(EntityRef::exists).forEach(this::handlePackage);
        }
    }

    /**
     * Adds data into the network, specifically to be moved around
     *
     * @param data        The data to add
     * @param currentNode The node to add at
     */
    public void insertData(EntityRef data, GraphNode currentNode) {
        Side nextDirection = currentNode.dataEnterNetwork(data);
        GraphPositionComponent component = new GraphPositionComponent();

        component.currentNode = currentNode;
        component.currentDirection = null;
        data.addOrSaveComponent(component);

        moveToNode(data, nextDirection);
    }


    /**
     * Move the data to the next node and then queues up the next movement.
     * <p>
     * This is done in the following steps:
     * <p>
     * 1. The data is moved to the new node
     * 2. The new node is queried for how to route the data
     * 3. The next node movement is stored in the component
     *
     * @param data The data being moved
     */
    private void handlePackage(EntityRef data) {
        /* 'move' the data to the next node */
        GraphPositionComponent component = data.getComponent(GraphPositionComponent.class);
        component.currentNode = component.nextNode;
        component.currentDirection = component.nextDirection;

        /* Allow the data to operate on the node */
        component.currentNode.dataEnterNode(data, component.currentDirection);

        /* Query the now current node for the next movement */
        if (component.currentNode.isEdge()) {
            switch (component.currentNode.processEdge(data, component.currentDirection)) {
                case SAME:
                    moveToNode(data, component.currentDirection);
                    break;
                case LEAVE:
                    /* There are only two connections so the one that doesn't match current is the option we want */
                    for (Side side : component.currentNode.getConnectingNodes().keySet()) {
                        if (side != component.currentDirection) {
                            moveToNode(data, component.currentDirection.reverse());
                        }
                    }
                    break;
                case OTHER:
                default:
                    removeFromNetwork(data);
            }
        } else if (component.currentNode.isTerminus()) {
            if (component.currentNode.processTerminus(data)) {
                removeFromNetwork(data);
            } else {
                /* There is only on connection so bounce back the way it came in */
                moveToNode(data, component.currentDirection);
            }
        } else {
            Side side = component.currentNode.processJunction(data, component.currentDirection);
            if (side == null) {
                removeFromNetwork(data);
            } else {
                moveToNode(data, side);
            }
        }
    }

    /**
     * Moves the data to the node connected on the specified side
     *
     * @param data        The data to move
     * @param leavingSide The side to leave via
     */
    private void moveToNode(EntityRef data, Side leavingSide) {
        GraphPositionComponent positionComponent = data.getComponent(GraphPositionComponent.class);
        GraphNode currentNode = positionComponent.currentNode;

        positionComponent.nextNode = currentNode.getConnectingNodes().get(leavingSide);
        positionComponent.nextDirection = leavingSide.reverse();

        entitiesToProcess.add(currentNode.holdDataFor(), data);
    }

    private void removeFromNetwork(EntityRef data) {
        data.removeComponent(GraphPositionComponent.class);
        //TODO: Implement
    }
}

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
package org.terasology.blockGraphs.dataMovement;

import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import org.terasology.blockGraphs.BlockGraphManager;
import org.terasology.blockGraphs.graphDefinitions.nodeDefinitions.NodeDefinition;
import org.terasology.blockGraphs.graphDefinitions.nodes.EdgeNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.GraphNode;
import org.terasology.engine.Time;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
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
 * </p>
 * <p>
 * Firstly, the data enters the movement system. This is done through the {@link #insertData(GraphNode, EntityRef, NodeDefinition)} method.
 * This will invoke the initial {@link NodeDefinition#dataEnterNode(GraphNode, EntityRef, Side)} method.
 * Then the data is queried for the next node to progress to using the {@link NodeDefinition#dataEnterNetwork(GraphNode, EntityRef)} method.
 * This method is only called when the data initially enters the network, and defaults to the processJunction method detailed below
 * <p>
 * The data delay is then obtained and the data is added to the mapping.
 * <p>
 * When the delay is up, the data is taken out of the map, the data is moved to the next location the dataEnterNode
 * method is called, and the cycle repeats.
 * The dataEnterNetwork method is only used when the data initally enters the network, in all other cases one of the
 * following methods are used (depending on which is appropriate for the node).
 * 1. {@link NodeDefinition#processJunction(GraphNode, EntityRef, Side)}, if the node has 3 or more connections.
 * 2. {@link NodeDefinition#processEdge(EdgeNode, EntityRef, Side)}, if the node is a edge (exactly 2 connections)
 * 3. {@link NodeDefinition#processTerminus(GraphNode, EntityRef)}, if the node is a terminus, (exactly 1 connection)
 * </p>
 * <p>
 * The next location to move ot is calculated before the delay is held is because the delay is intended to simulate
 * the data having a travel time between the nodes. Thus if the node is altered before the data is able to reach the
 * destination, it is immediately ejected from the network and node, with the appropriate notifying methods being called.
 * <p>
 * It makes some sense to move the next node calculations to after the delay, as to allow any changes to the graph
 * to be processed. This however, just complicates things as well as clashing with the intended purpose of a delay
 * as detailed above
 */
@Share(GraphMovementSystem.class)
@RegisterSystem(RegisterMode.AUTHORITY)
public class GraphMovementSystem extends BaseComponentSystem implements UpdateSubscriberSystem {

    private SortedMap<Long, EntityRef> delays = Maps.newTreeMap(Ordering.natural());

    @In
    private Time time;
    private List<EntityRef> entitiesToProcess = new LinkedList<>();

    @In
    private BlockGraphManager graphManager;

    @Override
    public void update(float delta) {
        if (!delays.isEmpty() && time.getGameTimeInMs() >= delays.firstKey()) {
            /* Get all entities to process this frame */
            do {
                entitiesToProcess.add(delays.remove(delays.firstKey()));
            } while (!delays.isEmpty() && time.getGameTimeInMs() >= delays.firstKey());

            /* Process them */
            for (EntityRef entity : entitiesToProcess) {
                if (entity.exists()) {
                    handlePackage(entity);
                }
            }
            entitiesToProcess.clear();
        }
    }


    /**
     * Adds data into the network, specifically to be moved around
     *
     * @param node        The node to insert the data at
     * @param data        The data to add
     * @param currentNode The definition for the node being added
     */
    public void insertData(GraphNode node, EntityRef data, NodeDefinition currentNode) {
        GraphPositionComponent component = new GraphPositionComponent();
        component.currentNode = node.getNodeId();
        component.currentDirection = null;
        component.graph = node.getGraphUri();
        data.addOrSaveComponent(component);

        /* Initial data processing */
        currentNode.dataEnterNode(node, data, null);
        Side nextDirection = currentNode.dataEnterNetwork(node, data);

        /* Calculate where the data should be moved to */
        setNextNode(data, nextDirection, currentNode.holdDataFor(node));
    }

    /**
     * Move the data to the next node
     *
     * @param data The data to move
     * @return The component holding the position of the data
     */
    private GraphPositionComponent moveToNextNode(EntityRef data) {
        GraphPositionComponent component = data.getComponent(GraphPositionComponent.class);
        component.currentNode = component.nextNode;
        component.currentDirection = component.nextDirection;
        component.nextDirection = null;
        component.nextNode = -1;
        return component;
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
        /* Move the data to the next node */
        GraphPositionComponent component = moveToNextNode(data);

        GraphNode currentNode = graphManager.getGraphNode(component.graph, component.currentNode);
        NodeDefinition nodeDefinition = graphManager.getNodeDefinition(component.graph, component.currentNode);

        /* Allow the node ot operate on the data */
        nodeDefinition.dataEnterNode(currentNode, data, component.currentDirection);

        /* Query the now current node for the next movement */
        if (currentNode instanceof EdgeNode) {
            treatAsEdge(data, component, currentNode, nodeDefinition);
        } else if (currentNode.isTerminus()) {
            treatAsTerminus(data, component, currentNode, nodeDefinition);
        } else {
            treatAsJunction(data, component, currentNode, nodeDefinition);
        }
    }


    /**
     * Queries the node for the next position to move to & handles scheduling a movement there.
     * Treats the node as a Junction. This is a node with three or more connections
     *
     * @param data           The data to process
     * @param component      The position component on the data
     * @param currentNode    The node being moved around
     * @param nodeDefinition The definition used for the node
     */
    private void treatAsJunction(EntityRef data, GraphPositionComponent component, GraphNode currentNode, NodeDefinition nodeDefinition) {
        Side side = nodeDefinition.processJunction(currentNode, data, component.currentDirection);
        if (side == null) {
            removeFromNetwork(data);
        } else {
            setNextNode(data, side, nodeDefinition.holdDataFor(currentNode));
        }
    }

    /**
     * Queries the node for the next position to move to & handles scheduling a movement there.
     * Treats the node as a Junction. This is a node with exactly two connections.
     * This node is also unique in that it can be multiple real world blocks long.
     *
     * @param data           The data to process
     * @param component      The position component on the data
     * @param currentNode    The node being moved around
     * @param nodeDefinition The definition used for the node
     */
    private void treatAsEdge(EntityRef data, GraphPositionComponent component, GraphNode currentNode, NodeDefinition nodeDefinition) {
        switch (nodeDefinition.processEdge((EdgeNode) currentNode, data, component.currentDirection)) {
            case SAME:
                setNextNode(data, component.currentDirection, nodeDefinition.holdDataFor(currentNode));
                break;
            case OTHER:
                /* There are only two connections so the one that doesn't match current is the option we want */
                for (Side side : currentNode.getConnectingNodes().keySet()) {
                    if (side != component.currentDirection) {
                        setNextNode(data, component.currentDirection.reverse(), nodeDefinition.holdDataFor(currentNode));
                    }
                }
                break;
            case LEAVE:
            default:
                removeFromNetwork(data);
        }
    }

    /**
     * Queries the node for the next position to move to & handles scheduling a movement there.
     * Treats the node as a Terminus. This is a node with only one connection
     *
     * @param data           The data to process
     * @param component      The position component on the data
     * @param currentNode    The node being moved around
     * @param nodeDefinition The definition used for the node
     */
    private void treatAsTerminus(EntityRef data, GraphPositionComponent component, GraphNode currentNode, NodeDefinition nodeDefinition) {
        if (nodeDefinition.processTerminus(currentNode, data)) {
            removeFromNetwork(data);
        } else {
            /* There is only on connection so bounce back the way it came in */
            setNextNode(data, component.currentDirection, nodeDefinition.holdDataFor(currentNode));
        }
    }


    /**
     * Sets the node this data will move into after the delay.
     * This does not actually move the data into this node, it just sets up where it will be moving to.
     * <p>
     * NOTE: This does not perform a check to see if there is a node connected to that side.
     * This _will_ throw an error if the side does not have a node attached.
     *
     * @param data        The data to move
     * @param leavingSide The side to leave via
     */
    private void setNextNode(EntityRef data, Side leavingSide, int holdDuration) {
        GraphPositionComponent positionComponent = data.getComponent(GraphPositionComponent.class);
        GraphNode currentNode = graphManager.getGraphNode(positionComponent.graph, positionComponent.currentNode);

        positionComponent.nextNode = currentNode.getConnectingNodes().get(leavingSide).getNodeId();
        positionComponent.nextDirection = leavingSide.reverse();

        delays.put(holdDuration + time.getGameTimeInMs(), data);
    }


    /**
     * Removes the data from the network.
     * Ensures that the data is cleanly removed from the movement system and the node/graph is updated
     *
     * @param data The data to remove
     */
    private void removeFromNetwork(EntityRef data) {
        data.removeComponent(GraphPositionComponent.class);
        //TODO: Implement
    }
}

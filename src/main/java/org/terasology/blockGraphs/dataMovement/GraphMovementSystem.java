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
import org.terasology.blockGraphs.graphDefinitions.NodeRef;
import org.terasology.blockGraphs.graphDefinitions.nodeDefinitions.NodeDefinition;
import org.terasology.blockGraphs.graphDefinitions.nodes.EdgeSide;
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
 * We don't use the DelayManager system to avoid polluting that with both small duration requests and large numbers of requests
 * <p>
 * Firstly, the data enters the movement system. This is done through the {@link #insertData(NodeRef, EntityRef)} method.
 * This will invoke the initial {@link NodeDefinition#dataEnterNode(NodeRef, EntityRef, Side)} method.
 * <p>
 * Next the node is queried for the next node to move to using the appropriate of the following:
 * 1. {@link NodeDefinition#processJunction(NodeRef, EntityRef, Side)}, if the node has 3 or more connections.
 * 2. {@link NodeDefinition#processEdge(NodeRef, EntityRef, EdgeSide)}, if the node is a edge (exactly 2 connections)
 * 3. {@link NodeDefinition#processTerminus(NodeRef, EntityRef)}, if the node is a terminus, (exactly 1 connection)
 * <p>
 * The data delay is then obtained and the data is added to the mapping.
 * <p>
 * When the delay is up, the data is taken out of the map, the data is moved to the next location the dataEnterNode
 * method is called, and the cycle repeats.
 * <p>
 * The next location to move to is calculated before the delay is held is because the delay is intended to simulate
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
                    moveToNextNode(entity);
                    handlePackage(entity);
                }
            }
            entitiesToProcess.clear();
        }
    }


    /**
     * Adds data into the network, specifically to be moved around
     *
     * @param node The node to insert the data at
     * @param data The data to add
     */
    public void insertData(NodeRef node, EntityRef data) {
        GraphPositionComponent component = new GraphPositionComponent();
        component.graph = node.getGraphUri();
        component.isEntering = true;
        component.currentNode = node.getNodeId();
        component.currentDirection = null;
        component.nextNode = -1;
        component.nextDirection = null;
        data.addOrSaveComponent(component);

        /* Begin moving in network */
        handlePackage(data);
    }

    /**
     * Move the data to the next node
     *
     * @param data The data to move
     */
    private void moveToNextNode(EntityRef data) {
        GraphPositionComponent component = data.getComponent(GraphPositionComponent.class);
        component.currentNode = component.nextNode;
        component.currentDirection = component.nextDirection;
        component.nextDirection = null;
        component.isEntering = false;
        component.nextNode = -1;
    }

    /**
     * Move the data to the next node and then queues up the next movement.
     * <p>
     * This is done in the following steps:
     * <p>
     * 1. The new node is queried for how to route the data
     * 2. The next node movement is stored in the component
     * 3. It is re-added to the list for the delay processing
     *
     * @param data The data being moved
     */
    private void handlePackage(EntityRef data) {
        GraphPositionComponent component = data.getComponent(GraphPositionComponent.class);
        /* Move the data to the next node */
        NodeRef currentNode = graphManager.getGraphNode(component.graph, component.currentNode);
        NodeDefinition nodeDefinition = graphManager.getNodeDefinition(component.graph, component.currentNode);

        /* Allow the node ot operate on the data */
        nodeDefinition.dataEnterNode(currentNode, data, component.currentDirection);

        switch (currentNode.getNodeType()) {
            case JUNCTION:
                treatAsJunction(data, currentNode, component, nodeDefinition);
                break;
            case EDGE:
                treatAsEdge(data, currentNode, component, nodeDefinition);
                break;
            case TERMINUS:
                treatAsTerminus(data, currentNode, nodeDefinition);
                break;
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
    private void treatAsJunction(EntityRef data, NodeRef currentNode, GraphPositionComponent component, NodeDefinition nodeDefinition) {
        Side side = nodeDefinition.processJunction(currentNode, data, component.currentDirection);
        if (side == null) {
            removeFromNetwork(data, false);
        } else {
            setNextNode(data, currentNode, currentNode.asJunction().getNodeForSide(side), nodeDefinition.holdDataFor(currentNode));
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
    private void treatAsEdge(EntityRef data, NodeRef currentNode, GraphPositionComponent component, NodeDefinition nodeDefinition) {
        EdgeSide side = nodeDefinition.processEdge(currentNode, data, EdgeSide.fromSide(component.currentDirection));
        if (side == null) {
            removeFromNetwork(data, false);
            return;
        }
        switch (side) {
            case FRONT:
                setNextNode(data, currentNode, currentNode.asEdge().frontNode, nodeDefinition.holdDataFor(currentNode));
                break;
            case BACK:
                setNextNode(data, currentNode, currentNode.asEdge().backNode, nodeDefinition.holdDataFor(currentNode));
                break;
        }
    }


    /**
     * Queries the node for the next position to move to & handles scheduling a movement there.
     * Treats the node as a Terminus. This is a node with only one connection
     *
     * @param data           The data to process
     * @param currentNode    The node being moved around
     * @param nodeDefinition The definition used for the node
     */
    private void treatAsTerminus(EntityRef data, NodeRef currentNode, NodeDefinition nodeDefinition) {
        if (nodeDefinition.processTerminus(currentNode, data)) {
            removeFromNetwork(data, false);
        } else {
            /* There is only on connection so bounce back the way it came in */
            setNextNode(data, currentNode, currentNode.asTerminus().connectionNode, nodeDefinition.holdDataFor(currentNode));
        }
    }


    /**
     * Sets the node this data will move into after the delay.
     * This does not actually move the data into this node, it just sets up where it will be moving to.
     * <p>
     * NOTE: This does not perform a check to see if there is a node connected to that side.
     * This _will_ throw an error if the side does not have a node attached.
     *
     * @param data The data to move
     */
    private void setNextNode(EntityRef data, NodeRef currentNode, NodeRef nextNode, int holdDuration) {
        GraphPositionComponent positionComponent = data.getComponent(GraphPositionComponent.class);

        positionComponent.nextNode = nextNode.getNodeId();
        positionComponent.nextDirection = nextNode.getSideForNode(currentNode);

        delays.put(holdDuration + time.getGameTimeInMs(), data);
    }


    /**
     * Removes the data from the network.
     * Ensures that the data is cleanly removed from the movement system and the node/graph is updated
     *
     * @param data The data to remove
     */
    private void removeFromNetwork(EntityRef data, boolean wasEjected) {
        GraphPositionComponent component = data.getComponent(GraphPositionComponent.class);
        data.removeComponent(GraphPositionComponent.class);
        OnLeaveGraphEvent event = new OnLeaveGraphEvent();
        event.wasEjected = wasEjected;
        event.finalNode = graphManager.getGraphNode(component.graph, component.currentNode);
        data.send(event);
    }

}

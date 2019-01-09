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
import org.terasology.blockGraph.graphDefinitions.BlockGraph;
import org.terasology.blockGraph.graphDefinitions.GraphUri;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.math.Side;
import org.terasology.math.geom.Vector3i;
import org.terasology.world.block.BlockUri;

import java.util.HashMap;
import java.util.Map;

/**
 * The base class for all Graph Nodes.
 * Handles some basic things like calling different sub methods depending on which "mode" the node is in
 * <p>
 * Should be extended to provide specific node types
 *
 * @see BaseGraphNode
 */
public abstract class GraphNode {

    private Map<Side, GraphNode> nodes = new HashMap<>(6);
    private Vector3i worldPos;

    /**
     * The id of this node in it's {@link BlockGraph} instance
     */
    private int nodeId;

    /**
     * The URI of the graph this node belongs to
     */
    private GraphUri graphUri;

    /**
     * The position of the 'front' of the edge
     */
    private Vector3i frontPos;
    /**
     * The position of the 'back' of the edge
     */
    private Vector3i backPos;

    /**
     * To simulate data being passed through the system slowly, data can be held at a node for a period of time
     *
     * @return The number of milliseconds to hold the data at the node for
     */
    public int holdDataFor() {
        return -1;
    }

    /**
     * @return The block type that this node should be linked to
     */
    public abstract BlockUri getBlockForNode();

    /**
     * Called when the data enters the node to allow the implementations to modify the data if they wish
     * <p>
     * This is called <i>before</i>
     * - {@link #processEdge(EntityRef, Side)}
     * - {@link #processTerminus(EntityRef)}
     * - or {@link #processJunction(EntityRef, Side)}
     *
     * @param data  The data entering
     * @param entry The side the data has entered by
     */
    public abstract void dataEnterNode(EntityRef data, Side entry);

    /**
     * Called when the data is initially inserted into the network via this node
     * Should determine which side the data should leave by
     *
     * @param data The data being entered
     * @return The side the data should leave by
     */
    public abstract Side dataEnterNetwork(EntityRef data);

    /**
     * Returns which side the data should move into
     * <p>
     * This is called <i>after</i> {@link #dataEnterNode(EntityRef, Side)}
     *
     * @param data  The data being moved
     * @param entry The side the data entered via
     * @return The side the data should leave by
     */
    public abstract Side processJunction(EntityRef data, Side entry);

    /**
     * Returns which side the data should leave from.
     * Only applies when the node is in edge format
     * <p>
     * This is called <i>after</i> {@link #dataEnterNode(EntityRef, Side)}
     *
     * @param data  The data being moved
     * @param entry The side entered by
     * @return The side to leave by
     */
    public abstract EdgeMovementOptions processEdge(EntityRef data, Side entry);

    /**
     * Processes the case where the data has just entered a junction node.
     * <p>
     * This is called <i>after</i> {@link #dataEnterNode(EntityRef, Side)}
     *
     * @param data The data being moved
     * @return True if the data should leave and false if the data should leave the way it entered
     */
    public abstract boolean processTerminus(EntityRef data);

    /**
     * @return The nodes attached to this one.
     */
    public Map<Side, GraphNode> getConnectingNodes() {
        return nodes;
    }

    /**
     * Sets the positions to use for the ends of the edge
     *
     * @param front The position to use as the front of the edge
     * @param back  The position to use as the back of the edge
     */
    public void setEdgePos(Vector3i front, Vector3i back) {
        frontPos = front;
        backPos = back;
    }

    public boolean isEdge() {
        return nodes.size() == 2;
    }

    /**
     * @return True if the node is no longer an edge, and this hasn't been updated
     */
    public boolean wasEdge() {
        return frontPos != null || backPos != null;
    }

    /**
     * Clears the remnants of being an edge
     */
    public void clearEdge() {
        frontPos = null;
        backPos = null;
    }

    public boolean isTerminus() {
        return nodes.size() == 1;
    }


    public Vector3i getFrontPos() {
        return frontPos;
    }

    public Vector3i getBackPos() {
        return backPos;
    }

    public Vector3i getWorldPos() {
        return worldPos;
    }

    public void setWorldPos(Vector3i worldPos) {
        this.worldPos = worldPos;
    }

    public int getNodeId() {
        return nodeId;
    }

    public void setNodeId(int nodeId) {
        this.nodeId = nodeId;
    }

    public GraphUri getGraphUri() {
        return graphUri;
    }

    public void setGraphUri(GraphUri graphUri) {
        this.graphUri = graphUri;
    }

    /**
     * Links this node with another.
     * Duplicates the link both ways.
     * This does not respect any existing connection via that side
     *
     * @param otherNode The other node to link to
     */
    public void linkNode(GraphNode otherNode) {
        /* Yes this is ugly, but it's better than making a bunch of new vectors */
        Side nodeSide = Side.inDirection(
                otherNode.worldPos.x - worldPos.x,
                otherNode.worldPos.y - worldPos.y,
                otherNode.worldPos.z - worldPos.z);

        nodes.put(nodeSide, otherNode);
        otherNode.nodes.put(nodeSide.reverse(), this);
    }
}

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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.terasology.blockGraphs.graphDefinitions.GraphUri;
import org.terasology.blockGraphs.graphDefinitions.NodeRef;
import org.terasology.math.Side;
import org.terasology.math.geom.Vector3i;

import java.util.Collection;

/**
 * The base class for all Graph Nodes.
 * Handles some basic things like calling different sub methods depending on which "mode" the node is in
 * <p>
 * Should be extended to provide specific node types
 *
 * @see EdgeNode
 */
public class JunctionNode extends GraphNode {

    /**
     * All the nodes attached to this one, and what side they physically are on
     */
    public BiMap<Side, NodeRef> nodes = HashBiMap.create(6);
    /**
     * The position of this node in the world
     */
    public Vector3i worldPos;

    public JunctionNode(GraphUri graphUri, int nodeId, int definitionId) {
        super(graphUri, nodeId, definitionId);
    }

    public NodeType getNodeType() {
        return NodeType.JUNCTION;
    }

    /**
     * Links this node with another.
     * This does not respect any existing connection via that side
     *
     * @param otherNode The other node to link to
     */
    public void linkNode(NodeRef otherNode, Side nodeSide) {
        nodes.put(nodeSide, otherNode);
    }

    /**
     * Links this node with another.
     * This does not respect any existing connection via that side.
     * <p>
     * Additionally, updates the position of this node to reflect the supplied position
     *
     * @param otherNode The other node to link to
     */
    public void linkNode(NodeRef otherNode, Side thisToOther, Vector3i pos) {
        linkNode(otherNode, thisToOther);
        worldPos = pos;
    }

    /**
     * Unlinks this node with the node on the specific side.
     * Does not ensure that the other half of the connection is broken
     *
     * @param side The side to unlink
     */
    public void unlinkNode(Side side) {
        nodes.remove(side);
    }

    @Override
    public void unlinkNode(NodeRef node) {
        nodes.values().remove(node);
    }

    @Override
    public Collection<NodeRef> getConnections() {
        return nodes.values();
    }

    @Override
    public Side getSideForNode(NodeRef node) {
        return nodes.inverse().getOrDefault(node, null);
    }

    /**
     * Gets the node, if there is any, attached to the given side
     *
     * @param side The side the node is attached to
     * @return The node attached to the given side
     */
    public NodeRef getNodeForSide(Side side) {
        return nodes.get(side);
    }

    /**
     * Unlink this node from all connections
     */
    @Override
    public void unlinkAll() {
        for (Side side : nodes.keySet()) {
            unlinkNode(side);
        }
    }

}

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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.terasology.blockGraphs.graphDefinitions.GraphUri;
import org.terasology.blockGraphs.graphDefinitions.NodeRef;
import org.terasology.math.Side;
import org.terasology.math.geom.Vector3i;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * This type has exactly two connections, and can span multiple blocks.
 * <p>
 * It is used to simplify long stretches of common node types for performance
 */
public class EdgeNode extends GraphNode {

    /**
     * The position of the 'front' of the edge
     */
    public Vector3i frontPos;
    /**
     * The side the connection enters though in the front
     */
    public Side frontSide;
    /**
     * The node at the 'front' of this node
     */
    public NodeRef frontNode;


    /**
     * The position of the 'back' of the edge
     */
    public Vector3i backPos;
    /**
     * The side the connection enters through in the back
     */
    public Side backSide;
    /**
     * The back connection of this node
     */
    public NodeRef backNode;

    /**
     * The position of each position in this edge.
     * This _IS_ ordered, with 0 being the {@link #backPos} and n-1 being the {@link #frontPos}
     * TODO: Best way to split (and also store) and edge
     * Possible use some simple linked class storing a "string" from one end to the other
     * This could be a component and stored on the block entities. Maybe a part of the GraphNodeComponent
     * ****
     * Store it in the EdgeNode class. This doesn't split the edge information across two weird spots
     * Store as an ArrayList: Allows for easy random index access
     * Store as LinkedList: More intuitive towards what is is. Adjusting is "easier"
     */
    public List<Vector3i> worldPositions = new ArrayList<>();

    public EdgeNode(GraphUri graphUri, int nodeId, int definitionId) {
        super(graphUri, nodeId, definitionId);
    }

    public NodeType getNodeType() {
        return NodeType.EDGE;
    }


    /**
     * Links an end of the edge to a specific node, in a specific direction
     *
     * @param otherNode The node to link
     * @param edgeSide  The end of the edge the node should be linked to
     * @param linkSide  The side the node should be linked to
     */
    public void linkNode(NodeRef otherNode, Side edgeSide, Side linkSide) {
        if (edgeSide == Side.FRONT) {
            frontSide = linkSide;
            frontNode = otherNode;

        } else if (edgeSide == Side.BACK) {
            backSide = linkSide;
            backNode = otherNode;
        }
    }

    /**
     * Links an end of this edge to a specific node, in a specific direction, at a specific position
     *
     * @param otherNode The node to link
     * @param edgeSide  The end of the edge the node should be linked to
     * @param linkSide  The side the node should be linked to
     * @param pos       The position of the end of the edge being linked
     */
    public void linkNode(NodeRef otherNode, Side edgeSide, Side linkSide, Vector3i pos) {
        linkNode(otherNode, edgeSide, linkSide);
        if (edgeSide == Side.FRONT) {
            frontPos = pos;
        } else if (edgeSide == Side.BACK) {
            backPos = pos;
        }
    }

    /**
     * Unlinks one end of the edge
     *
     * @param side The end of the edge to unlink
     */
    public void unlinkNode(Side side) {
        if (side == Side.FRONT) {
            frontSide = null;
            frontNode = null;
        } else if (side == Side.BACK) {
            backSide = null;
            backNode = null;
        }
    }

    /**
     * Unlinks the specified node from this edge.
     *
     * @param node The node to unlink
     */
    @Override
    public void unlinkNode(NodeRef node) {
        if (frontNode == node) {
            frontSide = null;
            frontNode = null;
        } else if (backNode == node) {
            backSide = null;
            backNode = null;
        }
    }

    @Override
    public Collection<NodeRef> getConnections() {
        Set<NodeRef> set = Sets.newHashSet(frontNode, backNode);
        set.removeIf(Objects::isNull);
        return ImmutableSet.copyOf(set);
    }

    @Override
    public Side getSideForNode(NodeRef node) {
        if (frontNode == node) {
            return Side.FRONT;
        } else if (backNode == node) {
            return Side.BACK;
        } else {
            return null;
        }
    }

    @Override
    public void unlinkAll() {
        unlinkNode(Side.FRONT);
        unlinkNode(Side.BACK);
    }

}

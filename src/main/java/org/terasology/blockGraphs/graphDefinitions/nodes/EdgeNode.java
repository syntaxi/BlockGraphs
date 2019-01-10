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

import org.terasology.blockGraphs.graphDefinitions.GraphUri;
import org.terasology.math.Side;
import org.terasology.math.geom.Vector3i;

/**
 * An edge node is a specific subtype of a graph node.
 * This type has exactly two connections, and can span multiple blocks.
 * <p>
 * It is used to simplify long stretches of common node types for performance
 */
public class EdgeNode extends GraphNode {

    /**
     * The position of the 'front' of the edge
     */
    private Vector3i frontPos;
    /**
     * The side the connection enters though in the front
     */
    private Side frontSide;

    /**
     * The position of the 'back' of the edge
     */
    private Vector3i backPos;
    /**
     * The side the connection enters through in the back
     */
    private Side backSide;

    public EdgeNode(GraphUri graphUri, int nodeId) {
        super(graphUri, nodeId);
    }

    @Override
    public void linkNode(GraphNode otherNode) {
        //void
        throw new UnsupportedOperationException("Edge Nodes require the end to be specified when linking nodes");
    }

    /**
     * Links a node to a specific end of the edge
     *
     * @param otherNode The other node to link to
     * @param nodeSide  The end of the edge to link the node via
     */
    @Override
    public void linkNode(GraphNode otherNode, Side nodeSide) {
        if (nodeSide == Side.FRONT) {
            linkNode(otherNode, nodeSide, Side.inDirection(
                    otherNode.worldPos.x - frontPos.x,
                    otherNode.worldPos.y - frontPos.y,
                    otherNode.worldPos.z - frontPos.z));
        } else if (nodeSide == Side.BACK) {
            linkNode(otherNode, nodeSide, Side.inDirection(
                    otherNode.worldPos.x - backPos.x,
                    otherNode.worldPos.y - backPos.y,
                    otherNode.worldPos.z - backPos.z));
        } else {
            throw new UnsupportedOperationException("Edge nodes need to have a front and back specified");
        }
    }

    /**
     * Links an end of the edge to a specific node, in a specific direction
     *
     * @param node     The node to link
     * @param edgeSide The end of the edge that the node should be linked to
     * @param linkSide The side that the node should be linked
     */
    public void linkNode(GraphNode node, Side edgeSide, Side linkSide) {
        if (edgeSide == Side.FRONT) {
            frontSide = linkSide;
            nodes.put(Side.FRONT, node);
        } else if (edgeSide == Side.BACK) {
            backSide = linkSide;
            nodes.put(Side.BACK, node);
        } else {
            throw new UnsupportedOperationException("Edge Nodes only support Front or Back.");
        }
    }

    /**
     * Unlinks one end of the edge
     *
     * @param side The end of the edge to unlink
     */
    @Override
    public void unlinkNode(Side side) {
        if (side == Side.FRONT) {
            frontSide = null;
            nodes.remove(Side.FRONT);
        } else if (side == Side.BACK) {
            backSide = null;
            nodes.remove(Side.BACK);
        } else {
            throw new UnsupportedOperationException("Edge nodes only support Front or Back");
        }
    }

    /**
     * Unlinks the specified node from this edge.
     *
     * @param node The node to unlink
     */
    public void unlinkNode(GraphNode node) {
        if (nodes.get(Side.FRONT) == node) {
            unlinkNode(Side.FRONT);
        } else if (nodes.get(Side.BACK) == node) {
            unlinkNode(Side.BACK);
        }
    }

    @Override
    public void unlinkAll() {
        unlinkNode(Side.FRONT);
        unlinkNode(Side.BACK);
    }

    public GraphNode getFrontNode() {
        return getConnectingNodes().get(Side.FRONT);
    }

    public Side getFrontSide() {
        return frontSide;
    }

    public Vector3i getFrontPos() {
        return frontPos;
    }

    public void setFrontPos(Vector3i frontPos) {
        this.frontPos = frontPos;
    }

    public GraphNode getBackNode() {
        return getConnectingNodes().get(Side.BACK);
    }

    public Side getBackSide() {
        return backSide;
    }

    public Vector3i getBackPos() {
        return backPos;
    }

    public void setBackPos(Vector3i backPos) {
        this.backPos = backPos;
    }
}

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
package org.terasology.blockGraphs.graphDefinitions.nodes;

import org.terasology.blockGraphs.graphDefinitions.BlockGraph;
import org.terasology.blockGraphs.graphDefinitions.GraphUri;
import org.terasology.blockGraphs.graphDefinitions.NodeRef;
import org.terasology.math.Side;
import org.terasology.math.geom.Vector3i;

import java.util.Collection;

public abstract class GraphNode {

    /**
     * The id of this node in it's {@link BlockGraph} instance
     */
    public int nodeId;

    /**
     * The URI of the graph this node belongs to
     */
    public GraphUri graphUri;

    /**
     * The ID of the definition this node is linked to
     */
    public int definitionId;

    public GraphNode(GraphUri graphUri, int nodeId, int definitionId) {
        this.graphUri = graphUri;
        this.nodeId = nodeId;
        this.definitionId = definitionId;
    }

    public abstract NodeType getNodeType();




    /**
     * Removes all connections this node has.
     * Does not break the other side of the the link
     */
    public abstract void unlinkAll();

    /**
     * Unlink this node and another.
     * Does not replicate.
     * TODO: Check if unlinking should be replicated to other nodes. This would be a good idea if it is always done
     *
     * @param node The node to unlink
     */
    public abstract void unlinkNode(NodeRef node);

    public abstract Collection<NodeRef> getConnections();

    public abstract Side getSideForNode(NodeRef node);
}

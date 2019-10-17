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

import org.terasology.blockGraphs.graphDefinitions.BlockGraph;
import org.terasology.blockGraphs.graphDefinitions.GraphNodeComponent;
import org.terasology.blockGraphs.graphDefinitions.GraphType;
import org.terasology.blockGraphs.graphDefinitions.nodes.EdgeNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.GraphNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.JunctionNode;
import org.terasology.blockGraphs.graphDefinitions.nodes.TerminusNode;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.math.Side;
import org.terasology.math.geom.Vector3i;
import org.terasology.registry.In;
import org.terasology.world.BlockEntityRegistry;
import org.terasology.world.OnChangedBlock;
import org.terasology.world.block.BlockUri;

import java.util.ArrayList;
import java.util.List;

import static org.terasology.blockGraphs.NodeLinkHelper.getSideFrom;
import static org.terasology.blockGraphs.NodeLinkHelper.getSurroundingPos;
import static org.terasology.blockGraphs.NodeLinkHelper.tryBiLink;

/**
 * This class is the counterpart to the {@link BlockGraphConstructor}.
 * Whilst it deals with constructing graphs from a collection of blocks,
 * this deals with changes made to a graph once it has been constructed.
 * <p>
 * A change to a graph can be split into two major categories, placing and removing.
 * A placing change is one triggered by a block being placed.
 * (More accurately when a block <i>without</i> a graph type is modified)
 * These can either result in a graph simply being enlarged, or in the merging of two graphs
 * A removing change is one triggered by the destruction of a block
 * (More accurately when a block <i>with</i> a graph type is modified)
 * These can either result in a graph shrinking, being destroyed or in the splitting of a graph
 */
public class GraphChangeManager extends BaseComponentSystem {

    @In
    private BlockEntityRegistry entityRegistry;

    @In
    private BlockGraphManager graphManager;

    /**
     * Called when
     * <p>
     * Filters on:
     *
     * @see OnChangedBlock
     */
    @ReceiveEvent
    public void OnChangedBlock(OnChangedBlock event, EntityRef entity) {
        if (graphManager.getGraphType(event.getOldType().getURI()) != null) {
            removalEvent(event);
        } else if (graphManager.getGraphType(event.getNewType().getURI()) != null) {
            placingEvent(event);
        } //todo: These may be independent and hence can potentially both happen
        //todo: Consider replacing a block in one graph with another in a different
    }

    /**
     * There was no graph block here before.
     *
     * @param event
     */
    private void placingEvent(OnChangedBlock event) {
        GraphType graph = graphManager.getGraphType(event.getNewType().getURI());
        /* Get all neighbouring blocks & scan for graphs */
        Vector3i[] neighbours = getSurroundingPos(event.getBlockPosition());

        List<NodePosition> nodes = new ArrayList<>();
        for (Vector3i neighbour : neighbours) {
            NodePosition node = new NodePosition();
            node.pos = neighbour;
            EntityRef blockEntity = entityRegistry.getExistingEntityAt(neighbour);
            if (!blockEntity.exists() || !blockEntity.hasComponent(GraphNodeComponent.class)) {
                continue;
            }
            GraphNodeComponent component = blockEntity.getComponent(GraphNodeComponent.class);
            if (component.graphUri.getGraphUri() != graph.getUri()) {
                continue;
            }
            node.graph = graphManager.getGraphInstance(component.graphUri);
            node.node = node.graph.getNode(component.nodeId);
            nodes.add(node);
        }

    }

    private class NodePosition {
        GraphNode node;
        Vector3i pos;
        BlockGraph graph;
    }


    private void mergeGraphs(GraphType graphType, BlockUri blockType, Vector3i newPos, List<NodePosition> neighbours) {
        NodePosition position = new NodePosition();
        position.graph = graphManager.newGraphInstance(graphType);
        position.node = position.graph.createTerminusNode(blockType);
        position.pos = newPos;

        for (NodePosition neighbour : neighbours) {
            mergeInto(position,
                    neighbour);
        }
    }

    /**
     * Merges two graphs together.
     * <p>
     * We have a couple of cases, dictated by what node types each node is as well as what each node already connects to
     * (Note: left hand side is always "from". Right hand side is always "to")
     * (Note: Scenarios that are not symmetrical can be flipped)
     * <p>
     * TODO: Simplify this down somehow!!
     * <pre>
     * 1. Terminus == Terminus
     *      1.1  Blank == T === T == Blank
     *          Link To & From
     *      1.2  Blank == T === T == T (== Blank) [implied]
     *          Upgrade First To T into Edge
     *      1.3  Blank == T === T == E
     *          Merge To T and To E
     *      1.4  Blank == T === T == J
     *          Upgrade To T into Edge
     *      1.5  T == T === T == T
     *          Upgrade First From T into Edge
     *          Upgrade First To T into Edge
     *          Merge Both E
     *      1.6  T == T === T == E
     *          Upgrade First From T into Edge
     *          Upgrade To T into Edge
     *          Merge all Three E
     *      1.7  T == T === T == J
     *          Upgrade First From T into Edge
     *          Upgrade To T into Edge
     *          Merge Both E
     *      1.8  E == T === T == E
     *          Upgrade From T into Edge
     *          Upgrade To T into Edge
     *          Merge all Four E
     *      1.9  E == T === T == J
     *          Upgrade From T into Edge
     *          Upgrade To T into Edge
     *          Merge all Three E
     *      1.10 J == T === T == J
     *          Upgrade From T into Edge
     *          Upgrade To T into Edge
     *          Merge Both E
     *
     * 2. Terminus == Edge
     *      Because Edges must be "full" (ie both ends connected) we know that the TO pos will have to be a junction
     *      So we can upgrade it to a Junction and apply the Junction rules
     *      2.a End
     *          Split E into a Junction and an Edge
     *          Apply appropriate rule (T === J)
     *      2.b Middle
     *          Split E into a Junction and two Edges either side
     *          Apply appropriate rule (T === J)
     *
     * 3. Terminus == Junction
     *      We know that the junction cannot be full, else we wouldn't be able to touch it.
     *      It is also irrelevant what it connects to.
     *      3.1 Blank == T === J
     *          Link To & From
     *      3.2 T == T === J
     *          Convert From T into an Edge
     *      3.3 E == T === J
     *          Convert From T into an Edge
     *          Merge both E
     *      3.4 J == T === J
     *          Convert From T into an Edge
     * 4. Edge == Terminus
     * 5. Junction == Terminus
     *      These are covered by flipped cases. Simply switch "To" and "From" in the descriptions
     * 6. Edge == Edge
     *      Edges are always "full" so all cases are the same.
     *      Convert both into Junctions. If in middle of edge, split Edge into two
     *      Link To & From
     * 7. Edge == Junction
     *      Edges are always "full" so all cases are the same.
     *      Convert Edge into Junction. If in middle of edge, split Edge into two
     *      Link To & From
     * 8. Junction == Junction
     *      Just Link To & From
     * </pre>
     *
     * @param from
     * @param to
     */
    private void mergeInto(NodePosition from, NodePosition to) {
        //TODO: Merge graph instances and INVALIDATE old instance. Ensure references are updated to avoid issues
        switch (from.node.getNodeType()) {
            case TERMINUS:
                mergeFromTerminus(from, to);
                break;
            case EDGE:
                mergeFromEdge(from, to);
                break;
            case JUNCTION:
                mergeFromJunction(from, to);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + from.node.getNodeType());
        }
    }

    private void mergeFromTerminus(NodePosition from, NodePosition to) {
        switch (to.node.getNodeType()) {
            case TERMINUS:
                break;
            case EDGE:
                break;
            case JUNCTION:

                break;
            default:
                throw new IllegalStateException("Unexpected value: " + to.node.getNodeType());
        }
    }

    private void mergeFromEdge(NodePosition from, NodePosition to) {
        switch (to.node.getNodeType()) {
            case TERMINUS:
                mergeFromTerminus(to, from); // Use existing code but flip the to and from
                break;
            case EDGE:

                break;
            case JUNCTION:

                break;
            default:
                throw new IllegalStateException("Unexpected value: " + to.node.getNodeType());
        }
    }

    private void splitEdgeAt(NodePosition pos) {
        EdgeNode edgeNode = (EdgeNode) pos.node;
        // Firstly handle edge being 1 block
        if (edgeNode.worldPositions.size() == 1) {
            BlockGraph graph = graphManager.getGraphInstance(edgeNode.graphUri);
            JunctionNode newNode = graph.createJunctionNode(edgeNode.definitionId);

            GraphNode backNode = edgeNode.backNode;
            Side backSide = edgeNode.backSide;
            GraphNode frontNode = edgeNode.frontNode;
            Side frontSide = edgeNode.frontSide;

            graph.removeNode(edgeNode);

            tryBiLink(newNode, backNode, backSide);
            tryBiLink(newNode, frontNode, frontSide);
            linkNodeToPosition(pos.pos, newNode);
        } else {

            // Make the new junction node
            BlockGraph graph = graphManager.getGraphInstance(edgeNode.graphUri);
            JunctionNode junctionNode = graph.createJunctionNode(edgeNode.definitionId);
            junctionNode.worldPos = pos.pos;
            linkNodeToPosition(junctionNode.worldPos, junctionNode);

            if (pos.pos == edgeNode.frontPos) { // Front end

                // Record the details of the old front
                GraphNode frontNode = edgeNode.frontNode;
                Side frontSide = edgeNode.frontSide;

                // Separate the edge node and it's link
                edgeNode.unlinkNode(frontNode);
                frontNode.unlinkNode(edgeNode);

                // Shrink edge by one
                Vector3i newFrontPos = edgeNode.worldPositions.get(edgeNode.worldPositions.size() - 2);
                edgeNode.worldPositions.remove(edgeNode.worldPositions.size() - 1);
                edgeNode.frontPos = newFrontPos;


                //  Link the junction node to the two connections
                tryBiLink(junctionNode, frontNode, frontSide); // link it to what the edge linked to
                tryBiLink(junctionNode, edgeNode, getSideFrom(junctionNode.worldPos, newFrontPos)); // link it the edge


            } else if (pos.pos == edgeNode.backPos) { // Back end

                // Record the details of the old front
                GraphNode backNode = edgeNode.backNode;
                Side backSide = edgeNode.backSide;

                // Separate the edge node and it's link
                edgeNode.unlinkNode(backNode);
                backNode.unlinkNode(edgeNode);

                // Shrink edge by one
                Vector3i newBackPos = edgeNode.worldPositions.get(1);
                edgeNode.worldPositions.remove(0);
                edgeNode.backPos = newBackPos;

                //  Link the junction node to the two connections
                tryBiLink(junctionNode, backNode, backSide); // link it to what the edge linked to
                tryBiLink(junctionNode, edgeNode, getSideFrom(junctionNode.worldPos, newBackPos)); // link it the edge

            } else { // Middle

                // Calculate the positions of the split
                Vector3i splitPos = pos.pos;
                int indexOfPos = edgeNode.worldPositions.indexOf(pos.pos);
                if (indexOfPos < 0) {
                    throw new IllegalStateException("Attempting to split an edge at point it doesn't cover");
                }
                Vector3i behindPos = edgeNode.worldPositions.get(indexOfPos - 1);
                Vector3i infrontPos = edgeNode.worldPositions.get(indexOfPos + 1);

                GraphNode frontNode = edgeNode.frontNode;
                Side frontSide = edgeNode.frontSide;
                Vector3i frontPos = edgeNode.frontPos;

                // Unlink the front of this edgeNode
                edgeNode.unlinkNode(frontNode);
                frontNode.unlinkNode(edgeNode);

                // Make the edge infront
                EdgeNode edgeInfront = graph.createEdgeNode(edgeNode.definitionId);
                edgeInfront.worldPositions = sublistCopy(edgeNode.worldPositions, indexOfPos + 1, -1);
                edgeInfront.frontPos = frontPos;
                tryBiLink(edgeInfront, frontNode, frontSide);
                edgeInfront.backPos = infrontPos;
                tryBiLink(edgeInfront, junctionNode, getSideFrom(edgeInfront.backPos, junctionNode.worldPos));
            }
        }
    }

    private <T> List<T> sublistCopy(List<T> list, int from, int to) {
        if (to < 0) {
            to = list.size() + to;
        }
        return new ArrayList<>(list.subList(from, to));
    }

    private void linkNodeToPosition(Vector3i position, GraphNode node) {
        EntityRef entity = entityRegistry.getExistingEntityAt(position);
        GraphNodeComponent component = new GraphNodeComponent();

        component.graphUri = node.graphUri;
        component.nodeId = node.nodeId;

        entity.addComponent(component);

        switch (node.getNodeType()) {
            case TERMINUS:
                ((TerminusNode) node).worldPos = position;
                break;
            case JUNCTION:
                ((JunctionNode) node).worldPos = position;
                break;
        }
    }


    private void mergeFromJunction(NodePosition from, NodePosition to) {
        switch (to.node.getNodeType()) {
            case TERMINUS:
                mergeFromTerminus(to, from); // Use existing code but flip the to and from
                break;
            case EDGE:
                mergeFromEdge(from, to); // Use existing code but flip the to and from
                break;
            case JUNCTION:
                Side fromToTo = getSideFrom(from.pos, to.pos);
                if (!tryBiLink(from.node, to.node, fromToTo)) {
                    throw new IllegalStateException("Failed to link two Junction Nodes when merging graphs");
                }
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + to.node.getNodeType());
        }
    }

    private void removalEvent(OnChangedBlock event) {

    }

}

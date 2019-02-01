# Block Graphs
Block Graphs allows you to, as the name suggests, build graphs of blocks, with each block corresponding to a node.  
This module does also support fully abstract graphs, without any blocks involved and for that see **Abstract Graphs** below.

It is intended to be a successor to [BlockNetworks](http://github.com/Terasology/BlockNetworks) and _if/when_ they reach feature parity can be considered a replacement. 
For more alternatives to this module check out the aforementioned BlockNetworks as well as [Machines](https://github com/Terasology/Machines).

**This module is currently under development.**  
All information herein is likely to change. I shall attempt to keep the architecture unchanged but api details like class names and how things are set/registered/etc will likely be in flux.

## Basic Setup
This is the quickest and simplest method of getting a block graph registered.

0. Add the module to your dependencies in the `module.txt`.
1. Create a _subclass_ of `NodeDefinition`, implement the required methods.
2. Repeat 1 for as many different node types you want in your graph.
3. Create a _new instance_ of `GraphType`.
4. Add all of the NodeDefinitions to the GraphType using `GraphType#addNodeType`
5. Register the graph type using `BlockGraphManager#addGraphType`
6. Use the `BlockGraphManager#injectData` methods to add data into the graph

If the module is functioning correctly now you can place any of the blocks you referenced in step 1 and it will build graphs automatically, linking them


<table>
<tr>
<td>

```
    
 [] 
    
```

</td>
<td>

```
    
 () 
    
```

</td>
</tr># Block Graphs
Block Graphs allows you to, as the name suggests, build graphs of blocks, with each block corresponding to a node.  
This module does also support fully abstract graphs, without any blocks involved and for that see **Abstract Graphs** below.

It is intended to be a successor to [BlockNetworks](http://github.com/Terasology/BlockNetworks) and _if/when_ they reach feature parity can be considered a replacement. 
For more alternatives to this module check out the aforementioned BlockNetworks as well as [Machines](https://github com/Terasology/Machines).

**This module is currently under development.**  
All information herein is likely to change. I shall attempt to keep the architecture unchanged but api details like class names and how things are set/registered/etc will likely be in flux.

## Basic Setup
This is the quickest and simplest method of getting a block graph registered.

0. Add the module to your dependencies in the `module.txt`.
1. Create a _subclass_ of `NodeDefinition`, implement the required methods.
2. Repeat 1 for as many different node types you want in your graph.
3. Create a _new instance_ of `GraphType`.
4. Add all of the NodeDefinitions to the GraphType using `GraphType#addNodeType`
5. Register the graph type using `BlockGraphManager#addGraphType`
6. Use the `BlockGraphManager#injectData` methods to add data into the graph

If the module is functioning correctly now you can place any of the blocks you referenced in step 1 and it will build graphs automatically, linking them.

## Module Structure
This will explain how the api for the module works. If you are after technical details visit ~~the Wiki~~ sections further down.

Broadly, the module is broken into three segements. One handles dynamically making graphs as blocks are placed. The next deals with how the data moves around. The last part is more nebulous and is how the graphs are registered, setup and encompasses most of the API. It is the one I will focus on, as well as the second.

Firstly I need to make a distinction between Graphs and GraphTypes. A _graph_ is a collection of _nodes_ with nodes linked where blocks touch. A _GraphType_ is a collection of _NodeDefinitions_, which in turn describe & control the behaviour of nodes.  GraphTypes describe, well, a type of graph. What this means is that your module should define a GraphType, and then interact with Graph

### GraphTypes & NodeDefinitions
NodeDefinitions control how the nodes in the graph work. They contain methods which take in a data packet, the node itself and a side, and should return the side that the data should move through, or null for the data to leave the graph. There are also methods that allow you to process the data when it enters the node or graph. It's here that I'm also going to explain the difference between a `GraphNode` and an `EdgeNode`.  
An EdgeNode is a more specialised type of GraphNode. In order to simplify the graph, stretches of blocks are condensed into a single node. To illustrate this, take a look at the diagram. On the left is the layout of the blocks in the world, and the right is how the nodes look.

<table>
<tr>
<td>

```
┌─┐┌─┐┌─┐┌─┐┌─┐┌─┐
│a││b││b││b││c││f│
└─┘└─┘└─┘└─┘└─┘└─┘
              ┌─┐
              │d│
              └─┘
   ┌─┐┌─┐┌─┐┌─┐
   │e││d││d││d│
   └─┘└─┘└─┘└─┘
```

</td>
<td>

```
╭─╮ ╭─╮ ╭─╮ ╭─╮
│a│-│b│-│c│-│f│
╰─╯ ╰─╯ ╰─╯ ╰─╯
             ╭─╮
             │d│
             ╰─╯
   ╭─╮╭─╮╭─╮╭─╮
   │e││d││d││d│
   ╰─╯╰─╯╰─╯╰─╯
```

</td>
</tr>
</table>
</table>

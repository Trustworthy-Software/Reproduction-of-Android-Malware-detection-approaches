/**
 * File: src/dynCG/callGraph.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      Changes
 * -------------------------------------------------------------------------------------------
 * 12/10/15		hcai		created; for representing dynamic call graph
 * 01/05/16		hcai		the first basic, working version
 * 01/25/16		hcai		added routines counting total outgoing and incoming calls from a node
 * 01/28/16		hcai		added caller and callee ranking by outgoing/incoming call instances
 * 05/09/16		hcai		fix the method-level taint flow reachability 
 * 05/10/16		hcai		fixed a few minor issues in getNumberofFlowPaths
 * 05/11/16		hcai		debugging continued: benign apps got even much higher sensitive info reachability than malware
 * 05/12/16		hcai		confirmed that above bug seemed working already
 * 05/12/16		hcai		implement the variant that considers all paths rather than just shortest paths from lca to source/sink
*/
package dynCG;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jgrapht.*;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.alg.*;
import org.jgrapht.traverse.*;

/** represent the dynamic call graph built from whole program path profiles */
public class callGraph {
	public static final String CALL_DELIMIT = " -> ";
	// bijective mapping between string and integer tagging of canonically named method (package + class + method signature)
	public static final Map<String, Integer> g_me2idx = new HashMap<String, Integer>();
	public static final Map<Integer, String> g_idx2me = new HashMap<Integer,String>();
	
	public String toString() {
		return "dynamic conflated call graph: " + g_me2idx.size() + " methods; " + 
				this._graph.vertexSet().size() + " nodes; " + this._graph.edgeSet().size() + " edges.";
	}

	public static class CGNode {
		// method index
		private Integer idx;
		CGNode() {
			this(-1);
		}
		CGNode(int _idx) {
			idx = _idx;
		}
		public int getIndex () {
			return idx;
		}
		public String getMethodName() {
			if (-1 == idx) {
				return null;
			}
			return g_idx2me.get(this.idx);
		}
		public String getSootMethodName() {
			return "<" + getMethodName() + ">";
		}
		public String getSootClassName() {
			String ret = getMethodName();
			if (ret == null) return null;
			if (ret.indexOf(":")==-1) {
				System.err.println("weird node: " + ret);
				System.exit(-1);
			}
			return ret.substring(0, ret.indexOf(":"));
		}
		public boolean equals(Object other) {
			return ((CGNode)other).idx.intValue() == this.idx.intValue();
		}
		public int hashCode() {
			return idx.hashCode();
		}
		
		public String toString() {
			return getMethodName() + "[" + idx + "]";
		}
	}
	
	public static class CGNodeFactory implements VertexFactory<CGNode> {
		@Override
		public CGNode createVertex() {
			return new CGNode();
		}
	}
	
	public static class CGEdge {
		private CGNode src;
		private CGNode tgt;
		/* keep the time stamp of each instance, in the order of enrollment */
		/* then the size of this collection indicates the frequency of this call */
		private Set<Integer> tss;
		CGEdge(CGNode _src, CGNode _tgt) {
			src = _src;
			tgt = _tgt;
			tss = new LinkedHashSet<Integer>();
		}
		public void addInstance (int ts) {
			tss.add(ts);
		}
		public CGNode getSource() {
			return src;
		}
		public CGNode getTarget() {
			return tgt;
		}
		
		public int getFrequency() { return tss.size(); }
		public Set<Integer> getAllTS () { return tss; }
		
		public String toString() {
			return "["+g_idx2me.get(src.getIndex()) + "->" + g_idx2me.get(tgt.getIndex())+"]:" + getFrequency();
		}
		
		public boolean equals (Object other) {
			return src.equals(((CGEdge)other).src) && tgt.equals(((CGEdge)other).tgt);
		}
	}
	
	public static class CGEdgeFactory implements EdgeFactory<CGNode,CGEdge> {
		@Override
		public CGEdge createEdge(CGNode v0, CGNode v1) {
			//System.out.println("ADD edge from factory");
			//System.exit(-1);
			return new CGEdge(v0, v1);
		}
	}
	
	private final DirectedGraph<CGNode, CGEdge> _graph = new DefaultDirectedGraph<CGNode, CGEdge>(new CGEdgeFactory());
	
	callGraph() {
	}
	
	public DirectedGraph<CGNode, CGEdge> getInternalGraph() { return _graph; }
	
	public CGEdge addEdgeOrg(CGNode src, CGNode tgt, int ts) {
		_graph.addVertex(src);
		_graph.addVertex(tgt);
		if (!_graph.containsEdge(src, tgt)) {
			_graph.addEdge(src, tgt);
		}
		CGEdge ret = _graph.getEdge(src, tgt);
		ret.addInstance(ts);
		return ret;
	}
	
	public void sanityCheck() {
		System.out.println("Total edge: " + _graph.edgeSet().size());
		for (CGEdge e : _graph.edgeSet()) {
			if (e.getAllTS().isEmpty()) {
				System.out.println("Edge added without ts: " + e);
				System.exit (0);
			}
			//System.out.println(e.getAllTS().size());
		}
		System.out.println("Sanity Check went okay.");
	}

	public CGEdge addEdge(CGNode src, CGNode tgt, int ts) {
		if (!_graph.containsVertex(src)) _graph.addVertex(src);
		if (!_graph.containsVertex(tgt)) _graph.addVertex(tgt);

		CGEdge ret = _graph.getEdge(src, tgt);
		if (null==ret) {
			ret = new CGEdge(src,tgt);
			ret.addInstance(ts);
			_graph.addEdge(src, tgt, ret);
		}
		else {
			assert ret.getAllTS().size()>=1;
			ret.addInstance(ts);
		}
		return ret;
	}
	
	private CGNode getCreateNode(int mid) {
		CGNode tobe = new CGNode(mid);
		if (!_graph.containsVertex(tobe)) return tobe;
		for (CGNode n : _graph.vertexSet()) {
			if (tobe.equals(n)) {
				return n;
			}
		}
		return null;
		//throw new Exception("impossible error!");
	}
	
	public CGEdge addCall (int caller, int callee, int ts) {
		//addEdge(new CGNode(caller), new CGNode(callee));
		return addEdge (getCreateNode(caller), getCreateNode(callee), ts);
	}
	
	public int addMethod (String mename) {
		if (g_me2idx.keySet().contains(mename)) return g_me2idx.get(mename);
		int curidx = g_me2idx.size();
		g_me2idx.put(mename, curidx);
		
		assert !g_idx2me.containsKey(curidx);
		assert g_idx2me.size() == curidx;
		g_idx2me.put(curidx, mename);
		
		return curidx;
	}

	public CGEdge addCall (String traceLine, int ts) {
		traceLine = traceLine.trim();
		assert traceLine.contains(CALL_DELIMIT);
		String[] segs = traceLine.split(CALL_DELIMIT);
		assert segs.length == 2;
		for (int k = 0; k < segs.length; ++k) {
			segs[k] = segs[k].trim();
			if (segs[k].startsWith("<")) {
				segs[k] = segs[k].substring(1);
			}
			if (segs[k].endsWith(">")) {
				segs[k] = segs[k].substring(0, segs[k].length()-1);
			}
		}
		
		return addCall (addMethod (segs[0]), addMethod (segs[1]), ts);
	}
	
	public CGNode getNodeByName (String mename) {
		for (CGNode cgn : _graph.vertexSet()) {
			if (cgn.getMethodName().equalsIgnoreCase(mename) || cgn.getSootMethodName().equalsIgnoreCase(mename)) return cgn;
		}
		return null;
	}
	
	public CGEdge getEdgeByName(String caller, String callee) {
		CGNode src = getNodeByName (caller);
		CGNode tgt = getNodeByName (callee);
		if (null == src || null == tgt) return null;
		
		return _graph.getEdge(src, tgt);
	}
	
	public Set<CGNode> getAllCallees (String caller) {
		Set<CGNode> ret = new HashSet<CGNode>();
		
		CGNode src = getNodeByName (caller);
		if (null == src) return ret;
		
		for (CGEdge oe : _graph.outgoingEdgesOf(src)) {
			ret.add(oe.getTarget());
		}
		return ret;
	}

	public Set<CGNode> getAllCallers (String callee) {
		Set<CGNode> ret = new HashSet<CGNode>();
		
		CGNode tgt = getNodeByName (callee);
		if (null == tgt) return ret;
		
		for (CGEdge ie : _graph.incomingEdgesOf(tgt)) {
			ret.add(ie.getSource());
		}
		return ret;
	}

	public int getTotalOutCalls (String caller) {
		int ret = 1;
		
		CGNode src = getNodeByName (caller);
		if (null == src) return ret;
		
		for (CGEdge oe : _graph.outgoingEdgesOf(src)) {
			ret += oe.getFrequency();
		}
		return ret;
	}

	public int getTotalInCalls (String callee) {
		int ret = 1;
		
		CGNode tgt = getNodeByName (callee);
		if (null == tgt) return ret;
		
		for (CGEdge ie : _graph.incomingEdgesOf(tgt)) {
			ret += ie.getFrequency();
		}
		return ret;
	}
	
	public List<CGEdge> getPath(String caller, String callee) {
		/* debug only
		System.out.println("reachability from " + caller + " to " + callee + " in " + this);
		for (CGNode n1 : this._graph.vertexSet()) {
			for (CGNode n2 : this._graph.vertexSet()) {
				//System.out.println(n);
				if (n1==n2) continue;
				List<CGEdge> edges = DijkstraShortestPath.findPathBetween(_graph, n1, n2);
				if (edges==null || edges.size()<=5) continue;
				System.out.println(n1 + "->" + n2 + " with length of " + edges.size());
			}
		}
		//System.exit(-1);
		*/

		CGNode src = getNodeByName (caller);
		CGNode tgt = getNodeByName (callee);
		List<CGEdge> pathedges = new ArrayList<CGEdge>();
		if (null != src && null != tgt) {
			List<CGEdge> edges = DijkstraShortestPath.findPathBetween(_graph, src, tgt);
			if (edges==null) {
				//System.out.println("\t really no path is found");
				return pathedges;
			}
			else pathedges.addAll(edges);
		}
		else {
			System.out.println("\t failed to locate nodes for " + caller + " and " + callee);
		}
		if (pathedges.isEmpty()) {
			//System.out.println("\t really no path is found");
		}

		return pathedges;
		
		/*
		DijkstraShortestPath<CGNode, CGEdge> finder = new DijkstraShortestPath<CGNode, CGEdge>(_graph, src, tgt);
		if (null == finder.getPath()) {
			System.out.println("\t really no path is found");
			return new ArrayList<CGEdge>();
		}

		return finder.getPath().getEdgeList();
		*/
	}

	public boolean isReachableOrg (String caller, String callee) {
		if (caller.equalsIgnoreCase(callee)) return true;
		return !getPath(caller, callee).isEmpty();
	}

	public boolean isReachable (String caller, String callee) {
		if (caller.equalsIgnoreCase(callee)) return true;

		CGNode src = getNodeByName (caller);
		CGNode tgt = getNodeByName (callee);
		
		if (null != src && null != tgt) {
			NaiveLcaFinder<CGNode, CGEdge> lcafinder = new NaiveLcaFinder<CGNode, CGEdge>( this._graph );
			CGNode lca = lcafinder.findLca(src, tgt);
			if (null==lca) {
				return false;
			}
			else {
				//System.out.println("FOUND ONE TAINT FLOW from " + src + " to " + tgt);
				return true;
			}
		}
		else {
			System.out.println("\t failed to locate nodes for " + caller + " and " + callee);
		}
		return false;
	}

	/**
	 * apply timestamp constraints to prune impossible flows with respect to happens-before relation: the source call must 
	 * happen before the sink call for the flow to possibly happen
	 * @return number of flow paths
	 */
	public int getNumberOfReachableFlowsConservative (String caller, String callee) {
		if (caller.equalsIgnoreCase(callee)) return 0;

		CGNode src = getNodeByName (caller);
		CGNode tgt = getNodeByName (callee);
		
		if (null != src && null != tgt) {
			NaiveLcaFinder<CGNode, CGEdge> lcafinder = new NaiveLcaFinder<CGNode, CGEdge>( this._graph );
			CGNode lca = lcafinder.findLca(src, tgt);
			if (null==lca) {
				return 0;
			}
			else {
				//System.out.println("FOUND ONE TAINT FLOW from " + src + " to " + tgt);
				List<CGEdge> edges2src = new ArrayList<CGEdge>(DijkstraShortestPath.findPathBetween(_graph, lca, src));
				List<CGEdge> edges2sink = new ArrayList<CGEdge>(DijkstraShortestPath.findPathBetween(_graph, lca, tgt));
				//assert edges2src.size()>=1 && edges2sink.size()>=1;
				
				// remove instances of a call edge happened earlier than any instance of its ancestor edge
				int thinnestEdge2src = Integer.MAX_VALUE;
				int mints2src = 0;
				if (edges2src.size()>=1) {
					thinnestEdge2src = edges2src.get(0).getAllTS().size();
					Set<Integer> remainedsrc = new HashSet<Integer>();
					for (int i = 1; i < edges2src.size(); i++) {
						int mints = Collections.min(edges2src.get(i-1).getAllTS());
						Set<Integer> tss = edges2src.get(i).getAllTS();
						/*
						Set<Integer> toremove = new HashSet<Integer>();
						for (Integer ts : tss) {
							if (ts <= mints) toremove.add(ts);
						}
						tss.removeAll(toremove);
						if (tss.size() < thinnestEdge2src) {
							thinnestEdge2src = tss.size();
						}
						*/
						int nremove = 0;
						for (Integer ts : tss) {
							if (ts <= mints) nremove++;
							else if (i==edges2src.size()-1) remainedsrc.add(ts);
						}
						if (tss.size()-nremove < thinnestEdge2src) {
							thinnestEdge2src = tss.size()-nremove;
						}
					}

					assert edges2src.get(edges2src.size()-1).getTarget().equals(src);
					//Set<Integer> tses = new HashSet<Integer>(edges2src.get(edges2src.size()-1).getAllTS());
					//tses.removeAll(remainedsrc); // edges2src.get(edges2src.size()-1).getAllTS();
					Set<Integer> tses = remainedsrc;
					if (tses.isEmpty()) {
						/*
						this.sanityCheck();
						System.out.println("target edge's tses" + tses);
						System.out.println("problematic edge: " + edges2src.get(edges2src.size()-1));
						System.exit(-1);
						*/
						mints2src = Integer.MAX_VALUE;
						// there is no feasible flow path then
						return 0;
					}
					else {
						mints2src = Collections.min(tses);
					}
				}
				
				int thinnestEdge2sink = Integer.MAX_VALUE;
				if (edges2sink.size()>=1) {
					thinnestEdge2sink = edges2sink.get(0).getAllTS().size();
					Set<Integer> remainedsink = new HashSet<Integer>();
					for (int i = 1; i < edges2sink.size(); i++) {
						int mints = Collections.min(edges2sink.get(i-1).getAllTS());
						Set<Integer> tss = edges2sink.get(i).getAllTS();
						/*
						Set<Integer> toremove = new HashSet<Integer>();
						for (Integer ts : tss) {
							if (ts <= mints || ts <= mints2src) toremove.add(ts);
						}
						tss.removeAll(toremove);
						if (tss.size() < thinnestEdge2sink) {
							thinnestEdge2sink = tss.size();
						}
						*/
						int nremove = 0;
						for (Integer ts : tss) {
							if (ts <= mints || ts <= mints2src) nremove++;
							else if (i == edges2sink.size()-1) remainedsink.add(ts);
						}
						if (tss.size()-nremove < thinnestEdge2sink) {
							thinnestEdge2sink = tss.size()-nremove;
						}
					}
					//if (edges2sink.get(edges2sink.size()-1).getAllTS().isEmpty()) {
					//if (toremovesink.isEmpty()) {
					if (edges2sink.get(edges2sink.size()-1).getAllTS().size()-remainedsink.size()==0) {
						return 0;
					}
				}
				
				return (thinnestEdge2src > thinnestEdge2sink ? thinnestEdge2sink : thinnestEdge2src);
			}
		}
		else {
			System.out.println("\t failed to locate nodes for " + caller + " and " + callee);
		}
		return 0;
	}
	
	public int getNumberOfReachableFlows(String caller, String callee) {
		if (caller.equalsIgnoreCase(callee)) return 0;

		CGNode src = getNodeByName (caller);
		CGNode tgt = getNodeByName (callee);
		
		if (null != src && null != tgt) {
			NaiveLcaFinder<CGNode, CGEdge> lcafinder = new NaiveLcaFinder<CGNode, CGEdge>( this._graph );
			CGNode lca = lcafinder.findLca(src, tgt);
			if (null==lca) {
				return 0;
			}

			//System.out.println("FOUND ONE TAINT FLOW from " + src + " to " + tgt);
			List<CGEdge> edges2src = new ArrayList<CGEdge>(DijkstraShortestPath.findPathBetween(_graph, lca, src));
			List<CGEdge> edges2sink = new ArrayList<CGEdge>(DijkstraShortestPath.findPathBetween(_graph, lca, tgt));
			
			if (!(edges2src.size()>=1 && edges2sink.size()>=1)) return 0;
			
			Set<Integer> srcedgesizes = new HashSet<Integer>();
			for (int k = 0; k < edges2src.size(); k++) {
				srcedgesizes.add(edges2src.get(k).getAllTS().size());
			}
			int maxpossibleFlows = Collections.min(srcedgesizes);
			
			Set<Integer> sinkedgesizes = new HashSet<Integer>();
			for (int k = 0; k < edges2sink.size(); k++) {
				sinkedgesizes.add(edges2sink.get(k).getAllTS().size());
			}
			maxpossibleFlows = Math.min(maxpossibleFlows, Collections.min(sinkedgesizes));
			
			System.out.println("maxpossibleFlows=" + maxpossibleFlows);

			int nflows = 0;
			// as per the order instance timestamps were put into the TS set, the timestamps are ordered non-descendingly 
			List<Integer> srctss = new ArrayList<Integer>(edges2src.get(edges2src.size()-1).getAllTS());
			List<Integer> tgttss = new ArrayList<Integer>(edges2sink.get(0).getAllTS());
			for (int k=0; k < maxpossibleFlows; k++) {
				Integer sts = srctss.get(k);
				Integer tts = tgttss.get(k);
				if (tts <= sts) continue;

				int sinc=1;
				int i = edges2src.size()-2;
				for (; i >= 0 ;i--) {
					List<Integer> curtss = new ArrayList<Integer>(edges2src.get(i).getAllTS());			
					//if (!edges2src.get(i).getAllTS().contains(sts-sinc)) break;
					if (curtss.get(k)!=(sts-sinc)) break;
					sinc++;
				}
				if (i!=-1) continue; // no actual flow path reaching the instance of src at time sts
				
				int tinc=1;
				int j = 1;
				for (; j < edges2sink.size(); j++) {
					List<Integer> curtss = new ArrayList<Integer>(edges2sink.get(j).getAllTS());			
					//if (!edges2sink.get(j).getAllTS().contains(tts+tinc)) break;
					if (curtss.get(k)!=(tts+tinc)) break;
					tinc++;
				}
				if (j!=edges2sink.size()) continue;
				
				nflows ++;
			}
			
			return nflows;
		}
		else {
			System.out.println("\t failed to locate nodes for " + caller + " and " + callee);
		}
		return 0;
	}
	
	// a variant of getNumberofReachableFlows that considers all paths from lca to source/sink instead of the shortest one
	private int findFeasibleFlows(List<CGEdge> edges2src, List<CGEdge> edges2sink) {
		if (!(edges2src.size()>=1 && edges2sink.size()>=1)) return 0;
		
		Set<Integer> srcedgesizes = new HashSet<Integer>();
		for (int k = 0; k < edges2src.size(); k++) {
			srcedgesizes.add(edges2src.get(k).getAllTS().size());
		}
		int maxpossibleFlows = Collections.min(srcedgesizes);
		
		Set<Integer> sinkedgesizes = new HashSet<Integer>();
		for (int k = 0; k < edges2sink.size(); k++) {
			sinkedgesizes.add(edges2sink.get(k).getAllTS().size());
		}
		maxpossibleFlows = Math.min(maxpossibleFlows, Collections.min(sinkedgesizes));
		
		System.out.println("maxpossibleFlows=" + maxpossibleFlows);

		int nflows = 0;
		// as per the order instance timestamps were put into the TS set, the timestamps are ordered non-descendingly 
		List<Integer> srctss = new ArrayList<Integer>(edges2src.get(edges2src.size()-1).getAllTS());
		List<Integer> tgttss = new ArrayList<Integer>(edges2sink.get(0).getAllTS());
		for (int k=0; k < maxpossibleFlows; k++) {
			Integer sts = srctss.get(k);

			int sinc=1;
			int i = edges2src.size()-2;
			for (; i >= 0 ;i--) {
				List<Integer> curtss = new ArrayList<Integer>(edges2src.get(i).getAllTS());			
				//if (!edges2src.get(i).getAllTS().contains(sts-sinc)) break;
				if (curtss.get(k)!=(sts-sinc)) break;
				sinc++;
			}
			if (i!=-1) continue; // no actual flow path reaching the instance of src at time sts
			
			Integer tts = tgttss.get(k);
			if (tts <= sts) continue;
			
			int tinc=1;
			int j = 1;
			for (; j < edges2sink.size(); j++) {
				List<Integer> curtss = new ArrayList<Integer>(edges2sink.get(j).getAllTS());			
				//if (!edges2sink.get(j).getAllTS().contains(tts+tinc)) break;
				if (curtss.get(k)!=(tts+tinc)) break;
				tinc++;
			}
			if (j!=edges2sink.size()) continue;
			
			nflows ++;
		}
		
		return nflows;
	}
	public int getNumberOfReachableFlowsAll (String caller, String callee) {
		if (caller.equalsIgnoreCase(callee)) return 0;

		CGNode src = getNodeByName (caller);
		CGNode tgt = getNodeByName (callee);
		
		if (null != src && null != tgt) {
			NaiveLcaFinder<CGNode, CGEdge> lcafinder = new NaiveLcaFinder<CGNode, CGEdge>( this._graph );
			CGNode lca = lcafinder.findLca(src, tgt);
			if (null==lca) {
				return 0;
			}
			
			List<List<CGEdge>> allsrcedges = new ArrayList<List<CGEdge>>();
			List<List<CGEdge>> allsinkedges = new ArrayList<List<CGEdge>>();

			// AllDirectedPaths right now may miss paths of length < 2, so just to be safe
			List<CGEdge> edges2src_shortest = new ArrayList<CGEdge>(DijkstraShortestPath.findPathBetween(_graph, lca, src));
			List<CGEdge> edges2sink_shortest = new ArrayList<CGEdge>(DijkstraShortestPath.findPathBetween(_graph, lca, tgt));
			// no shortest paths, no need to search other paths either
			if (edges2src_shortest.size()<1 || edges2sink_shortest.size()<1) return 0;
			allsrcedges.add(edges2src_shortest);
			allsinkedges.add(edges2sink_shortest);
			
			AllDirectedPaths<CGNode, CGEdge> pathfinder = new AllDirectedPaths<CGNode, CGEdge>(_graph);
			List<GraphPath<CGNode, CGEdge>> lca2srcpaths = pathfinder.getAllPaths(lca, src, true, 15);
			List<GraphPath<CGNode, CGEdge>> lca2sinkpaths = pathfinder.getAllPaths(lca, tgt, true, 15);
			for (GraphPath<CGNode, CGEdge> path : lca2srcpaths) {
				if (path.getEdgeList()==null || path.getEdgeList().isEmpty()) continue;
				allsrcedges.add(path.getEdgeList());
			}
			for (GraphPath<CGNode, CGEdge> path : lca2sinkpaths) {
				if (path.getEdgeList()==null || path.getEdgeList().isEmpty()) continue;
				allsinkedges.add(path.getEdgeList());
			}
			
			System.out.println("#paths from LCA to src: " + allsrcedges.size() + ", #paths from LCA to sink: " + allsinkedges.size());

			int nflows = 0;
			
			for (List<CGEdge> edges2src : allsrcedges) {
				for (List<CGEdge> edges2sink : allsinkedges) {
					nflows += findFeasibleFlows(edges2src, edges2sink);
				}
			}
			return nflows;
		}
		else {
			System.out.println("\t failed to locate nodes for " + caller + " and " + callee);
		}
		return 0;
	}
	
	/** assume that the sensitive info retrieved by the direct caller of the source can propagate 
	 * one back-edge away only (to that direct caller) */
	public int getNumberOfReachableFlowsConservativeEx (String srcname, String sinkname) {
		if (srcname.equalsIgnoreCase(sinkname)) return 0;

		CGNode src = getNodeByName (srcname);
		CGNode tgt = getNodeByName (sinkname);
		
		if (null == src || null == tgt || this.getAllCallers(srcname).size()<1) return 0;
		
		int nflows = 0;
		for (CGNode caller : this.getAllCallers(srcname)) {
			List<CGEdge> edges2sink = new ArrayList<CGEdge>(DijkstraShortestPath.findPathBetween(_graph, caller, tgt));
			if (edges2sink==null || edges2sink.size()<1) continue;

			CGEdge srcedge = this.getEdgeByName(caller.getSootMethodName(), srcname);
			assert srcedge != null;
			int mints2src = Collections.min(srcedge.getAllTS());
			
			int thinnestEdge2sink = edges2sink.get(0).getAllTS().size();
			Set<Integer> remainedsink = new HashSet<Integer>();
			for (int i = 1; i < edges2sink.size(); i++) {
				int mints = Collections.min(edges2sink.get(i-1).getAllTS());
				Set<Integer> tss = edges2sink.get(i).getAllTS();
				/*
				Set<Integer> toremove = new HashSet<Integer>();
				for (Integer ts : tss) {
					if (ts <= mints || ts <= mints2src) toremove.add(ts);
				}
				tss.removeAll(toremove);
				if (tss.size() < thinnestEdge2sink) {
					thinnestEdge2sink = tss.size();
				}
				*/
				int nremove = 0;
				for (Integer ts : tss) {
					if (ts <= mints || ts <= mints2src) nremove++;
					else if (i==edges2sink.size()-1) remainedsink.add(ts);
				}
				if (tss.size()-nremove < thinnestEdge2sink) {
					thinnestEdge2sink = tss.size()-nremove;
				}
			}
			//if (edges2sink.get(edges2sink.size()-1).getAllTS().isEmpty()) {
			if (remainedsink.isEmpty()) {
				continue;
			}
			
			nflows += (thinnestEdge2sink > srcedge.getAllTS().size() ? srcedge.getAllTS().size():thinnestEdge2sink);
		}
		
		return nflows;
	}
	
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	static class CGEdgeComparator implements Comparator<CGEdge> {
		private CGEdgeComparator() {
		}
		
		private static final CGEdgeComparator cgcSingleton = new CGEdgeComparator(); 
		public static final CGEdgeComparator inst() { return cgcSingleton; }

		public int compare(CGEdge a, CGEdge b) {
			if ( a.getFrequency() > b.getFrequency() ) {
				return 1;
			}
			else if ( a.getFrequency() < b.getFrequency() ) {
				return -1;
			}
			return 0;
		}
	}
	
	public List<CGEdge> listEdgeByFrequency() { return listEdgeByFrequency(true); }
	public List<CGEdge> listEdgeByFrequency(boolean verbose) {
		if (verbose) {
			System.out.println("\n==== call frequencies ===\n ");
		}
		List<CGEdge> allEdges = new ArrayList<CGEdge>();
		allEdges.addAll(this._graph.edgeSet());
		Collections.sort(allEdges, CGEdgeComparator.inst());
		if (verbose) {
			for (CGEdge e : allEdges) {
				System.out.println(e);
			}
		}
		return allEdges;
	}
	
	public List<CGNode> listCallers() { return listCallers(true); }
	public List<CGNode> listCallers(boolean verbose) {
		if (verbose) {
			System.out.println("\n==== caller ranked by non-ascending fan-out  === \n");
		}
		List<CGNode> allNodes = new ArrayList<CGNode>();
		allNodes.addAll(this._graph.vertexSet());
		Collections.sort(allNodes, new Comparator<CGNode>() {
			public int compare(CGNode a, CGNode b) {
				if ( _graph.outDegreeOf(a) > _graph.outDegreeOf(b) ) {
					return 1;
				}
				else if ( _graph.outDegreeOf(a) < _graph.outDegreeOf(b) ) {
					return -1;
				}
				return 0;
			}
		});
		if (verbose) {
			for (CGNode n : allNodes) {
				System.out.println(n+":"+_graph.outDegreeOf(n));
			}
		}
		return allNodes;
	}
	
	public List<CGNode> listCallerInstances() { return listCallerInstances(true); }
	public List<CGNode> listCallerInstances(boolean verbose) {
		if (verbose) {
			System.out.println("\n==== caller ranked by non-ascending outgoing call instances  === \n");
		}
		List<CGNode> allNodes = new ArrayList<CGNode>();
		allNodes.addAll(this._graph.vertexSet());
		Collections.sort(allNodes, new Comparator<CGNode>() {
			public int compare(CGNode a, CGNode b) {
				if ( getTotalOutCalls(a.getMethodName()) > getTotalOutCalls(b.getMethodName()) ) {
					return 1;
				}
				else if ( getTotalOutCalls(a.getMethodName()) < getTotalOutCalls(b.getMethodName()) ) {
					return -1;
				}
				return 0;
			}
		});
		if (verbose) {
			for (CGNode n : allNodes) {
				System.out.println(n+":"+ getTotalOutCalls(n.getMethodName()));
			}
		}
		return allNodes;
	}
	
	public List<CGNode> listCallees() { return listCallees(true); }
	public List<CGNode> listCallees(boolean verbose) {
		if (verbose) {
			System.out.println("\n==== callee ranked by non-ascending fan-in  === \n");
		}
		List<CGNode> allNodes = new ArrayList<CGNode>();
		allNodes.addAll(this._graph.vertexSet());
		Collections.sort(allNodes, new Comparator<CGNode>() {
			public int compare(CGNode a, CGNode b) {
				if ( _graph.inDegreeOf(a) > _graph.inDegreeOf(b) ) {
					return 1;
				}
				else if ( _graph.inDegreeOf(a) < _graph.inDegreeOf(b) ) {
					return -1;
				}
				return 0;
			}
		});
		if (verbose) {
			for (CGNode n : allNodes) {
				System.out.println(n+":"+_graph.inDegreeOf(n));
			}
		}
		return allNodes;
	}
	
	public List<CGNode> listCalleeInstances() { return listCalleeInstances(true); }
	public List<CGNode> listCalleeInstances(boolean verbose) {
		if (verbose) {
			System.out.println("\n==== callee ranked by non-ascending incoming call instances === \n");
		}
		List<CGNode> allNodes = new ArrayList<CGNode>();
		allNodes.addAll(this._graph.vertexSet());
		Collections.sort(allNodes, new Comparator<CGNode>() {
			public int compare(CGNode a, CGNode b) {
				if ( getTotalInCalls(a.getMethodName()) > getTotalInCalls(b.getMethodName()) ) {
					return 1;
				}
				else if ( getTotalInCalls(a.getMethodName()) < getTotalInCalls(b.getMethodName()) ) {
					return -1;
				}
				return 0;
			}
		});
		if (verbose) {
			for (CGNode n : allNodes) {
				System.out.println(n+":"+ getTotalInCalls(n.getMethodName()));
			}
		}
		return allNodes;
	}
}

/* vim :set ts=4 tw=4 tws=4 */


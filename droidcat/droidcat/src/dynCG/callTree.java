/**
 * File: src/dynCG/callTree.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      Changes
 * -------------------------------------------------------------------------------------------
 * 05/11/16		hcai		created; for representing dynamic call tree
 * 05/13/16		hcai		reached the first working version
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

import dynCG.callGraph.CGEdge;

/** represent the dynamic call graph built from whole program path profiles */
public class callTree {
	public static final String CALL_DELIMIT = " -> ";
	// bijective mapping between string and integer tagging of canonically named method (package + class + method signature)
	public static final Map<String, Integer> g_me2idx = new HashMap<String, Integer>();
	public static final Map<Integer, String> g_idx2me = new HashMap<Integer,String>();
	
	public String toString() {
		return "(dynamic) call tree: " + g_me2idx.size() + " methods; " + 
				this._graph.vertexSet().size() + " nodes; " + this._graph.edgeSet().size() + " edges.";
	}

	public static class CGNode {
		// method index
		private Integer idx;
		private Integer ts; // timestamp for a particular instance of an executed method
		CGNode() {
			this(-1,0);
		}
		CGNode(int _idx, Integer _ts) {
			idx = _idx;
			ts = _ts;
		}
		public int getIndex () {
			return idx;
		}
		public int getTimestamp() {
			return ts;
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
			boolean c1 = ((CGNode)other).idx.intValue() == this.idx.intValue();
			boolean c2 = ((CGNode)other).ts.intValue() == this.ts.intValue();
			return c1 && c2;
		}
		public int hashCode() {
			return idx.hashCode()+ts.hashCode();
		}
		
		public String toString() {
			return getMethodName() + "[idx=" + idx + ",ts=" + ts + "]";
		}
	}
	
	public static class CGNodeFactory implements VertexFactory<CGNode> {
		@Override
		public CGNode createVertex() {
			return new CGNode();
		}
	}
	
	// use the timestamp of target as the timestamp of the edge
	public static class CGEdge {
		private CGNode src;
		private CGNode tgt;
		CGEdge(CGNode _src, CGNode _tgt) {
			src = _src;
			tgt = _tgt;
		}
		public CGNode getSource() {
			return src;
		}
		public CGNode getTarget() {
			return tgt;
		}
		
		public String toString() {
			return "["+g_idx2me.get(src.getIndex()) + "->" + g_idx2me.get(tgt.getIndex())+"]";
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
	
	// mapping from method id to its latest instance node on the tree
	private final Map<Integer, CGNode> m2lastIns = new HashMap<Integer, CGNode>(); 
	CGNode getCreateLastIns(Integer mid, Integer ts) { 
		if (!m2lastIns.containsKey(mid)) {
			m2lastIns.put(mid, new CGNode(mid, ts));
		}
		return m2lastIns.get(mid);
	}
	CGNode getCreateLastIns(CGNode n) { 
		return getCreateLastIns (n.getIndex(),n.getTimestamp());
	}
	void updateLastIns(CGNode n) { 
		m2lastIns.put(n.getIndex(), n);
	}
	
	callTree() {
	}
	
	public DirectedGraph<CGNode, CGEdge> getInternalGraph() { return _graph; }
	
	public CGEdge addEdge(CGNode src, CGNode tgt) {
		if (!_graph.containsVertex(src)) _graph.addVertex(src);
		if (!_graph.containsVertex(tgt)) _graph.addVertex(tgt);

		CGEdge ret = _graph.getEdge(src, tgt);
		if (null==ret) {
			ret = new CGEdge(src,tgt);
			_graph.addEdge(src, tgt, ret);
		}
		return ret;
	}
	
	private CGNode getCreateNode(int mid, int ts) {
		CGNode tobe = new CGNode(mid,ts);
		if (!_graph.containsVertex(tobe)) return tobe;
		for (CGNode n : _graph.vertexSet()) {
			if (tobe.equals(n)) {
				return n;
			}
		}
		return null;
	}
	
	public CGEdge addCall (int caller, int callee, int ts) {
		//addEdge(new CGNode(caller), new CGNode(callee));
		CGNode tgt = getCreateNode(callee,ts);
		CGEdge ret = addEdge (getCreateLastIns(caller, ts), tgt);
		updateLastIns(tgt);
		return ret;
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
	
	public Set<CGNode> getNodesByName (String mename) {
		Set<CGNode> ret = new HashSet<CGNode>();
		for (CGNode cgn : _graph.vertexSet()) {
			if (cgn.getMethodName().equalsIgnoreCase(mename) || cgn.getSootMethodName().equalsIgnoreCase(mename))  {
				ret.add(cgn);
			}
		}
		return ret;
	}
	
	public Set<CGEdge> getEdgesByName(String caller, String callee) {
		Set<CGEdge> ret = new HashSet<CGEdge>();
		Set<CGNode> srcs = getNodesByName (caller);
		Set<CGNode> tgts = getNodesByName (callee);
		if (srcs.isEmpty() || tgts.isEmpty()) return ret;
		
		for (CGNode src:srcs) {
			for (CGNode tgt:tgts) {
				CGEdge e = _graph.getEdge(src, tgt);
				if (e!=null) {
					ret.add(e);
				}
			}
		}
		
		return ret;
	}
	
	public Set<CGNode> getAllCallees (String caller) {
		Set<CGNode> ret = new HashSet<CGNode>();
		
		Set<CGNode> srcs = getNodesByName (caller);
		if (srcs.isEmpty()) return ret;
		
		for (CGNode src:srcs) {
			for (CGEdge oe : _graph.outgoingEdgesOf(src)) {
				ret.add(oe.getTarget());
			}
		}
		return ret;
	}

	public Set<CGNode> getAllCallers (String callee) {
		Set<CGNode> ret = new HashSet<CGNode>();
		
		Set<CGNode> tgts = getNodesByName (callee);
		if (tgts.isEmpty()) return ret;
		
		for (CGNode tgt:tgts) {
			for (CGEdge ie : _graph.incomingEdgesOf(tgt)) {
				ret.add(ie.getSource());
			}
		}
		return ret;
	}

	public int getTotalOutCalls (String caller) {
		int ret = 0;
		
		Set<CGNode> srcs = getNodesByName (caller);
		if (srcs.isEmpty()) return ret;
		
		for (CGNode src:srcs) {
			ret += _graph.outDegreeOf(src);
		}
		return ret;
	}

	public int getTotalInCalls (String callee) {
		int ret = 0;
		
		Set<CGNode> tgts = getNodesByName (callee);
		if (tgts.isEmpty()) return ret;
		
		for (CGNode tgt:tgts) {
			ret += _graph.inDegreeOf(tgt);
		}
		return ret;
	}
	
	public List<List<CGEdge>> getPaths(String caller, String callee) {
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

		Set<CGNode> srcs = getNodesByName (caller);
		Set<CGNode> tgts = getNodesByName (callee);
		List<List<CGEdge>> allpaths = new ArrayList<List<CGEdge>>();
		for (CGNode src:srcs) {
			for (CGNode tgt:tgts) {
				if (src.getTimestamp()>=tgt.getTimestamp()) continue;
				if (null != src && null != tgt) {
					List<CGEdge> edges = DijkstraShortestPath.findPathBetween(_graph, src, tgt);
					if (edges!=null && !edges.isEmpty()) {
						allpaths.add(edges);
					}
				}
			}
		}

		return allpaths;
	}

	public boolean isReachableOrg (String caller, String callee) {
		if (caller.equalsIgnoreCase(callee)) return true;
		return !getPaths(caller, callee).isEmpty();
	}

	public boolean isReachable (String caller, String callee) {
		if (caller.equalsIgnoreCase(callee)) return true;

		Set<CGNode> srcs = getNodesByName (caller);
		Set<CGNode> tgts = getNodesByName (callee);
		
		for (CGNode src:srcs) {
			for (CGNode tgt:tgts) {
				if (src.getTimestamp()>=tgt.getTimestamp()) continue;
				NaiveLcaFinder<CGNode, CGEdge> lcafinder = new NaiveLcaFinder<CGNode, CGEdge>( this._graph );
				CGNode lca = lcafinder.findLca(src, tgt);
				if (null!=lca) return true;
			}
		}
		return false;
	}

	/**
	 * apply timestamp constraints to prune impossible flows with respect to happens-before relation: the source call must 
	 * happen before the sink call for the flow to possibly happen
	 * @return number of flow paths
	 */
	public int getNumberOfReachableFlows (String caller, String callee) {
		if (caller.equalsIgnoreCase(callee)) return 0;

		Set<CGNode> srcs = getNodesByName (caller);
		Set<CGNode> tgts = getNodesByName (callee);
		
		System.out.println(srcs.size() + " nodes found for caller " + caller);
		System.out.println(tgts.size() + " nodes found for callee " + callee);
		
		NaiveLcaFinder<CGNode, CGEdge> lcafinder = new NaiveLcaFinder<CGNode, CGEdge>( this._graph );
		int nflows = 0;
		for (CGNode src:srcs) {
			for (CGNode tgt:tgts) {
				if (src.getTimestamp()>=tgt.getTimestamp()) continue;
				CGNode lca = lcafinder.findLca(src, tgt);
				if (null==lca) {
					continue;
				}
				if (lca.equals(src) || lca.equals(tgt)) continue;
				
				//System.out.print("\nlooking for paths from lca " + lca + " to src " + src + " and tgt " + tgt + "....");
				List<CGEdge> edges2src = new ArrayList<CGEdge>(DijkstraShortestPath.findPathBetween(_graph, lca, src));
				List<CGEdge> edges2sink = new ArrayList<CGEdge>(DijkstraShortestPath.findPathBetween(_graph, lca, tgt));
				//System.out.println("Done");
				
				if (!(edges2src.size()>=1 && edges2sink.size()>=1)) continue;
				
				Integer sts = edges2src.get(edges2src.size()-1).getTarget().getTimestamp();
				Integer tts = edges2sink.get(0).getTarget().getTimestamp();
				if (tts <= sts) continue;

				int sinc=1;
				int i = edges2src.size()-2;
				for (; i >= 0 ;i--) {
					if (edges2src.get(i).getTarget().getTimestamp() != (sts - sinc)) break;
					sinc++;
				}
				if (i!=-1) continue; // no actual flow path reaching the instance of src at time sts
				
				int tinc=1;
				int j = 1;
				for (; j < edges2sink.size(); j++) {
					if (edges2sink.get(j).getTarget().getTimestamp() != (tts+tinc)) break;			
					tinc++;
				}
				if (j!=edges2sink.size()) continue;
				
				nflows ++;
			}
		}

		return nflows;
	}
	
	/** assume that the sensitive info retrieved by the direct caller of the source can propagate one back-edge away */
	public int getNumberOfReachableFlowsEx (String srcname, String sinkname) {
		if (srcname.equalsIgnoreCase(sinkname)) return 0;
		
		Set<CGNode> tgts = getNodesByName (sinkname);
		
		int nflows = 0;
		for (CGNode caller : this.getAllCallers(srcname)) {
			for (CGNode tgt:tgts) {
				List<CGEdge> edges2sink = new ArrayList<CGEdge>(DijkstraShortestPath.findPathBetween(_graph, caller, tgt));
				if (edges2sink==null || edges2sink.size()<1) continue;
				
				Integer sts = caller.getTimestamp();
				Integer tts = edges2sink.get(0).getTarget().getTimestamp();
				if (tts <= sts) continue;
				
				int tinc=1;
				int j = 1;
				for (; j < edges2sink.size(); j++) {
					if (edges2sink.get(j).getTarget().getTimestamp() != (tts+tinc)) break;			
					tinc++;
				}
				if (j!=edges2sink.size()) continue;
				
				nflows ++;
			}
		}
		return nflows;
	}
}

/* vim :set ts=4 tw=4 tws=4 */


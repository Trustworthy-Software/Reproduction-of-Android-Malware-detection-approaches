/**
 * File: src/iacUtil/MethodEventComparator.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      	Changes
 * -------------------------------------------------------------------------------------------
 * 07/05/13		hcai			Created; for method event comparison in function-level profiling
 *
*/
package iacUtil;

import java.util.Comparator;
import java.util.Map;

/* comparator used for sorting the method event map according to time stamps */
public class MethodEventComparator implements Comparator<String> {
	Map<String, Integer> base;
	public MethodEventComparator( Map<String, Integer> base ) {
		this.base = base;
	}

	public int compare(String x, String y) {
		Integer a = (Integer) base.get(x);
		Integer b = (Integer) base.get(y);
		// for sorting in non-descending order
		if ( a > b ) {
			return 1;
		}
		else if ( a < b ) {
			return -1;
		}
		return 0;
	}
}

/* vim :set ts=4 tw=4 tws=4 */


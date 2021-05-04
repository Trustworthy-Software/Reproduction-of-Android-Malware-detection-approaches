/**
 * File: src/reporter/covStat.java
 * -------------------------------------------------------------------------------------------
 * Date			Author      Changes
 * -------------------------------------------------------------------------------------------
 * 01/06/16		hcai		created; as common coverage statistics encapsulation
 *
*/
package reporters;

/** store the coverage at different levels of granularity */
public class covStat {
	public covStat () {
		covered = 0;
		total = 0;
		tag = "unknown";
	}
	public covStat(String _tag) {
		this();
		tag = _tag;
	}
	private String tag;
	private int covered;
	private int total; 
	public void incCovered (int increment) { covered += increment; }
	public void incTotal (int increment) { total += increment; }
	public void incCovered () { incCovered(1); }
	public void incTotal () { incTotal(1); }
	public int getCovered() { return covered; }
	public int getTotal() { return total; }
	public double getCoverage () {
		if (total == 0) return .0D;
		return (double)(covered * 1.0 / total); 
	}
	@Override public String toString() {
		return tag + " " + covered + " covered out of " + total + " for a coverage of " + getCoverage(); 
	}
}

/* vim :set ts=4 tw=4 tws=4 */


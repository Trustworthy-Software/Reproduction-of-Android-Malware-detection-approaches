package edu.uci.seal.flow.reflect;

import org.javatuples.Pair;

import soot.SootMethod;
import soot.Unit;
import soot.Value;

public class MethodInvokeFact {
	public Pair<Value,Value> valuePair;
	public Unit originUnit;
	public SootMethod originMethod;
	
	public MethodInvokeFact(Pair<Value,Value> valuePair, Unit curr, SootMethod methodOf) {
		this.valuePair = valuePair;
		this.originUnit = curr;
		this.originMethod = methodOf;
	}
	
	public String toString() {
		return valuePair + "#" + originUnit + "#" + originMethod;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((originMethod == null) ? 0 : originMethod.toString().hashCode());
		result = prime * result + ((originUnit == null) ? 0 : originUnit.toString().hashCode());
		result = prime * result + ((valuePair == null) ? 0 : valuePair.toString().hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MethodInvokeFact other = (MethodInvokeFact) obj;
		if (originMethod == null) {
			if (other.originMethod != null)
				return false;
		} else if (!originMethod.toString().equals(other.originMethod.toString()))
			return false;
		if (originUnit == null) {
			if (other.originUnit != null)
				return false;
		} else if (!originUnit.toString().equals(other.originUnit.toString()))
			return false;
		if (valuePair == null) {
			if (other.valuePair != null)
				return false;
		} else if (!valuePair.toString().equals(other.valuePair.toString()))
			return false;
		return true;
	}

}

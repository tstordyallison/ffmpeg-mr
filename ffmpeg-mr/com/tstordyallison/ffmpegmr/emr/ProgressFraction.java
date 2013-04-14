package com.tstordyallison.ffmpegmr.emr;

public class ProgressFraction{

	public ProgressFraction(int current, int total) {
		this.currentValue = current;
		this.totalValue = total;
	}
	private int currentValue = 0;
	private int totalValue = 0;
	public int getCurrentValue() {
		return currentValue;
	}
	public int getTotalValue() {
		return totalValue;
	}
	public double getProgressPercentDouble(){
		return ((double)currentValue/totalValue)*100;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + currentValue;
		result = prime * result + totalValue;
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof ProgressFraction))
			return false;
		ProgressFraction other = (ProgressFraction) obj;
		if (currentValue != other.currentValue)
			return false;
		if (totalValue != other.totalValue)
			return false;
		return true;
	}
	@Override
	public String toString() {
		return "[currentValue=" + currentValue + ", totalValue=" + totalValue + "]";
	}
}
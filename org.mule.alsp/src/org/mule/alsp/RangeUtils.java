package org.mule.alsp;

import java.util.List;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;

public class RangeUtils {
	private RangeUtils() {
		
	}
	
	public static String rangeToString(Range range) {
		return "[" + positionToString(range.getStart()) + ", " + positionToString(range.getEnd()) + "]";
	}
	
	public static String positionToString(Position pos) {
		return "(" + pos.getLine() + ", " + pos.getCharacter() + ")";
	}
	
	public static Range getRange(List<SymbolInformation> list) {
		Position min = new Position(Integer.MAX_VALUE, Integer.MAX_VALUE);
		Position max = new Position(0, 0);
		
		for(int i = 0; i < list.size(); i++) {
			SymbolInformation node = list.get(i);
			
			Position start = node.getLocation().getRange().getStart();
			Position end = node.getLocation().getRange().getEnd();
			
			if(comparePositions(min, start)) {
				min = start;
			}
			
			if(comparePositions(end, max)) {
				max = end;
			}
		}
		
		return new Range(min, new Position(max.getLine() + 1, max.getCharacter() + 1));
	}
	
	public static  boolean compareRanges(Range rg1, Range rg2) {
		if(comparePositions(rg1.getStart(), rg2.getStart())) {
			return false;
		}
		
		if(comparePositions(rg2.getEnd(), rg1.getEnd())) {
			return false;
		}
		
		return comparePositions(rg2.getStart(), rg1.getStart());
	}
	
	public static  boolean comparePositions(Position pos1, Position pos2) {
		if(pos1.getLine() > pos2.getLine()) {
			return true;
		}
		
		if(pos1.getLine() == pos2.getLine() && pos1.getCharacter() > pos2.getCharacter()) {
			return true;
		}
		
		return false;
	}
}

package org.eclipse.lsp4e.outline;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;

class SymbolsTree {
	private SymbolInformation info;
	
	private SymbolInformation parent;
	
	private List<SymbolsTree> children = new ArrayList<SymbolsTree>();
	
	private static final String CATEGORY_ID = "categories";
	
	public SymbolsTree() {
		
	}
	
	private SymbolsTree(SymbolInformation info) {
		this.info = info;
	}
	
	public void update(List<SymbolInformation> infos) {
		children = new ArrayList<SymbolsTree>();
		
		infos.forEach(info -> addChild(info));
		
		setParent();
	}
	
	public Object getParent(Object child) {
		if(!(child instanceof SymbolInformation)) {
			return null;
		}
		
		SymbolsTree owner = findOwner((SymbolInformation) child);
		
		if(owner == null) {
			return null;
		}
		
		return owner.parent;
	}
	
	public Object[] getChildren(Object parent) {
		if(!(parent instanceof SymbolInformation)) {
			return new Object[0];
		}
		
		SymbolsTree owner = findOwner((SymbolInformation) parent);
		
		if(owner == null) {
			return new Object[0];
		}
		
		return owner.getElements();
	}
	
	public Object[] getElements() {
		return children.stream().map(item -> item.info).toArray();
	}
	
	private void setParent() {
		children.forEach(child -> {
			child.parent = this.info;
			
			child.setParent();
		});
	}
	
	private SymbolsTree findOwner(SymbolInformation parent) {
		if(this.info == parent) {
			return this;
		}
		
		for(SymbolsTree child: children) {
			SymbolsTree canBeOwner = child.findOwner(parent);
			
			if(canBeOwner == null) {
				continue;
			}
			
			return canBeOwner;
		}
		
		return null;
	}
	
	private void addChild(SymbolInformation info) {
		int index = indexOfParentOf(info);
		
		if(index != -1) {
			SymbolsTree parent = children.get(index);
			
			parent.addChild(info);
			
			return;
		}
		
		SymbolsTree newItem = new SymbolsTree(info);
		
		index = indexOfChildOf(info);
		
		if(index != -1) {			
			SymbolInformation child = children.get(index).info;
			
			children.remove(index);
			
			newItem.addChild(child);
		}
		
		children.add(newItem);
	}
	
	private int indexOfParentOf(SymbolInformation info) {
		for(int i = 0; i < children.size(); i++) {
			if(isIncluded(children.get(i).info, info)) {
				return i;
			}
		}
		
		return -1;
	}
	
	private int indexOfChildOf(SymbolInformation info) {
		for(int i = 0; i < children.size(); i++) {
			if(isIncluded(info, children.get(i).info)) {
				return i;
			}
		}
		
		return -1;
	}
	
	private boolean isIncluded(SymbolInformation parent, SymbolInformation symbol) {
		if(isCategory(symbol)) {
			return false;
		}
		
		if(isCategory(parent)) {
			return symbol.getContainerName().equals(parent.getName());
		}
		
		if(!symbol.getContainerName().equals(parent.getContainerName())) {
			return false;
		}
		
		return isIncluded(parent.getLocation(), symbol.getLocation());
	}
	
	private boolean isIncluded(Location reference, Location included) {
		if(!reference.getUri().equals(included.getUri())) {
			return false;
		}
		
		if(!isAfter(reference.getRange().getStart(), included.getRange().getStart())) {
			return false;
		}
		
		if(isAfter(included.getRange().getEnd(), reference.getRange().getEnd())) {
			return false;
		}
		
		return true;
	}
		
	private boolean isAfter(Position reference, Position included) {
		return included.getLine() > reference.getLine() || (included.getLine() == reference.getLine() && included.getCharacter() > reference.getCharacter());
	}
	
	public static boolean isCategory(SymbolInformation symbol) {
		return CATEGORY_ID.equals(symbol.getContainerName());
	}
	
	public static boolean isValidRange(SymbolInformation symbol) {
		Range range = symbol.getLocation().getRange();
		
		return isValidPosition(range.getStart()) && isValidPosition(range.getEnd());
	}
	
	private static boolean isValidPosition(Position position) {
		return position.getLine() >= 0 && position.getCharacter() >= 0;
	}
}
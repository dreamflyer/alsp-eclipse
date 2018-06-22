/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.outline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;

public class SymbolsModel {
	private static final SymbolInformation ROOT = new SymbolInformation();
	
	private static final Object[] EMPTY = new Object[0];
	
	private Map<SymbolInfoWrapper, List<SymbolInformation>> childrenMap = new HashMap<>();
	
	public boolean update(List<? extends SymbolInformation> response) {
		childrenMap.clear();
		
		if(response != null && !response.isEmpty()) {
			Collections.sort(response, new Comparator<SymbolInformation>() {
				public int compare(SymbolInformation o1, SymbolInformation o2) {
					Range r1 = o1.getLocation().getRange();
					Range r2 = o2.getLocation().getRange();
					
					if(r1.getStart().getLine() == r2.getStart().getLine()) {
						return Integer.compare(r1.getStart().getCharacter(), r2.getStart().getCharacter());
					}
					
					return Integer.compare(r1.getStart().getLine(), r2.getStart().getLine());
				}
			});
			
			Stack<SymbolInfoWrapper> parentStack = new Stack<>();
			
			parentStack.push(new SymbolInfoWrapper(ROOT));
			
			SymbolInformation previousSymbol = null;
			
			for(int i = 0; i < response.size(); i++) {
				SymbolInformation symbol = response.get(i);
				
				if(isIncluded(previousSymbol, symbol)) {
					parentStack.push(new SymbolInfoWrapper(previousSymbol));
					
					addChild(parentStack.peek(), symbol);
				} else if(isIncluded(parentStack.peek().info, symbol)) {
					addChild(parentStack.peek(), symbol);
				} else {
					while(!isIncluded(parentStack.peek().info, symbol)) {
						parentStack.pop();
					}
					
					addChild(parentStack.peek(), symbol);
					
					parentStack.push(new SymbolInfoWrapper(symbol));
				}
				
				previousSymbol = symbol;
			}
		}
		return true;
	}
	
	private boolean isIncluded(SymbolInformation parent, SymbolInformation symbol) {
		if(parent == null || symbol == null) {
			return false;
		}
		
		if(parent == ROOT) {
			return true;
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
	
	private void addChild(SymbolInfoWrapper parent, SymbolInformation child) {
		List<SymbolInformation> children = childrenMap.get(parent);
		
		if(children == null) {
			children = new ArrayList<>();
			
			childrenMap.put(parent, children);
		}
		
		children.add(child);
	}
	
	public Object[] getElements() {
		return getChildren(ROOT);
	}
	
	public Object[] getChildren(Object parentElement) {
		if(parentElement != null && parentElement instanceof SymbolInformation) {
			List<SymbolInformation> children = childrenMap.get(new SymbolInfoWrapper((SymbolInformation) parentElement));
			
			if(children != null) {
				return children.toArray();
			}
		}
		
		return EMPTY;
	}
	
	public Object getParent(Object element) {
		Optional<SymbolInfoWrapper> result = childrenMap.keySet().stream().filter(parent -> {
			List<SymbolInformation> children = childrenMap.get(parent);
			
			return children == null ? false : children.contains(element);
		}).findFirst();
		
		return result.isPresent() ? result.get() : null;
	}
}

class SymbolInfoWrapper {
	SymbolInformation info;
	
	SymbolInfoWrapper(SymbolInformation info) {
		this.info = info;
	}
	
	public int hashCode() {
		return info.hashCode();
	}
	
	public boolean equals(Object obj) {
		if(obj instanceof SymbolInfoWrapper) {
			SymbolInfoWrapper another = (SymbolInfoWrapper) obj;
			
			return another.info == this.info;
		}
		
		return false;
	}
}

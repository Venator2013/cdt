/*******************************************************************************
 * Copyright (c) 2014 Ericsson, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Marc-Andre Laperle - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.ui.search;

import org.eclipse.search.ui.text.Match;
import org.eclipse.search.ui.text.MatchFilter;

/**
 * A filter class that implements hiding the Read-only references.
 */
public class HideReadOnlyReferences extends MatchFilter {
	public static final MatchFilter READ_ONLY_FILTER = new HideReadOnlyReferences();

	public HideReadOnlyReferences() {
		super();
	}

	@Override
	public boolean filters(Match match) {
		return match instanceof CSearchMatch && !((CSearchMatch) match).isWriteAccess();
	}

	@Override
	public String getActionLabel() {
		return CSearchMessages.HideReadOnlyReferences_actionLabel;
	}

	@Override
	public String getDescription() {
		return CSearchMessages.HideReadOnlyReferences_description;
	}

	@Override
	public String getID() {
		return "HideReadOnlyReferences"; //$NON-NLS-1$
	}

	@Override
	public String getName() {
		return CSearchMessages.HideReadOnlyReferences_name;
	}
}

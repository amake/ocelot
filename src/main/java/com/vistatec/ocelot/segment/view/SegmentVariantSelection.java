/*
 * Copyright (C) 2015, VistaTEC or third-party contributors as indicated
 * by the @author tags or express copyright attribution statements applied by
 * the authors. All third-party contributions are distributed under license by
 * VistaTEC.
 *
 * This file is part of Ocelot.
 *
 * Ocelot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Ocelot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, write to:
 *
 *     Free Software Foundation, Inc.
 *     51 Franklin Street, Fifth Floor
 *     Boston, MA 02110-1301
 *     USA
 *
 * Also, see the full LGPL text here: <http://www.gnu.org/copyleft/lesser.html>
 */
package com.vistatec.ocelot.segment.view;

import com.vistatec.ocelot.segment.model.SegmentVariant;

/**
 * Represents a clipboard selection of SegmentVariant content.
 */
public class SegmentVariantSelection {
    private int row;
    private SegmentVariant variant;
    // Indexes into the display representation of variant
    private int selectionStart, selectionEnd;

    public SegmentVariantSelection(int row, SegmentVariant variant, int start, int end) {
        this.row = row;
        this.variant = variant;
        this.selectionStart = start;
        this.selectionEnd = end;
    }

    public int getRow() {
        return row;
    }

    public SegmentVariant getVariant() {
        return variant;
    }

    public int getSelectionStart() {
        return selectionStart;
    }

    public int getSelectionEnd() {
        return selectionEnd;
    }

    @Override
    public String toString() {
        return "Row " + row + " [" + selectionStart + ", " + selectionEnd + "] of " + variant;
    }

    public String getDisplayText() {
        return variant.getDisplayText().substring(selectionStart, selectionEnd);
    }
}

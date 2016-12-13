package com.vladsch.flexmark.ext.aside;

import com.vladsch.flexmark.ast.BlockContent;
import com.vladsch.flexmark.ast.CustomBlock;
import com.vladsch.flexmark.util.sequence.BasedSequence;
import com.vladsch.flexmark.util.sequence.SubSequence;

import java.util.List;

/**
 * A ExtAside block node
 */
public class AsideBlock extends CustomBlock {
    private BasedSequence openingMarker = SubSequence.NULL;

    @Override
    public void getAstExtra(StringBuilder out) {
        segmentSpanChars(out, openingMarker, "marker");
    }

    @Override
    public BasedSequence[] getSegments() {
        return new BasedSequence[] { openingMarker };
    }

    public AsideBlock() {
    }

    public AsideBlock(BasedSequence chars) {
        super(chars);
    }

    public AsideBlock(BasedSequence chars, List<BasedSequence> segments) {
        super(chars, segments);
    }

    public AsideBlock(BlockContent blockContent) {
        super(blockContent);
    }

    public BasedSequence getOpeningMarker() {
        return openingMarker;
    }

    public void setOpeningMarker(BasedSequence openingMarker) {
        this.openingMarker = openingMarker;
    }
}
package net.coderbot.batchedentityrendering.impl;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.datafixers.util.Pair;
import net.coderbot.batchedentityrendering.mixin.RenderTypeAccessor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SegmentedBufferBuilder implements MultiBufferSource, MemoryTrackingBuffer {
    private final BufferBuilder buffer;
    private final List<RenderType> usedTypes;
    private RenderType currentType;

    public SegmentedBufferBuilder() {
        // 2 MB initial allocation
        this.buffer = new BufferBuilder(512 * 1024);
        this.usedTypes = new ArrayList<>(256);

        this.currentType = null;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        if (!Objects.equals(currentType, renderType)) {
            if (currentType != null) {
                buffer.end();
                usedTypes.add(currentType);
            }
            if (shouldSortOnUpload(currentType)) { //*DESIGN CHANGE* put if statement right here 
                buffer.sortQuads(0, 0, 0);
            }

            buffer.begin(renderType.mode(), renderType.format());

            currentType = renderType;
        }

        // Use duplicate vertices to break up triangle strips
        // https://developer.apple.com/library/archive/documentation/3DDrawing/Conceptual/OpenGLES_ProgrammingGuide/Art/degenerate_triangle_strip_2x.png
        // This works by generating zero-area triangles that don't end up getting rendered.
        // TODO: How do we handle DEBUG_LINE_STRIP?
        if (RenderTypeUtil.isTriangleStripDrawMode(currentType)) {
            ((BufferBuilderExt) buffer).splitStrip();
        }

        return buffer;
    }

    public List<BufferSegment> getSegments() {
    	final List<BufferSegment> segments = new ArrayList<>(usedTypes.size()); //*CODE STYLE* made list final
    	 
        if (currentType == null) { //*CODE STYLE* made segments equal previous return value, and put everything else in the else stmt 
            segments = Collections.emptyList();
        } else {
        	usedTypes.add(currentType);

        	if (shouldSortOnUpload(currentType)) {
        		buffer.sortQuads(0, 0, 0);
        	}

        	buffer.end();
        	currentType = null;

       

        	for (RenderType type : usedTypes) {
        		Pair<BufferBuilder.DrawState, ByteBuffer> pair = buffer.popNextBuffer();

        		BufferBuilder.DrawState drawState; //*DESIGN* I separated the variable by creating them first
        		ByteBuffer slice; 
        		drawState = pair.getFirst();
        		slice = pair.getSecond();

        		segments.add(new BufferSegment(slice, drawState, type));
        	}

        	usedTypes.clear();
        }

        return segments;
    }

    private static boolean shouldSortOnUpload(final RenderType type) { //*CODE STYLE* made argument final
        return ((RenderTypeAccessor) type).shouldSortOnUpload();
    }

    @Override
    public int getAllocatedSize() {
        return ((MemoryTrackingBuffer) buffer).getAllocatedSize();
    }

    @Override
    public int getUsedSize() {
        return ((MemoryTrackingBuffer) buffer).getUsedSize();
    }
}

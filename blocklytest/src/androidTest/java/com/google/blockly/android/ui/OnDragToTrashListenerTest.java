package com.google.blockly.android.ui;

import android.content.ClipDescription;
import android.view.DragEvent;

import com.google.blockly.android.MockitoAndroidTestCase;
import com.google.blockly.android.clipboard.BlockClipDataHelper;
import com.google.blockly.android.control.BlocklyController;
import com.google.blockly.model.Block;

import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

/**
 * Unit tests for {@link OnDragToTrashListener}.
 */
public class OnDragToTrashListenerTest extends MockitoAndroidTestCase {
    @Mock
    BlocklyController mMockController;
    @Mock
    BlockClipDataHelper mMockClipDataHelper;

    @Mock
    BlockListView mMockToolbox;
    @Mock
    WorkspaceView mMockWorkspaceView;
    @Mock
    PendingDrag mMockToolboxDrag;
    @Mock
    PendingDrag mMockWorkspaceDrag;

    @Mock
    DragEvent mBlockDragStartFromToolbox;
    @Mock
    DragEvent mBlockDragStartFromWorkspace;
    @Mock
    DragEvent mBlockDragEntered;
    @Mock
    DragEvent mBlockDragLocation;
    @Mock
    DragEvent mBlockDragExited;
    @Mock
    DragEvent mBlockDrop;
    @Mock
    DragEvent mBlockDragEnded;
    @Mock
    DragEvent mRemoteBlockDragEvent;
    @Mock
    DragEvent mOtherDragEvent;

    /** Test instance. */
    OnDragToTrashListener mOnDragToTrashListener;

    ClipDescription mBlockClipDescription = new ClipDescription("block", new String[] {});
    ClipDescription mOtherClipDescription = new ClipDescription("other", new String[] {});

    @Override
    public void setUp() throws Exception {
        super.setUp();

        Mockito.stub(mMockController.getClipDataHelper())
                .toReturn(mMockClipDataHelper);

        Mockito.when(mMockToolboxDrag.getDragInitiator()).thenReturn(mMockToolbox);
        Mockito.when(mMockWorkspaceDrag.getDragInitiator()).thenReturn(mMockWorkspaceView);

        mockDragEvent(mBlockDragStartFromToolbox,
                DragEvent.ACTION_DRAG_STARTED,
                true,
                mMockToolboxDrag);
        mockDragEvent(mBlockDragStartFromWorkspace,
                DragEvent.ACTION_DRAG_STARTED,
                true,
                mMockWorkspaceDrag);
        mockDragEvent(mBlockDragEntered,
                DragEvent.ACTION_DRAG_ENTERED,
                true,
                mMockWorkspaceDrag);
        mockDragEvent(mBlockDragLocation,
                DragEvent.ACTION_DRAG_LOCATION,
                true,
                mMockWorkspaceDrag);
        mockDragEvent(mBlockDragExited,
                DragEvent.ACTION_DRAG_EXITED,
                true,
                mMockWorkspaceDrag);
        mockDragEvent(mBlockDrop,
                DragEvent.ACTION_DROP,
                true,
                mMockWorkspaceDrag);
        mockDragEvent(mBlockDragEnded,
                DragEvent.ACTION_DRAG_ENDED,
                true,
                null);  // End does not reference the local state.

        mockDragEvent(mRemoteBlockDragEvent, DragEvent.ACTION_DRAG_STARTED, true, null);
        mockDragEvent(mOtherDragEvent, DragEvent.ACTION_DRAG_STARTED, false, null);

        mOnDragToTrashListener = new OnDragToTrashListener(mMockController);
    }

    @Test
    public void testIsTrashableBlock() {
        assertFalse("Cannot remove a block that is not yet on the workspace (drag from toolbox)",
                mOnDragToTrashListener.isTrashableBlock(mBlockDragStartFromToolbox));

        assertTrue(mOnDragToTrashListener.isTrashableBlock(mBlockDragStartFromWorkspace));
        assertTrue(mOnDragToTrashListener.isTrashableBlock(mBlockDragEntered));
        assertTrue(mOnDragToTrashListener.isTrashableBlock(mBlockDragLocation));
        assertTrue(mOnDragToTrashListener.isTrashableBlock(mBlockDragExited));
        assertTrue(mOnDragToTrashListener.isTrashableBlock(mBlockDrop));

        assertFalse("DRAG_ENDED does not have local state (reference to the WorkspaceView)",
                mOnDragToTrashListener.isTrashableBlock(mBlockDragEnded));
        assertFalse("Blocks from other activities (no local state) are not trashable.",
                mOnDragToTrashListener.isTrashableBlock(mRemoteBlockDragEvent));
        assertFalse("DragEvents that are not recognized blocks are not trashable.",
                mOnDragToTrashListener.isTrashableBlock(mOtherDragEvent));
    }

    @Test
    public void testOnDrag() {
        Mockito.verify(mMockController).getClipDataHelper();

        assertTrue(mOnDragToTrashListener.onDrag(null, mBlockDragStartFromWorkspace));
        Mockito.verifyNoMoreInteractions(mMockController);

        mOnDragToTrashListener.onDrag(null, mBlockDragEntered);
        Mockito.verifyNoMoreInteractions(mMockController);

        mOnDragToTrashListener.onDrag(null, mBlockDragLocation);
        Mockito.verifyNoMoreInteractions(mMockController);

        mOnDragToTrashListener.onDrag(null, mBlockDragExited);
        Mockito.verifyNoMoreInteractions(mMockController);

        assertTrue(mOnDragToTrashListener.onDrag(null, mBlockDrop));
        Mockito.verify(mMockController)
                .trashRootBlock(Mockito.any(Block.class));

        mOnDragToTrashListener.onDrag(null, mBlockDragEnded);
        Mockito.verifyNoMoreInteractions(mMockController);
    }

    @Test
    public void testOnDrag_invalid() {
        assertFalse("Cannot remove a block that is not yet on the workspace (drag from toolbox)",
                mOnDragToTrashListener.onDrag(null, mBlockDragStartFromToolbox));
        assertFalse("Blocks from other activities (no local state) are not trashable.",
                mOnDragToTrashListener.onDrag(null, mRemoteBlockDragEvent));
        assertFalse("DragEvents that are not recognized blocks are not trashable.",
                mOnDragToTrashListener.onDrag(null, mOtherDragEvent));
        Mockito.verify(mMockController, Mockito.never())
                .trashRootBlock(Mockito.any(Block.class));
    }

    private void mockDragEvent(
            DragEvent event, int action, boolean isBlock, PendingDrag pending) {
        ClipDescription clipDescrip = isBlock ? mBlockClipDescription : mOtherClipDescription;

        Mockito.when(event.getAction())
                .thenReturn(action);
        Mockito.when(event.getClipDescription())
                .thenReturn(clipDescrip);
        Mockito.when(mMockClipDataHelper.isBlockData(clipDescrip))
                .thenReturn(isBlock);
        Mockito.when(mMockClipDataHelper.getPendingDrag(event))
                .thenReturn(pending);

    }
}

/*
 * Copyright 2015 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.blockly.android.ui;

import android.content.Context;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;

import com.google.blockly.android.clipboard.BlockClipDataHelper;
import com.google.blockly.android.control.BlocklyController;
import com.google.blockly.android.control.ConnectionManager;
import com.google.blockly.model.Block;
import com.google.blockly.model.Connection;
import com.google.blockly.model.WorkspacePoint;

import java.util.ArrayList;

/**
 * Handles updating the viewport into the workspace and is the parent view for all blocks. This view
 * is responsible for handling drags. A drag on the workspace will move the viewport and a drag on a
 * block or stack of blocks will drag those within the workspace.
 */
public class WorkspaceView extends NonPropagatingViewGroup {
    private static final String TAG = "WorkspaceView";
    private static final boolean LOG_DRAG_EVENTS = false;

    private final ViewPoint mTemp = new ViewPoint();
    // Viewport bounds. These define the bounding box of all blocks, in view coordinates, and
    // are used to determine ranges and offsets for scrolling.
    private final Rect mBlocksBoundingBox = new Rect();

    private BlocklyController mController = null;
    private WorkspaceHelper mViewHelper = null;
    private BlockClipDataHelper mClipHelper = null;
    private ConnectionManager mConnectionManager = null;

    @VisibleForTesting
    OnDragListener mDragEventListener = new OnDragListener();

    private PendingDrag mPendingDrag = null;
    private BlockView mHighlightedBlockView = null;
    private final ArrayList<Connection> mDraggedConnections = new ArrayList<>();

    private final ViewPoint mTempViewPoint = new ViewPoint();
    private final WorkspacePoint mTempWorkspacePoint = new WorkspacePoint();

    public WorkspaceView(Context context) {
        this(context, null);
    }

    public WorkspaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WorkspaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setOnDragListener(mDragEventListener);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        mBlocksBoundingBox.setEmpty();
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            BlockGroup blockGroup = (BlockGroup) getChildAt(i);
            blockGroup.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);

            // Determine this BlockGroup's bounds in view coordinates and extend boundaries
            // accordingly. Do NOT use mViewHelper.workspaceToVirtualViewCoordinates below, since we want the
            // bounding box independent of scroll offset.
            mViewHelper.workspaceToVirtualViewDelta(blockGroup.getFirstBlockPosition(), mTemp);
            if (mViewHelper.useRtl()) {
                mTemp.x -= blockGroup.getMeasuredWidth();
            }

            mBlocksBoundingBox.union(mTemp.x, mTemp.y,
                    mTemp.x + blockGroup.getMeasuredWidth(),
                    mTemp.y + blockGroup.getMeasuredHeight());
        }

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int childCount = getChildCount();

        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            if (child instanceof BlockGroup) {
                BlockGroup bg = (BlockGroup) child;

                // Get view coordinates of child from its workspace coordinates. Note that unlike
                // onMeasure() above, workspaceToVirtualViewCoordinates() must be used for
                // conversion here, so view scroll offset is properly applied for positioning.
                mViewHelper.workspaceToVirtualViewCoordinates(bg.getFirstBlockPosition(), mTemp);
                if (mViewHelper.useRtl()) {
                    mTemp.x -= bg.getMeasuredWidth();
                }

                child.layout(mTemp.x, mTemp.y,
                        mTemp.x + bg.getMeasuredWidth(), mTemp.y + bg.getMeasuredHeight());
            }
        }
    }

    /**
     * Sets the workspace this view should display.
     *
     * @param controller The controller for this instance.
     */
    public void setController(BlocklyController controller) {
        mController = controller;

        if (mController != null) {
            mViewHelper = controller.getWorkspaceHelper();
            mClipHelper = controller.getClipDataHelper();
            mConnectionManager = controller.getWorkspace().getConnectionManager();
        } else {
            mViewHelper = null;
        }
    }

    public WorkspaceHelper getWorkspaceHelper() {
        return mViewHelper;
    }

    /**
     * @return The bounding box in view coordinates of the workspace region occupied by blocks.
     */
    @NonNull
    public Rect getBlocksBoundingBox(@NonNull Rect outRect) {
        outRect.set(mBlocksBoundingBox);
        return outRect;
    }

    /**
     * Removes all connections of block and its descendants from the {@link }ConnectionManager}, so
     * these connections are not considered as potential connections when looking from connections
     * during dragging.
     *
     * @param block The root block of connections that should be removed.
     */
    private void removeDraggedConnectionsFromConnectionManager(Block block) {
        mDraggedConnections.clear();
        // Don't track any of the connections that we're dragging around.
        block.getAllConnectionsRecursive(mDraggedConnections);
        for (int i = 0; i < mDraggedConnections.size(); i++) {
            Connection conn = mDraggedConnections.get(i);
            mConnectionManager.removeConnection(conn);
            conn.setDragMode(true);
        }
    }

    /**
     * Continue dragging the currently moving block.  Called during ACTION_DRAG_LOCATION.
     *
     * @param event The next drag event to handle, as received by the {@link WorkspaceView}.
     */
    private void continueDragging(DragEvent event) {
        updateBlockPosition(event);

        // highlight as we go
        if (mHighlightedBlockView != null) {
            mHighlightedBlockView.setHighlightedConnection(null);
        }
        Pair<Connection, Connection> connectionCandidate =
                findBestConnection(mPendingDrag.getRootDraggedBlock());
        if (connectionCandidate != null) {
            mHighlightedBlockView = mViewHelper.getView(connectionCandidate.second.getBlock());
            mHighlightedBlockView.setHighlightedConnection(connectionCandidate.second);
        }

        mPendingDrag.getDragGroup().requestLayout();
    }

    /**
     * Move the currently dragged block in response to a new {@link MotionEvent}.
     * <p/>
     * All of the child blocks move with the root block based on its position during layout.
     *
     * @param event The {@link MotionEvent} to react to.
     */
    private void updateBlockPosition(DragEvent event) {
        // The event is relative to the WorkspaceView. Grab the pixel offset within.
        ViewPoint curDragLocationPixels = mTempViewPoint;
        curDragLocationPixels.set((int) event.getX(), (int) event.getY());
        WorkspacePoint curDragPositionWorkspace = mTempWorkspacePoint;
        mViewHelper.virtualViewToWorkspaceCoordinates(curDragLocationPixels, curDragPositionWorkspace);

        WorkspacePoint touchDownWorkspace = mPendingDrag.getTouchDownWorkspaceCoordinates();
        // Subtract original drag location from current location to get delta
        int workspaceDeltaX = curDragPositionWorkspace.x - touchDownWorkspace.x;
        int workspaceDeltaY = curDragPositionWorkspace.y - touchDownWorkspace.y;

        WorkspacePoint blockOrigPosition = mPendingDrag.getOriginalBlockPosition();
        mPendingDrag.getRootDraggedBlock().setPosition(blockOrigPosition.x + workspaceDeltaX,
                blockOrigPosition.y + workspaceDeltaY);
        mPendingDrag.getDragGroup().requestLayout();
    }

    private Pair<Connection, Connection> findBestConnection(Block block) {
        return mConnectionManager.findBestConnection(block, mViewHelper.getMaxSnapDistance());
    }

    /**
     * Attempts to connect a dropped drag group with nearby connections
     */
    private void maybeConnectDragGroup() {
        Block dragRoot = mPendingDrag.getRootDraggedBlock();

        // Maybe snap to connections.
        Pair<Connection, Connection> connectionCandidate = findBestConnection(dragRoot);
        if (connectionCandidate != null) {
            mController.connect(connectionCandidate.first, connectionCandidate.second);
            // .connect(..) includes bumping block within snap distance of the new location.
        } else {
            // Even if no connection is found, still bump any neighbors within snap distance of the
            // new location.
            mController.bumpNeighbors(dragRoot);
        }
    }

    /**
     * Finish a drag gesture and clear pending drag info.  Called by event handlers for ACTION_DROP
     * and ACTION_DRAG_ENDED.
     */
    // TODO(305): Revert actions when behavior if not a drop.
    private void finishDragging() {
        // Update the drag group so that everything that has been changed will be properly
        // invalidated. Also, update the positions of all of the connections that were impacted
        // by the move and add them back to the manager. All of the connection locations will be
        // set relative to their block views immediately after this loop.  For now we just want
        // to unset drag mode and add the connections back to the list; 0, 0 is a cheap place to
        // put them.
        // Dragged connections may be empty, especially if the
        for (int i = 0; i < mDraggedConnections.size(); i++) {
            Connection cur = mDraggedConnections.get(i);
            cur.setPosition(0, 0);
            cur.setDragMode(false);
            mConnectionManager.addConnection(cur);
        }
        mDraggedConnections.clear();

        if (mHighlightedBlockView != null) {
            mHighlightedBlockView.setHighlightedConnection(null);
            mHighlightedBlockView = null;
        }

        if (mPendingDrag != null) {
            BlockView blockView = mPendingDrag.getRootDraggedBlockView();
            if (blockView != null) {
                ((View) blockView).setPressed(false);
            } // else, trashing or similar manipulation made the view disappear.
            mPendingDrag = null;
        }
    }

    @VisibleForTesting
    class OnDragListener implements View.OnDragListener {
        @Override
        public boolean onDrag(View workspaceView, DragEvent event) {
            final int action = event.getAction();

            if (LOG_DRAG_EVENTS) {
                Log.d(TAG, "onDrag: " + BlockViewDragUtils.getActionName(event) + ", " + event);
            }

            if (!mClipHelper.isBlockData(event.getClipDescription())) {
                if (LOG_DRAG_EVENTS) {
                    Log.d(TAG, "onDrag: Not a block.");
                }
                return false;
            }

            if (action == DragEvent.ACTION_DRAG_STARTED) {
                mPendingDrag = mClipHelper.getPendingDrag(event);

                // Triggered in maybeStartDrag(..).
                // The rest of the drag data is already captured in mPendingDrag.
                // NOTE: This event position does not respect view scale.

                BlockView rootDraggedBlockView = mPendingDrag.getRootDraggedBlockView();
                Block rootBlock = rootDraggedBlockView.getBlock();
                if(rootBlock.isMovable()) {
                    removeDraggedConnectionsFromConnectionManager(rootBlock);
                    // TODO(#35): This might be better described as "selected".
                    ((View) rootDraggedBlockView).setPressed(true);
                    return true;    // We want to keep listening for drag events
                } else {
                    Log.w(TAG, "Unexpected drag of unmoveable block");
                    return false;   // We don't want to keep listening for drag events
                }
            }

            if (mPendingDrag != null) {
                switch (action) {
                    case DragEvent.ACTION_DRAG_LOCATION:
                        // If we're still finishing up a previous drag we may have missed the
                        // start of the drag, in which case we shouldn't do anything.
                        continueDragging(event);
                        break;
                    case DragEvent.ACTION_DRAG_ENDED:
                        // TODO(#202): Cancel pending drag?
                        if (event.getResult()) {
                            break;
                        }
                        // Otherwise fall through
                    case DragEvent.ACTION_DROP:
                        // Finalize dragging and reset dragging state flags.
                        // These state flags are still used in the initial phase of figuring out if a
                        // drag has started.
                        maybeConnectDragGroup();
                        finishDragging();  // TODO(#) remove when pre-drag side-effects of old code are removed.
                        return true;    // The drop succeeded.
                    default:
                        break;
                }
            }
            return false;   // In every case that gets here, the return value won't be checked.
        }
    };
}

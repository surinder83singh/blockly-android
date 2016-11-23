/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.blockly.android.ui;

import android.content.ClipData;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.support.annotation.IntDef;
import android.support.annotation.Size;
import android.support.annotation.VisibleForTesting;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import com.google.blockly.android.clipboard.BlockClipDataHelper;
import com.google.blockly.android.control.BlocklyController;
import com.google.blockly.android.control.ConnectionManager;
import com.google.blockly.model.Block;
import com.google.blockly.model.Connection;
import com.google.blockly.model.Workspace;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

/**
 * Controller for dragging blocks and groups of blocks within a workspace.
 */
public class BlockViewDragUtils {
    private static final String TAG = "BlockViewDragUtils";
    private static final boolean LOG_TOUCH_EVENTS = false;
    private static final boolean LOG_DRAG_EVENTS = false;

    private static final int TAP_TIMEOUT = ViewConfiguration.getTapTimeout();

    public static String getActionName(DragEvent event) {
        int action = event.getAction();
        return (action == DragEvent.ACTION_DRAG_STARTED) ? "DRAG_STARTED" :
                (action == DragEvent.ACTION_DRAG_LOCATION) ? "DRAG_LOCATION" :
                (action == DragEvent.ACTION_DRAG_ENDED) ? "DRAG_ENDED" :
                (action == DragEvent.ACTION_DROP) ? "DROP" :
                "UNKNOWN ACTION #" + action;
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({DRAG_MODE_IMMEDIATE, DRAG_MODE_SLOPPY})
    public @interface DragMode {}
    @VisibleForTesting static final int DRAG_MODE_IMMEDIATE = 0;
    @VisibleForTesting static final int DRAG_MODE_SLOPPY = 1;

    private final ArrayList<Connection> mDraggedConnections = new ArrayList<>();
    // For use in bumping neighbours; instance variable only to avoid repeated allocation.
    private final ArrayList<Connection> mTempConnections = new ArrayList<>();
    // Rect for finding the bounding box of the trash can view.
    private final Rect mTrashRect = new Rect();
    // For use in getting location on screen.
    private final int[] mTempScreenCoord1 = new int[2];
    private final int[] mTempScreenCoord2 = new int[2];
    private final ViewPoint mTempViewPoint = new ViewPoint();

    private Handler mMainHandler;
    private final BlocklyController mController;
    private final WorkspaceHelper mViewHelper;
    private final BlockClipDataHelper mClipHelper;
    private final Workspace mWorkspace;
    private final ConnectionManager mConnectionManager;

    /**
     * This flags helps check {@link #onTouchBlockImpl} is not called recursively, which can occur
     * when the view hierarchy is manipulated during event handling.
     */
    private boolean mWithinOnTouchBlockImpl = false;

    private PendingDrag mPendingDrag;
    private Runnable mLogPending = (LOG_TOUCH_EVENTS || LOG_DRAG_EVENTS) ? new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, (mPendingDrag == null ? "\tnot pending" :
                    (mPendingDrag.getDragGroup()==null ? "\tpending: touched = " + mPendingDrag.getTouchedBlockView()
                    : "\tdragging: " + mPendingDrag.getDragGroup())));
        }
    } : null;

    // Which {@link BlockView} was touched, and possibly may be being dragged.
    private WorkspaceView mWorkspaceView;
    // The view for the trash can.
    private View mTrashView;
    //The square of the required touch slop before starting a drag, precomputed to avoid
    // square root operations at runtime.
    private float mTouchSlopSquared = 0.0f;

    /**
     * @param blocklyController The {@link BlocklyController} managing Blocks in this activity.
     */
    public BlockViewDragUtils(BlocklyController blocklyController) {
        mController = blocklyController;
        mWorkspace = blocklyController.getWorkspace();
        mViewHelper = blocklyController.getWorkspaceHelper();
        mClipHelper = blocklyController.getClipDataHelper();
        mConnectionManager = mWorkspace.getConnectionManager();

        mMainHandler = new Handler();

        float touchSlop = ViewConfiguration.get(mController.getContext()).getScaledTouchSlop();
        mTouchSlopSquared = touchSlop * touchSlop;
    }

    /**
     * Remove all the connections in a blocks tree from the list of connections being dragged. This
     * is used when removing shadow blocks from a block tree during a drag. If there's no drag
     * in progress this has no effects.
     *
     * @param rootBlock The start of the block tree to remove connections for.
     */
    public void removeFromDraggingConnections(Block rootBlock) {
        if (mPendingDrag == null) {
            return;
        }
        mTempConnections.clear();
        rootBlock.getAllConnectionsRecursive(mTempConnections);
        for (int i = 0; i < mTempConnections.size(); i++) {
            Connection conn = mTempConnections.get(i);
            mDraggedConnections.remove(conn);
            conn.setDragMode(false);
        }
    }

    /**
     * Creates a BlockTouchHandler that will initiate a drag only after the user has dragged
     * beyond some touch threshold.
     *
     * @param blockGestureHandler The {@link BlockView.GestureHandler} to handle gestures for the
     *                          constructed {@link BlockTouchHandler}.
     * @return A newly constructed {@link BlockTouchHandler}.
     */
    public BlockTouchHandler buildSloppyBlockTouchHandler(
            final BlockView.GestureHandler blockGestureHandler) {

        return new BlockTouchHandler() {
            @Override
            public boolean onTouchBlock(BlockView blockView, MotionEvent motionEvent) {
                return onTouchBlockImpl(DRAG_MODE_SLOPPY, blockGestureHandler, blockView, motionEvent,
                        /* interceptMode */ false);
            }

            @Override
            public boolean onInterceptTouchEvent(BlockView blockView, MotionEvent motionEvent) {
                // Intercepted move events might still be handled but the child view, such as
                // a drop down field.
                return onTouchBlockImpl(DRAG_MODE_SLOPPY, blockGestureHandler, blockView, motionEvent,
                        /* interceptMode */ true);
            }
        };
    }

    /**
     * Creates a BlockTouchHandler that will initiate a drag as soon as the BlockView receives a
     * {@link MotionEvent} directly (not via interception).
     *
     * @param blockGestureHandler The {@link BlockView.GestureHandler} to handle gestures for the
     *                          constructed {@link BlockTouchHandler}.
     * @return A newly constructed {@link BlockTouchHandler}.
     */
    public BlockTouchHandler buildImmediateDragBlockTouchHandler(
            final BlockView.GestureHandler blockGestureHandler) {

        return new BlockTouchHandler() {
            @Override
            public boolean onTouchBlock(BlockView blockView, MotionEvent motionEvent) {
                return onTouchBlockImpl(
                        DRAG_MODE_IMMEDIATE, blockGestureHandler, blockView, motionEvent,
                        /* interceptMode */ false);
            }

            @Override
            public boolean onInterceptTouchEvent(BlockView blockView, MotionEvent motionEvent) {
                return onTouchBlockImpl(
                        DRAG_MODE_IMMEDIATE, blockGestureHandler, blockView, motionEvent,
                        /* interceptMode */ true);
            }
        };
    }

    public void setWorkspaceView(WorkspaceView view) {
        mWorkspaceView = view;
    }

    // TODO(#210): Generalize this to other possible block drop targets.
    public void setTrashView(View trashView) {
        mTrashView = trashView;
    }

    /**
     * Let the BlockViewDragUtils know that a block was touched. This will be called when the block in the
     * workspace has been touched, but a drag has not yet been started.
     *
     * This method handles both regular touch events and intercepted touch events, with the latter
     * identified with the {@code interceptMode} parameter.  The only difference is that intercepted
     * events only return true (indicating they are handled) when a drag has been initiated. This
     * allows any underlying View, such as a field to handle the MotionEvent normally.
     *
     * @param dragMode The mode (immediate or sloppy) for handling this touch event.
     * @param blockGestureHandler The {@link BlockView.GestureHandler} attached to this view.
     * @param touchedView The {@link BlockView} that detected a touch event.
     * @param event The touch event.
     * @param interceptMode When true forces all {@link MotionEvent#ACTION_MOVE} events
     *                                   that match {@link #mPendingDrag} to return true / handled.
     *                                   Otherwise, it only returns true if a drag is started.
     *
     * @return True if the event was handled by this touch implementation.
     */
    @VisibleForTesting
    boolean onTouchBlockImpl(@DragMode int dragMode, BlockView.GestureHandler blockGestureHandler,
                             BlockView touchedView, MotionEvent event, boolean interceptMode) {
        if (mWithinOnTouchBlockImpl) {
            throw new IllegalStateException(
                    "onTouchBlockImpl() called recursively. Make sure OnDragHandler." +
                    "maybeGetDragGroupCreator() is not manipulating the View hierarchy.");
        }
        mWithinOnTouchBlockImpl = true;

        final int action = MotionEventCompat.getActionMasked(event);

        boolean matchesPending = false;
        if (mPendingDrag != null) {
            matchesPending = mPendingDrag.isMatchAndProcessed(event, touchedView);
            if (!matchesPending && !mPendingDrag.isAlive()) {
                mPendingDrag = null;  // Was a part of previous gesture. Delete.
            }
        }

        if (LOG_TOUCH_EVENTS) {
            Log.d(TAG, "onTouchBlockImpl: "
                    + (dragMode == DRAG_MODE_IMMEDIATE ? "IMMEDIATE" : "SLOPPY")
                    + (interceptMode ? " intercept" : " direct")
                    + "\n\t" + event
                    + "\n\tMatches pending? " + matchesPending);

            mMainHandler.removeCallbacks(mLogPending);  // Only call once per event 'tick'
            mMainHandler.post(mLogPending);
        }

        final boolean result;
        if (action == MotionEvent.ACTION_DOWN ) {
            if (mPendingDrag == null) {
                mPendingDrag = new PendingDrag(mController, touchedView, event);
                if (interceptMode) {
                    // Do not handle intercepted down events. Allow child views (particularly
                    // fields) to handle the touch normally.
                    result = false;
                } else {
                    // The user touched the block directly.
                    if (dragMode == DRAG_MODE_IMMEDIATE) {
                        result = maybeStartDrag(blockGestureHandler);
                    } else {
                        result = true;
                    }
                }
            } else if (matchesPending && !interceptMode) {
                // The Pending Drag was created during intercept, but the child did not handle it
                // and the event has bubbled down to here.
                if (dragMode == DRAG_MODE_IMMEDIATE) {
                    result = maybeStartDrag(blockGestureHandler);
                } else {
                    result = true;
                }
            } else {
                result = false; // Pending drag already started with a different view / pointer id.
            }
        } else if (matchesPending) {
            // This touch is part of the current PendingDrag.
            if (action == MotionEvent.ACTION_MOVE) {
                if (mPendingDrag.isDragging()) {
                    result = false;  // We've already cancelled or started dragging.
                } else {
                    // Mark all direct move events as handled, but only intercepted events if they
                    // initiate a new drag.
                    boolean isDragGesture =
                            (!interceptMode && dragMode == DRAG_MODE_IMMEDIATE
                                    && event.getDownTime() > TAP_TIMEOUT)
                            || isBeyondSlopThreshold(event);
                    boolean isNewDrag = isDragGesture && maybeStartDrag(blockGestureHandler);
                    result = isNewDrag || !interceptMode;
                }
            }
            // Handle the case when the user releases before moving far enough to start a drag.
            else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (!mPendingDrag.isDragging()) {
                    if (!interceptMode && mPendingDrag.isClick()) {
                        blockGestureHandler.onBlockClicked(mPendingDrag);
                    }
                    BlockView blockView = mPendingDrag.getRootDraggedBlockView();
                    if (blockView != null) {
                        ((View) blockView).setPressed(false);
                    } // else, trashing or similar manipulation made the view disappear.
                    mPendingDrag = null;
                }
                result = !interceptMode;
            } else {
                result = false; // Unrecognized event action
            }
        } else {
            result = false; // Doesn't match existing drag.
        }

        if (LOG_TOUCH_EVENTS) Log.d(TAG, "\treturn " + result);
        mWithinOnTouchBlockImpl = false;
        return result;
    }

    /**
     * Checks whether {@code actionMove} is beyond the allowed slop (i.e., unintended) drag motion
     * distance.
     *
     * @param actionMove The {@link MotionEvent#ACTION_MOVE} event.
     * @return True if the motion is beyond the allowed slop threshold
     */
    // TODO(#): Replace with GestureDetector onScroll.
    private boolean isBeyondSlopThreshold(MotionEvent actionMove) {
        BlockView touchedView = mPendingDrag.getTouchedBlockView();

        // Not dragging yet - compute distance from Down event and start dragging if far enough.
        @Size(2) int[] touchDownLocation = mTempScreenCoord1;
        mPendingDrag.getTouchDownScreen(touchDownLocation);

        @Size(2) int[] curScreenLocation = mTempScreenCoord2;
        touchedView.getTouchLocationOnScreen(actionMove, curScreenLocation);

        final int deltaX = touchDownLocation[0] - curScreenLocation[0];
        final int deltaY = touchDownLocation[1] - curScreenLocation[1];

        // Dragged far enough to start a drag?
        return (deltaX * deltaX + deltaY * deltaY > mTouchSlopSquared);
    }

    /**
     * Handle motion events while starting to drag a block.  This keeps track of whether the block
     * has been dragged more than {@code mTouchSlop} and starts a drag if necessary. Once the drag
     * has been started, all following events will be handled through view
     * {@link View.OnDragListener}s.
     *
     * @param blockGestureHandler The {@link BlockView.GestureHandler} managing this drag.
     */
    private boolean maybeStartDrag(BlockView.GestureHandler blockGestureHandler) {
        // Check with the pending drag handler to select or create the dragged group.
        final PendingDrag pendingDrag = mPendingDrag;  // Stash for async callback
        final Runnable dragGroupCreator = blockGestureHandler.maybeGetDragGroupCreator(pendingDrag);
        final boolean foundDragGroup = (dragGroupCreator != null);
        if (foundDragGroup) {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mPendingDrag != null && mPendingDrag.isDragging()) {
                        return; // Ignore.  Probably being handled by a child view.
                    }

                    dragGroupCreator.run();
                    boolean dragStarted = pendingDrag.isDragging();
                    if (dragStarted) {
                        mPendingDrag = pendingDrag;
                        final BlockGroup dragGroup = mPendingDrag.getDragGroup();
                        ViewParent parent = dragGroup.getParent();
                        // TODO(#): Allow unparented BlockGroups (new blocks from Toolbox, etc.)
                        if (parent != mWorkspaceView) {
                            throw new IllegalStateException("dragGroup is root in WorkspaceView");
                        }

                        try {
                            ClipData clipData = mClipHelper.buildDragClipData(pendingDrag);

                            int dragFlags = 0;
                            if (android.os.Build.VERSION.SDK_INT >= 24) {
                                // TODO(#): Allow app to configure DRAG_FLAG_GLOBAL or _OPAQUE?
                                dragFlags |= 0x00000100;  // View.DRAG_FLAG_GLOBAL
                                dragFlags |= 0x00000200;  // View.DRAG_FLAG_OPAQUE
                            }
                            mWorkspaceView.startDrag(
                                    clipData,
                                    new DragShadowBuilder(pendingDrag),
                                    pendingDrag,
                                    dragFlags);
                        } catch (IOException e) {
                            // Serialization failed in ClipDataHelper.
                            mPendingDrag = null;
                        }
                    } else {
                        mPendingDrag = null;
                    }
                }
            });
        }

        return foundDragGroup;
    }

    /**
     * Check whether the given event occurred on top of the trash can button.  Should be called from
     * {@link WorkspaceView}.
     *
     * @param event The event whose location should be checked, with position in WorkspaceView
     * coordinates.
     * @return Whether the event was on top of the trash can button.
     */
    // TODO(#): Use DragEvent DRAG_ENTERED / DRAG_EXITED in TrashIconView
    private boolean touchingTrashView(DragEvent event) {
        if (mTrashView == null) {
            return false;
        }

        mTrashView.getLocationOnScreen(mTempScreenCoord1);
        mTrashView.getHitRect(mTrashRect);

        mTrashRect.offset(
                (mTempScreenCoord1[0] - mTrashRect.left),
                (mTempScreenCoord1[1] - mTrashRect.top));
        // Get the touch location on the screen
        mTempViewPoint.set((int) event.getX(), (int) event.getY());
        mViewHelper.virtualViewToScreenCoordinates(mTempViewPoint, mTempViewPoint);

        // Check if the touch location was on the trash
        return mTrashRect.contains(mTempViewPoint.x, mTempViewPoint.y);
    }

    /**
     * Ends a drag in the trash can, clearing state and deleting blocks as needed.
     */
    // TODO(#): Move to OnDragListener in TrashIconView.
    private boolean dropInTrash() {
        mDraggedConnections.clear();
        return mController.trashRootBlock(mPendingDrag.getRootDraggedBlock());
    }

    private static class DragShadowBuilder extends View.DragShadowBuilder {
        private PendingDrag mPendingDrag;
        private int mSizeX, mSizeY;

        DragShadowBuilder(PendingDrag pendingDrag) {
            super(pendingDrag.getDragGroup());
            mPendingDrag = pendingDrag;

            BlockGroup dragGroup = pendingDrag.getDragGroup();
            mSizeX = dragGroup.getWidth();
            if (mSizeX == 0) {
                dragGroup.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                dragGroup.layout(0, 0, dragGroup.getMeasuredWidth(), dragGroup.getMeasuredHeight());
                mSizeX = dragGroup.getWidth();
            }
            mSizeY = dragGroup.getHeight();
        }

        public void onProvideShadowMetrics(Point shadowSize, Point shadowTouchPoint) {
            shadowSize.set(mSizeX, mSizeY);
            ViewPoint dragTouchOffset = mPendingDrag.getDragTouchOffset();
            shadowTouchPoint.set(dragTouchOffset.x, dragTouchOffset.y);
        }
    }
}

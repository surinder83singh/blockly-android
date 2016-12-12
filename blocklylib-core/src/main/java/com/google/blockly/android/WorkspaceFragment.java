/*
 *  Copyright 2015 Google Inc. All Rights Reserved.
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

package com.google.blockly.android;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.blockly.android.control.BlocklyController;
import com.google.blockly.android.ui.BlockView;
import com.google.blockly.android.ui.VirtualWorkspaceView;
import com.google.blockly.android.ui.WorkspaceView;
import com.google.blockly.model.Workspace;

/**
 * The {@code WorkspaceFragement} holds the active {@link WorkspaceView} and workspace
 * {@link BlockView}s.
 * <p/>
 * The workspace can be configured as scrollable or fixed via the <code>scrollable</code> attribute.
 * For example:
 * <blockquote><pre>
 * &lt;fragment
 *     xmlns:android="http://schemas.android.com/apk/res/android"
 *     xmlns:blockly="http://schemas.android.com/apk/res-auto"
 *     android:name="com.google.blockly.WorkspaceFragment"
 *     android:id="@+id/blockly_workspace"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent"
 *     /&gt;
 * </pre></blockquote>
 */
public class WorkspaceFragment extends Fragment {
    private static final String TAG = "WorkspaceFragment";

    private BlocklyController mController;
    private Workspace mWorkspace;
    private VirtualWorkspaceView mVirtualWorkspaceView;
    private WorkspaceView mWorkspaceView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final ViewGroup rootView =
                (ViewGroup) inflater.inflate(R.layout.fragment_workspace, container, false);

        mVirtualWorkspaceView =
                (VirtualWorkspaceView) rootView.findViewById(R.id.virtual_workspace);
        mWorkspaceView = (WorkspaceView) rootView.findViewById(R.id.workspace);
        return rootView;
    }

    /**
     * Sets the controller to use in this fragment for instantiating views. This should be the same
     * controller used for an associated {@link ToolboxFragment} or {@link TrashFragment}.
     *
     * @param controller The controller backing this fragment.
     */
    public void setController(BlocklyController controller) {
        if (controller == mController) {
            return; // no-op
        }

        mController = controller;
        mWorkspace = (controller == null) ? null : mController.getWorkspace();
        mController.initWorkspaceView(mWorkspaceView);
    }

    public void setZoomBehavior(ZoomBehavior zoomBehavior){
        if (mVirtualWorkspaceView != null) {
            mVirtualWorkspaceView.setZoomBehavior(zoomBehavior);
        }
    }

    /**
     * @return The workspace being used by this fragment.
     */
    public Workspace getWorkspace() {
        return mWorkspace;
    }

    /**
     * @return The {@link WorkspaceView} inside this fragment.
     */
    public WorkspaceView getWorkspaceView() {
        return mWorkspaceView;
    }
}

/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.blockly.android.clipboard;

import android.content.ClipData;

import com.google.blockly.android.control.BlocklyController;
import com.google.blockly.android.ui.BlockViewFactory;
import com.google.blockly.android.ui.WorkspaceHelper;
import com.google.blockly.model.Block;
import com.google.blockly.model.BlockFactory;


/**
 * {@code ClipDataTransformer} is an interface to help transform {@link Block} data and view objects
 * into ClipData, and back. This is used for drag-and-drop operations and copy/paste actions.
 * <p/>
 * Every application needs one implementation. Most applications will be content with
 * {@link SingleMimeTypeClipDataHelper}.
 */
public interface BlockClipDataHelper {
    /**
     * Sets the {@link BlocklyController} for this instance. Called during the controller's
     * initialization.  The controller provides access to critical classes, such as the
     * {@link WorkspaceHelper}, {@link BlockFactory}, and {@link BlockViewFactory}
     */
    void setController(BlocklyController controller);

    /**
     * This determines whether an incoming {@link ClipData} is a representation of Blockly
     * {@link Block}s that can be handled by this {@link BlockClipDataHelper}.
     *
     * @param clipData The incoming clipboard data.
     */
    boolean isBlockData(ClipData clipData);
}

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
import android.content.Context;

import com.google.blockly.android.control.BlocklyController;

/**
 * Implements ClipDataTransformer with a single support MIME type.  Uses intent extras as the
 * in-transit storage format.
 */
public class SingleMimeTypeClipDataHelper implements BlockClipDataHelper {
    public static final String EXTRA_BLOCKLY_XML = "BLOCKLY_XML";
    public static final String EXTRA_DRAG_SHADOW_WIDTH = "DRAG_SHADOW_WIDTH";
    public static final String EXTRA_DRAG_SHADOW_HEIGHT = "DRAG_SHADOW_HEIGHT";
    public static final String EXTRA_DRAG_OFFSET_X = "DRAG_OFFSET_X";
    public static final String EXTRA_DRAG_OFFSET_Y = "DRAG_OFFSET_Y";

    protected final String mMimeType;
    protected final int mClipLabelRes;  // TODO(#): Singular vs plural ("block" vs "blocks")

    protected BlocklyController mController;
    protected Context mContext;
    protected String mClipLabel;

    /**
     * Constructs a new {@link SingleMimeTypeClipDataHelper} with the provided MIME string and
     * user visible (accessibility, etc.) clip label string.
     *
     * @param mimeType The MIME type the new instance use for encoding and decoding.
     * @param clipLabelRes The resource id of the label to apply to {@link ClipData}s.
     */
    public SingleMimeTypeClipDataHelper(String mimeType, int clipLabelRes) {
        mMimeType = mimeType;
        mClipLabelRes = clipLabelRes;
    }

    @Override
    public void setController(BlocklyController controller) {
        mController = controller;
        mContext = mController.getContext();
        mClipLabel = mContext.getResources().getString(mClipLabelRes);
    }

    @Override
    public boolean isBlockData(ClipData clipData) {
        return false;
    }
}

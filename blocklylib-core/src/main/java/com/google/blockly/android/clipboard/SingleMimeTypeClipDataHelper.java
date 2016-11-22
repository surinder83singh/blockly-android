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
import android.content.Intent;

import com.google.blockly.android.control.BlocklyController;
import com.google.blockly.android.ui.PendingDrag;
import com.google.blockly.android.ui.WorkspaceHelper;
import com.google.blockly.model.Block;
import com.google.blockly.utils.BlocklyXmlHelper;

import java.io.IOException;

/**
 * Implements ClipDataTransformer with a single support MIME type.  Uses intent extras as the
 * in-transit storage format.
 */
public class SingleMimeTypeClipDataHelper implements BlockClipDataHelper {
    public static final String EXTRA_BLOCKLY_XML = "BLOCKLY_XML";

    protected final String mMimeType;
    protected final int mClipLabelRes;  // TODO(#): Singular vs plural ("block" vs "blocks")

    protected BlocklyController mController;
    protected WorkspaceHelper mViewHelper;
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
        mViewHelper = controller.getWorkspaceHelper();

        mContext = mController.getContext();
        mClipLabel = mContext.getResources().getString(mClipLabelRes);
    }

    @Override
    public boolean isBlockData(ClipData clipData) {
        return false;
    }

    @Override
    public ClipData buildDragClipData(PendingDrag drag) throws IOException {
        Block root = drag.getRootDraggedBlock();
        String xml = BlocklyXmlHelper.writeBlockToXml(root);
        
        Intent intent = new Intent();
        intent.putExtra(EXTRA_BLOCKLY_XML, xml);
        ClipData.Item item = new ClipData.Item(intent);

        return new ClipData(mClipLabel, new String[] {mMimeType}, item);
    }
}

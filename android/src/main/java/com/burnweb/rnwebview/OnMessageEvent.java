package com.burnweb.rnwebview;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.events.Event;
import com.facebook.react.uimanager.events.RCTEventEmitter;

/**
 * Created by Wanchope Blanco on 2017/11/02.
 */

public class OnMessageEvent extends Event<com.burnweb.rnwebview.OnMessageEvent> {

    public static final String JS_EVENT_NAME = "topMessage";
    private final String mData;

    public OnMessageEvent(int viewId, String data) {
        super(viewId);

        mData = data;
    }

    @Override
    public String getEventName() {
        return JS_EVENT_NAME;
    }

    @Override
    public void dispatch(RCTEventEmitter rctEventEmitter) {
        WritableMap data = Arguments.createMap();
        data.putString("data", mData.toString());
        rctEventEmitter.receiveEvent(getViewTag(), getEventName(), data);
    }
}
